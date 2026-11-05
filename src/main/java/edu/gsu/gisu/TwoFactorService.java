package edu.gsu.gisu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * RFC 6238-style 6-digit TOTP, encrypted secrets, one-use recovery codes.
 * Local profile generates a stable, gitignored encryption key; production
 * requires GISU_TOTP_ENCRYPTION_KEY supplied securely by the server operator.
 */
@Service
public class TwoFactorService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String KEY_FILE = ".gisu-totp-key";
    private static final int PERIOD_SECONDS = 30;
    private static final int RECOVERY_COUNT = 6;

    private final JdbcTemplate db;
    private final Environment env;
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKeySpec encryptionKey;

    public TwoFactorService(JdbcTemplate db, Environment env,
                            @Value("${GISU_TOTP_ENCRYPTION_KEY:}") String configuredKey) {
        this.db = db;
        this.env = env;
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    public boolean enabled(int customerId) {
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM account_totp_settings " +
                "WHERE customer_id = ? AND enabled = TRUE", Integer.class, customerId);
        return count != null && count > 0;
    }

    public boolean hasPendingSetup(int customerId) {
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM account_totp_settings " +
                "WHERE customer_id = ? AND enabled = FALSE", Integer.class, customerId);
        return count != null && count > 0;
    }

    @Transactional
    public boolean beginSetup(int customerId) {
        if (enabled(customerId)) return false;
        // A new attempt replaces any unconfirmed secret from the same user.
        db.update("DELETE FROM account_totp_settings " +
                "WHERE customer_id = ? AND enabled = FALSE", customerId);
        byte[] seed = new byte[20];
        random.nextBytes(seed);
        String encrypted = encrypt(encodeBase32(seed));
        db.update("INSERT INTO account_totp_settings " +
                "(customer_id, secret_ciphertext, enabled, last_used_step) " +
                "VALUES (?, ?, FALSE, -1)", customerId, encrypted);
        return true;
    }

    public String pendingSecret(int customerId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT secret_ciphertext FROM account_totp_settings " +
                "WHERE customer_id = ? AND enabled = FALSE", customerId);
        if (rows.isEmpty()) return null;
        return decrypt(String.valueOf(rows.get(0).get("secret_ciphertext")));
    }

    public String provisioningUri(int customerId, String email) {
        String secret = pendingSecret(customerId);
        if (secret == null) return null;
        String label = URLEncoder.encode("GISU:" + email, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret +
                "&issuer=GISU&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Set up the user's authenticator and issue recovery codes only after
     * a valid one-time app code proves possession of the shared secret.
     */
    @Transactional
    public List<String> confirmSetup(int customerId, String code) {
        if (enabled(customerId) || !consumeTotp(customerId, code, false))
            return List.of();
        int updated = db.update(
                "UPDATE account_totp_settings SET enabled = TRUE, " +
                "enabled_at = CURRENT_TIMESTAMP WHERE customer_id = ? AND enabled = FALSE",
                customerId);
        if (updated != 1) return List.of();
        db.update("DELETE FROM account_totp_recovery_codes WHERE customer_id = ?", customerId);
        List<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_COUNT; i++) {
            byte[] bytes = new byte[6];
            random.nextBytes(bytes);
            String hex = HexFormat.of().withUpperCase().formatHex(bytes);
            String readable = hex.substring(0, 4) + "-" + hex.substring(4, 8) +
                    "-" + hex.substring(8);
            db.update("INSERT INTO account_totp_recovery_codes " +
                    "(customer_id, recovery_hash) VALUES (?, ?)",
                    customerId, BCrypt.hashpw(hex, BCrypt.gensalt(10)));
            plainCodes.add(readable);
        }
        return plainCodes;
    }

    @Transactional
    public boolean verifyLogin(int customerId, String code) {
        if (!enabled(customerId)) return false;
        return consumeTotp(customerId, code, true) || consumeRecovery(customerId, code);
    }

    @Transactional
    public boolean disable(int customerId, String code) {
        if (!enabled(customerId) ||
                !(consumeTotp(customerId, code, true) || consumeRecovery(customerId, code)))
            return false;
        db.update("DELETE FROM account_totp_recovery_codes WHERE customer_id = ?", customerId);
        return db.update(
                "DELETE FROM account_totp_settings WHERE customer_id = ? AND enabled = TRUE",
                customerId) == 1;
    }

    private boolean consumeTotp(int customerId, String code, boolean requireEnabled) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT secret_ciphertext, enabled, last_used_step " +
                "FROM account_totp_settings WHERE customer_id = ?", customerId);
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.get(0);
        Object enabledValue = row.get("enabled");
        boolean isEnabled = Boolean.TRUE.equals(enabledValue) ||
                (enabledValue instanceof Number n && n.intValue() != 0);
        if (isEnabled != requireEnabled) return false;
        long lastUsed = ((Number) row.get("last_used_step")).longValue();
        String secret = decrypt(String.valueOf(row.get("secret_ciphertext")));
        long now = Instant.now().getEpochSecond() / PERIOD_SECONDS;
        // A one-step clock skew in either direction is allowed.
        for (long step = now - 1; step <= now + 1; step++) {
            if (step <= lastUsed) continue; // reject repeated codes
            if (constantTimeEquals(totpAt(secret, step), code)) {
                return db.update(
                        "UPDATE account_totp_settings SET last_used_step = ? " +
                        "WHERE customer_id = ? AND enabled = ? AND last_used_step < ?",
                        step, customerId, requireEnabled, step) == 1;
            }
        }
        return false;
    }

    private boolean consumeRecovery(int customerId, String code) {
        if (code == null) return false;
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase();
        if (!normalized.matches("[A-F0-9]{12}")) return false;
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT recovery_id, recovery_hash FROM account_totp_recovery_codes " +
                "WHERE customer_id = ? ORDER BY recovery_id", customerId);
        for (Map<String, Object> row : rows) {
            if (BCrypt.checkpw(normalized, String.valueOf(row.get("recovery_hash")))) {
                return db.update(
                        "DELETE FROM account_totp_recovery_codes " +
                        "WHERE recovery_id = ? AND customer_id = ?",
                        row.get("recovery_id"), customerId) == 1;
            }
        }
        return false;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    // Package-private for test verification using the exact same RFC6238 algorithm.
    static String totpAt(String base32, long step) {
        try {
            byte[] key = decodeBase32(base32);
            byte[] counter = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counter[i] = (byte) step;
                step >>>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 15;
            int value = ((hash[offset] & 0x7f) << 24) |
                    ((hash[offset + 1] & 0xff) << 16) |
                    ((hash[offset + 2] & 0xff) << 8) |
                    (hash[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Authenticator verification unavailable", ex);
        }
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(ALPHABET.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) result.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    private static byte[] decodeBase32(String encoded) {
        int buffer = 0, bits = 0;
        byte[] result = new byte[encoded.length() * 5 / 8 + 2];
        int pos = 0;
        for (char c : encoded.toUpperCase().toCharArray()) {
            int idx = ALPHABET.indexOf(c);
            if (idx < 0) throw new IllegalArgumentException("Invalid authenticator key");
            buffer = (buffer << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                result[pos++] = (byte) (buffer >>> (bits - 8));
                bits -= 8;
            }
        }
        return Arrays.copyOf(result, pos);
    }

    private String encrypt(String secret) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot protect authenticator secret", ex);
        }
    }

    private String decrypt(String ciphertext) {
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext);
            if (packed.length < 29) throw new IllegalArgumentException("Corrupt authenticator data");
            byte[] iv = Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Authenticator key missing or changed; " +
                    "account recovery requires administrator intervention", ex);
        }
    }

    private SecretKeySpec encryptionKey() {
        if (encryptionKey != null) return encryptionKey;
        synchronized (this) {
            if (encryptionKey == null) {
                String value = configuredKey;
                if (value.isBlank() && env.acceptsProfiles(Profiles.of("local"))) {
                    value = localKey();
                }
                if (value.isBlank()) throw new IllegalStateException(
                        "Set GISU_TOTP_ENCRYPTION_KEY before enabling authenticator 2FA.");
                byte[] key = Base64.getDecoder().decode(value);
                if (key.length != 32) throw new IllegalStateException(
                        "GISU_TOTP_ENCRYPTION_KEY must be 32 bytes encoded as base64.");
                encryptionKey = new SecretKeySpec(key, "AES");
            }
            return encryptionKey;
        }
    }

    private String localKey() {
        Path file = Path.of(KEY_FILE);
        if (Files.notExists(file)) {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
                byte[] generated = new byte[32];
                random.nextBytes(generated);
                Files.writeString(file, Base64.getEncoder().encodeToString(generated),
                        StandardCharsets.US_ASCII);
            } catch (FileAlreadyExistsException alreadyCreated) {
                // Another local process created the key; reuse it.
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create private local authenticator key", ex);
            }
        }
        try {
            return Files.readString(file, StandardCharsets.US_ASCII).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read private local authenticator key", ex);
        }
    }
}
