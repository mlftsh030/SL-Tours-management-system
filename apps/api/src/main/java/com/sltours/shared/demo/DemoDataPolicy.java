package com.sltours.shared.demo;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Development sample rows (flagged by migration V12) are usable only with the dev or test profile. Everywhere else
 * they are excluded from availability, assignment, public listings and dashboard figures, and shown with a badge.
 */
@Component
public class DemoDataPolicy {
    private final boolean usable;
    @org.springframework.beans.factory.annotation.Autowired
    public DemoDataPolicy(Environment environment) {
        // force-exclude lets tests exercise production behaviour; it can only remove access, never grant it.
        this.usable = environment.acceptsProfiles(Profiles.of("dev", "test"))
            && !environment.getProperty("sltours.demo-data.force-exclude", Boolean.class, false);
    }
    public DemoDataPolicy(boolean usable) { this.usable = usable; }
    public boolean demoDataUsable() { return usable; }
    public boolean excludes(boolean demoData) { return demoData && !usable; }
}
