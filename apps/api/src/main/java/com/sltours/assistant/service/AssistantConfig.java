package com.sltours.assistant.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.activities.service.ActivityService;
import com.sltours.assistant.dto.AssistantDtos.Mode;
import com.sltours.assistant.provider.*;
import java.time.*;
import org.slf4j.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {
    private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

    @Bean
    AssistantRuntime assistantRuntime(AssistantProperties p, ObjectMapper json, ActivityService activities, Environment env) {
        if (p.mock()) {
            if (!env.acceptsProfiles(Profiles.of("dev", "test")))
                throw new IllegalStateException("The development mock assistant is only permitted with the dev or test profile");
            log.info("Planning assistant: development mock (not a language model)");
            return new AssistantRuntime(Mode.MOCK, new MockChatModel(json, activities, Clock.system(ZoneId.of("Africa/Johannesburg"))), null);
        }
        if (p.live()) {
            String problem = p.liveConfigurationProblem();
            if (problem != null) {
                log.warn("Planning assistant disabled: {}", problem);
                return AssistantRuntime.disabled(problem);
            }
            log.info("Planning assistant: live model {}", p.model());
            return new AssistantRuntime(Mode.LIVE, new AnthropicChatModel(p.baseUrl(), p.apiKey(), p.model(), json), null);
        }
        if (!"none".equalsIgnoreCase(p.provider())) return AssistantRuntime.disabled("Unknown assistant provider");
        return AssistantRuntime.disabled("Live chat is not configured");
    }
}
