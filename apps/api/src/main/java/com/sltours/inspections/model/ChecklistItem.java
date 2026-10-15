package com.sltours.inspections.model;

/**
 * Condition checklist completed at collection and return. A defect on a safety-critical item is always
 * treated as a serious safety defect, whatever severity the driver selects.
 */
public enum ChecklistItem {
    TYRES_WHEELS("Tyres, tread and wheels", true),
    BRAKES("Brakes and parking brake", true),
    LIGHTS_INDICATORS("Headlights, brake lights and indicators", true),
    SEATBELTS("Seatbelts for every seat", true),
    STEERING("Steering feels normal", true),
    DASHBOARD_WARNINGS("No dashboard warning lights", true),
    WINDSCREEN_WINDOWS("Windscreen and windows", false),
    MIRRORS("Mirrors", false),
    WIPERS("Wipers and washer fluid", false),
    FLUID_LEAKS("No visible fluid leaks", false),
    BODY_EXTERIOR("Body panels, bumpers and paint", false),
    INTERIOR("Interior clean and undamaged", false),
    AIR_CONDITIONING("Air conditioning", false),
    SAFETY_EQUIPMENT("First-aid kit, fire extinguisher and triangle", false),
    SPARE_WHEEL_TOOLS("Spare wheel and jack", false),
    LICENCE_DISC("Valid licence disc displayed", false);

    public final String label;
    public final boolean safetyCritical;
    ChecklistItem(String label, boolean safetyCritical) { this.label = label; this.safetyCritical = safetyCritical; }
}
