package com.sltours.audit;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityId, String detail) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "system" : authentication.getName();
        jdbc.update("insert into admin_audit_events(id, actor, action, entity_id, detail, created_at) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), actor, action, entityId, detail, OffsetDateTime.now());
    }
}
