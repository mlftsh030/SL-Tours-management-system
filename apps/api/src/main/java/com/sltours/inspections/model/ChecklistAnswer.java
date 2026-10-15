package com.sltours.inspections.model;
import jakarta.validation.constraints.*;

/** One checklist line. Defects need a description; safety-critical items are escalated automatically. */
public record ChecklistAnswer(@NotNull ChecklistItem item, @NotNull Result result, Severity severity, @Size(max = 500) String note) {
    public enum Result { OK, DEFECT }
    public enum Severity { MINOR, SAFETY }
    public boolean isDefect() { return result == Result.DEFECT; }
    public boolean isSafetyDefect() { return isDefect() && (item.safetyCritical || severity == Severity.SAFETY); }
}
