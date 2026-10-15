package com.sltours.activities.service;
import com.sltours.activities.model.ActivityEnums.Area;
import com.sltours.audit.AuditService;
import com.sltours.shared.error.ConflictException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Travel-time planning assumptions. Minutes = straight-line distance between area centres x road factor at the average
 * speed, plus a buffer for parking and walking. These are estimates shown with every itinerary, not measured routes.
 */
@Service
public class PlanningService {
    public record Settings(long version, BigDecimal roadFactor, int averageSpeedKmh, int bufferMinutes, int lunchMinutes, String updatedBy) {}
    public record AreaPoint(Area area, String label, double latitude, double longitude) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public PlanningService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public Settings settings() {
        return jdbc.queryForObject("select version, road_factor, average_speed_kmh, buffer_minutes, lunch_minutes, updated_by from planning_settings where id = 1",
            (rs, i) -> new Settings(rs.getLong(1), rs.getBigDecimal(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6)));
    }
    public Map<Area, AreaPoint> areas() {
        Map<Area, AreaPoint> map = new EnumMap<>(Area.class);
        jdbc.query("select area, label, latitude, longitude from planning_areas", rs -> {
            Area area = Area.valueOf(rs.getString(1));
            map.put(area, new AreaPoint(area, rs.getString(2), rs.getDouble(3), rs.getDouble(4)));
        });
        return map;
    }

    @Transactional
    public Settings update(long version, BigDecimal roadFactor, int speed, int buffer, int lunch) {
        String actor = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(a -> a.getName()).orElse("system");
        int rows = jdbc.update("""
            update planning_settings set road_factor = ?, average_speed_kmh = ?, buffer_minutes = ?, lunch_minutes = ?, version = version + 1,
            updated_by = ?, updated_at = now() where id = 1 and version = ?""", roadFactor, speed, buffer, lunch, actor, version);
        if (rows == 0) throw new ConflictException("Planning settings changed. Reload before saving.");
        audit.record("PLANNING_SETTINGS_UPDATED", "planning_settings", "roadFactor=" + roadFactor + "; speed=" + speed + "; buffer=" + buffer + "; lunch=" + lunch);
        return settings();
    }

    /** Estimated driving minutes between two areas, rounded up to 5 minutes. */
    public static int travelMinutes(AreaPoint from, AreaPoint to, Settings s) {
        double km = from.area() == to.area() ? 0 : haversineKm(from.latitude(), from.longitude(), to.latitude(), to.longitude());
        double minutes = km * s.roadFactor().doubleValue() / s.averageSpeedKmh() * 60 + s.bufferMinutes();
        return (int) Math.ceil(minutes / 5.0) * 5;
    }
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
    public String assumption(Settings s) {
        return "Travel times are SL Tours planning estimates: straight-line distance between area centres x " + s.roadFactor()
            + " at " + s.averageSpeedKmh() + " km/h, plus " + s.bufferMinutes() + " minutes for parking and walking. They do not reflect live traffic.";
    }
}
