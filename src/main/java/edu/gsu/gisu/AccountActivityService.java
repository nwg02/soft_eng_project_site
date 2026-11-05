package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Classroom account workflows: local requests, local issue notes and
 * in-app notifications. No insurance agent, insurer or external recipient
 * receives records from these methods.
 */
@Service
public class AccountActivityService {
    private final JdbcTemplate db;

    public AccountActivityService(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> agentRequests(int customerId) {
        return db.queryForList(
                "SELECT agent_request_id, contact_method, preferred_time, topic, " +
                "request_status, created_at FROM agent_requests " +
                "WHERE customer_id = ? ORDER BY created_at DESC, agent_request_id DESC",
                customerId);
    }

    public List<Map<String, Object>> reports(int customerId) {
        return db.queryForList(
                "SELECT claim_record_id, claim_category, description, claim_status, " +
                "submitted_at, last_updated_at FROM insurance_claim_records " +
                "WHERE customer_id = ? ORDER BY submitted_at DESC, claim_record_id DESC",
                customerId);
    }

    public List<Map<String, Object>> notifications(int customerId) {
        return db.queryForList(
                "SELECT notification_id, notification_type, title, message, read_at, created_at " +
                "FROM user_notifications WHERE customer_id = ? " +
                "ORDER BY created_at DESC, notification_id DESC LIMIT 100",
                customerId);
    }

    public int unreadCount(int customerId) {
        return count(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? AND read_at IS NULL",
                customerId);
    }

    public int openReportCount(int customerId) {
        return count(
                "SELECT COUNT(*) FROM insurance_claim_records WHERE customer_id = ? AND claim_status = 'open'",
                customerId);
    }

    public int agentRequestCount(int customerId) {
        return count("SELECT COUNT(*) FROM agent_requests WHERE customer_id = ?", customerId);
    }

    private int count(String sql, int customerId) {
        Integer value = db.queryForObject(sql, Integer.class, customerId);
        return value == null ? 0 : value;
    }

    @Transactional
    public void createAgentRequest(int customerId, String channel, String preferredTime,
                                   String topic) {
        db.update(
                "INSERT INTO agent_requests " +
                "(customer_id, contact_method, preferred_time, topic, request_status) " +
                "VALUES (?, ?, ?, ?, 'demo_recorded')",
                customerId, channel, preferredTime, topic);
        addNotification(customerId, "agent_request", "Assistance preference saved",
                "Your assistance request was recorded in GISU for demonstration. " +
                "No agent was contacted and no callback has been scheduled.");
    }

    @Transactional
    public void createReport(int customerId, String category, String description) {
        db.update(
                "INSERT INTO insurance_claim_records " +
                "(customer_id, claim_category, description, claim_status) " +
                "VALUES (?, ?, ?, 'open')",
                customerId, category, description);
        addNotification(customerId, "report", "Issue report saved",
                "Your issue note is saved in your GISU account. " +
                "It has not been submitted to an insurance company.");
    }

    @Transactional
    public boolean updateReportStatus(int customerId, long reportId, String expected,
                                      String next) {
        if (!("open".equals(expected) && "closed".equals(next)) &&
            !("closed".equals(expected) && "open".equals(next))) {
            return false;
        }
        int affected = db.update(
                "UPDATE insurance_claim_records SET claim_status = ?, " +
                "last_updated_at = CURRENT_TIMESTAMP " +
                "WHERE customer_id = ? AND claim_record_id = ? AND claim_status = ?",
                next, customerId, reportId, expected);
        if (affected == 1) {
            addNotification(customerId, "report",
                    "Issue report " + ("closed".equals(next) ? "closed" : "reopened"),
                    "You updated a GISU issue note. " +
                    "This does not change any insurer claim or coverage.");
        }
        return affected == 1;
    }

    public boolean markRead(int customerId, long notificationId) {
        return db.update(
                "UPDATE user_notifications SET read_at = CURRENT_TIMESTAMP " +
                "WHERE customer_id = ? AND notification_id = ? AND read_at IS NULL",
                customerId, notificationId) == 1;
    }

    public int markAllRead(int customerId) {
        return db.update(
                "UPDATE user_notifications SET read_at = CURRENT_TIMESTAMP " +
                "WHERE customer_id = ? AND read_at IS NULL",
                customerId);
    }

    private void addNotification(int customerId, String type, String title, String message) {
        db.update(
                "INSERT INTO user_notifications " +
                "(customer_id, notification_type, title, message) VALUES (?, ?, ?, ?)",
                customerId, type, title, message);
    }
}
