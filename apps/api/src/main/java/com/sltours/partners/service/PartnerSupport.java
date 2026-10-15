package com.sltours.partners.service;
import com.sltours.partners.model.PartnerEnums.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Shared lookups for the partnership services. */
@Component
public class PartnerSupport {
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private final JdbcTemplate jdbc;
    public PartnerSupport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<RouteStatus> routeStatus(UUID partnerId, Route route) {
        return jdbc.query("select status from partner_routes where partner_id = ? and route = ?",
            (rs, i) -> RouteStatus.valueOf(rs.getString(1)), partnerId, route.name()).stream().findFirst();
    }
    public boolean active(UUID partnerId, Route route) { return routeStatus(partnerId, route).orElse(null) == RouteStatus.APPROVED; }
    public String name(UUID partnerId) {
        return partnerId == null ? null : jdbc.query("select business_name from partners where id = ?", (rs, i) -> rs.getString(1), partnerId)
            .stream().findFirst().orElse(null);
    }
    /** Locks the partner row so payouts and decisions for one partner run one at a time. */
    public void lock(UUID partnerId) {
        if (jdbc.query("select id from partners where id = ? for update", (rs, i) -> rs.getObject(1), partnerId).isEmpty())
            throw new com.sltours.shared.error.ResourceNotFoundException("Partner not found");
    }
    public static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
    public static String rand(long cents) {
        return (cents < 0 ? "-" : "") + String.format(Locale.ROOT, "R%,.2f", Math.abs(cents) / 100.0).replace(',', ' ');
    }
    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static OffsetDateTime time(java.sql.Timestamp t) { return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC); }
}
