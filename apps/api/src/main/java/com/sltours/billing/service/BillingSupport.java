package com.sltours.billing.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.audit.AuditService;
import com.sltours.billing.model.BillingTypes.*;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JSON storage, the acting user, and the per-document history that also feeds the admin audit log. */
@Component
public class BillingSupport {
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public BillingSupport(ObjectMapper json, JdbcTemplate jdbc, AuditService audit) { this.json = json; this.jdbc = jdbc; this.audit = audit; }

    public String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not store document data", e); }
    }
    public <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored document data could not be read", e); }
    }

    public static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    /** Records a history entry on the document and the same action in the admin audit log. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void event(UUID documentId, UUID paymentId, String event, Channel channel, String detail) {
        event(documentId, paymentId, event, channel, detail, actor());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void event(UUID documentId, UUID paymentId, String event, Channel channel, String detail, String actor) {
        String trimmed = detail == null ? null : detail.length() > 480 ? detail.substring(0, 480) + "…" : detail;
        jdbc.update("insert into billing_events (id, document_id, payment_id, event, channel, actor, detail, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), documentId, paymentId, event, channel == null ? null : channel.name(), actor, trimmed, OffsetDateTime.now());
        audit.record("BILLING_" + event, documentId.toString(), trimmed == null ? event : trimmed);
    }
}
