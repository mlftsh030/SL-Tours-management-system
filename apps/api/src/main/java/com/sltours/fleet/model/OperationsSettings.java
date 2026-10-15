package com.sltours.fleet.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Single-row operational and pricing settings, editable by administrators. */
@Entity
@Table(name = "operations_settings")
public class OperationsSettings {
    public static final short ID = 1;
    @Id private short id = ID;
    @Version private Long version;
    @Column(nullable = false) private int turnaroundMinutes;
    @Column(nullable = false) private int billingIncrementMinutes;
    private Integer includedKmPerHour;
    private Integer excessKmRateCents;
    private Integer overtimeHourlyRateCents;
    @Column(nullable = false, length = 180) private String updatedBy;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    public long getVersion() { return version == null ? 0 : version; }
    public int getTurnaroundMinutes() { return turnaroundMinutes; }
    public void setTurnaroundMinutes(int value) { turnaroundMinutes = value; }
    public int getBillingIncrementMinutes() { return billingIncrementMinutes; }
    public void setBillingIncrementMinutes(int value) { billingIncrementMinutes = value; }
    public Integer getIncludedKmPerHour() { return includedKmPerHour; }
    public void setIncludedKmPerHour(Integer value) { includedKmPerHour = value; }
    public Integer getExcessKmRateCents() { return excessKmRateCents; }
    public void setExcessKmRateCents(Integer value) { excessKmRateCents = value; }
    public Integer getOvertimeHourlyRateCents() { return overtimeHourlyRateCents; }
    public void setOvertimeHourlyRateCents(Integer value) { overtimeHourlyRateCents = value; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch(String actor) { updatedBy = actor; updatedAt = OffsetDateTime.now(); }
}
