package com.sltours.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionSafetyCheckTest {
    private MockEnvironment env(String... profiles) { var e = new MockEnvironment(); e.setActiveProfiles(profiles); return e; }

    @Test void productionCannotShowSampleRatesEvenWhenConfigured() {
        var production = env("production").withProperty("sltours.pricing.allow-sample-rates", "true");
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(production)).hasMessageContaining("allow-sample-rates");
        var noProfile = env().withProperty("sltours.pricing.allow-sample-rates", "true");
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(noProfile)).hasMessageContaining("allow-sample-rates");
    }
    @Test void devProfileCannotBeCombinedWithProduction() {
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env("production", "dev"))).hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env("prod", "dev"))).hasMessageContaining("cannot be combined");
    }
    @Test void developmentMockAssistantIsRefusedOutsideDevAndTest() {
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env("production").withProperty("sltours.assistant.provider", "mock")))
            .hasMessageContaining("mock assistant");
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env().withProperty("sltours.assistant.provider", "MOCK"))).hasMessageContaining("mock assistant");
        assertThatCode(() -> ProductionSafetyCheck.verify(env("dev").withProperty("sltours.assistant.provider", "mock"))).doesNotThrowAnyException();
    }
    @Test void productionMustChooseSearchIndexingExplicitly() {
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env("production"))).hasMessageContaining("SLTOURS_SEO_INDEXING");
        // Docker Compose passes the variable through blank when it is not set.
        assertThatThrownBy(() -> ProductionSafetyCheck.verify(env("production").withProperty("sltours.seo.indexing", ""))).hasMessageContaining("SLTOURS_SEO_INDEXING");
        assertThatCode(() -> ProductionSafetyCheck.verify(env("production").withProperty("sltours.seo.indexing", "false"))).doesNotThrowAnyException();
        assertThatCode(() -> ProductionSafetyCheck.verify(env("dev"))).doesNotThrowAnyException();
    }
    @Test void normalConfigurationsStart() {
        assertThatCode(() -> ProductionSafetyCheck.verify(env("production").withProperty("sltours.seo.indexing", "true"))).doesNotThrowAnyException();
        assertThatCode(() -> ProductionSafetyCheck.verify(env("dev").withProperty("sltours.pricing.allow-sample-rates", "true"))).doesNotThrowAnyException();
    }
}
