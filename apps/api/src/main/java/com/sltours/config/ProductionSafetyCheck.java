package com.sltours.config;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when development-only behaviour could reach a real deployment: sample private-trip rates
 * shown as prices, the development mock assistant, the dev profile (demo driver sign-ins) mixed with a production profile, or
 * account emails going to a local mail catcher instead of a real provider, or a production profile without an explicit
 * search-indexing choice.
 */
@Component
public class ProductionSafetyCheck {
    static final Set<String> PRODUCTION_PROFILES = Set.of("production", "prod");
    public ProductionSafetyCheck(Environment environment) { verify(environment); }

    static void verify(Environment environment) {
        boolean devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean production = Arrays.stream(environment.getActiveProfiles()).anyMatch(PRODUCTION_PROFILES::contains);
        if (production && devOrTest)
            throw new IllegalStateException("The dev/test profile cannot be combined with a production profile");
        boolean sampleRates = environment.getProperty("sltours.pricing.allow-sample-rates", Boolean.class, false);
        if (sampleRates && !devOrTest)
            throw new IllegalStateException("sltours.pricing.allow-sample-rates is only permitted with the dev or test profile");
        if ("mock".equalsIgnoreCase(environment.getProperty("sltours.assistant.provider", "none")) && !devOrTest)
            throw new IllegalStateException("The development mock assistant is only permitted with the dev or test profile");
        String smtpHost = environment.getProperty("sltours.mail.smtp.host", "").trim().toLowerCase(Locale.ROOT);
        if (production && "smtp".equalsIgnoreCase(environment.getProperty("sltours.mail.provider", "none"))
                && Set.of("mailpit", "localhost", "127.0.0.1").contains(smtpHost))
            throw new IllegalStateException("Production account emails cannot use the local mail catcher (" + smtpHost + "); configure a real SMTP provider");
        // Staging keeps pages out of search results; production must choose deliberately so neither setting leaks across.
        String indexing = environment.getProperty("sltours.seo.indexing", "").trim();
        if (production && !Set.of("true", "false").contains(indexing.toLowerCase(Locale.ROOT)))
            throw new IllegalStateException("Set SLTOURS_SEO_INDEXING explicitly in production (true for the public site, false for a staging copy)");
    }
}
