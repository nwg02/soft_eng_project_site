package edu.gsu.gisu;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded single-instance rate limiter for high-risk POST endpoints. The
 * application's existing session chat throttle remains a second limit.
 * Railway multi-instance deployments need an ingress/distributed limiter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_KEYS = 8_000;
    private final Map<String, Window> counts = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong();
    private final boolean enabled;

    private record Window(long started, int count) {}

    public RequestRateLimitFilter(
            @Value("${gisu.security.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String remote = request.getRemoteAddr();
        String ip = remote == null || remote.isBlank() ? "unknown" : remote;
        boolean allowed = true;
        if ("/login".equals(path)) {
            allowed = allow("login-ip:" + ip, 60);
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                allowed = allow("login-id:" + digest(email.trim().toLowerCase(Locale.ROOT)), 10)
                        && allowed;
            }
        } else if ("/register".equals(path)) {
            allowed = allow("register-ip:" + ip, 25);
        } else if ("/api/chat".equals(path)) {
            allowed = allow("chat-ip:" + ip, 40);
        } else if (path.matches("/assessment/results/[0-9]+/ai")) {
            allowed = allow("assessment-ip:" + ip, 20);
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("user") instanceof Map<?, ?> user
                    && user.get("customer_id") instanceof Number id) {
                allowed = allow("assessment-user:" + id.intValue(), 6) && allowed;
            }
        } else if ("/login/verify".equals(path)) {
            allowed = allow("verify-ip:" + ip, 90);
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many attempts. Try again in a minute.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        cleanup(now);
        if (counts.size() >= MAX_KEYS && !counts.containsKey(key)) return false;
        Window result = counts.compute(key, (k, old) ->
                old == null || now - old.started >= WINDOW_MILLIS
                        ? new Window(now, 1) : new Window(old.started, old.count + 1));
        return result.count <= limit;
    }

    private void cleanup(long now) {
        long previous = lastCleanup.get();
        if (now - previous < WINDOW_MILLIS ||
                !lastCleanup.compareAndSet(previous, now)) return;
        counts.entrySet().removeIf(e -> now - e.getValue().started >= WINDOW_MILLIS);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Missing SHA-256", ex);
        }
    }
}
