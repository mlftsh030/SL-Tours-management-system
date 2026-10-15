package com.sltours.privatetrips.service;
import com.sltours.privatetrips.dto.PrivateTripDtos.VehicleOption;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The vehicles customers can ask for. Requests are matched to a fleet vehicle when SL Tours confirms the trip. */
@Service
public class VehicleOptionService {
    private final JdbcTemplate jdbc;
    public VehicleOptionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String COLUMNS = "slug, name, type_label, fleet_category, passenger_capacity, capacity_status, capacity_note, description, "
        + "image_url, image_thumbnail_url, image_alt, image_kind, image_credit, image_source_page, image_licence, image_licence_url, checked_on";

    public List<VehicleOption> published() {
        return jdbc.query("select " + COLUMNS + " from vehicle_options where published order by sort_order", (rs, i) -> map(rs));
    }

    public Optional<VehicleOption> find(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        return jdbc.query("select " + COLUMNS + " from vehicle_options where published and slug = ?", (rs, i) -> map(rs), slug.trim()).stream().findFirst();
    }

    private static VehicleOption map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VehicleOption(rs.getString("slug"), rs.getString("name"), rs.getString("type_label"),
            com.sltours.fleet.model.VehicleCategory.valueOf(rs.getString("fleet_category")), rs.getInt("passenger_capacity"),
            "CONFIRMED".equals(rs.getString("capacity_status")), rs.getString("capacity_note"), rs.getString("description"), rs.getString("image_url"),
            rs.getString("image_thumbnail_url"), rs.getString("image_alt"), "ILLUSTRATIVE".equals(rs.getString("image_kind")), rs.getString("image_credit"),
            rs.getString("image_source_page"), rs.getString("image_licence"), rs.getString("image_licence_url"), rs.getObject("checked_on", java.time.LocalDate.class));
    }
}
