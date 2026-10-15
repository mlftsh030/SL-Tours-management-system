package com.sltours.assistant.service;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Planning assistant settings. Live chat stays disabled unless a provider, credentials and every spending control are
 * configured. Token prices are not built in: set them from the provider's current price list.
 */
@ConfigurationProperties("sltours.assistant")
public record AssistantProperties(
    /** none, anthropic, or mock (development and test profiles only). */
    @DefaultValue("none") String provider,
    @DefaultValue("claude-sonnet-5-5") String model,
    String apiKey,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("20s") Duration requestTimeout,
    @DefaultValue("45s") Duration turnTimeout,
    @DefaultValue("900") int maxOutputTokens,
    @DefaultValue("6") int maxToolRounds,
    @DefaultValue("1000") int maxMessageChars,
    @DefaultValue("20") int maxTurnsPerConversation,
    @DefaultValue("30") int messagesPerSessionPerHour,
    @DefaultValue("60") int messagesPerAddressPerHour,
    @DefaultValue("500") int maxActiveConversations,
    @DefaultValue("30m") Duration conversationIdleTimeout,
    @DefaultValue("2000") int dailyModelRequestLimit,
    BigDecimal inputUsdPerMillionTokens,
    BigDecimal outputUsdPerMillionTokens,
    BigDecimal dailySpendLimitUsd,
    BigDecimal monthlySpendLimitUsd) {

    public boolean live() { return "anthropic".equalsIgnoreCase(provider); }
    public boolean mock() { return "mock".equalsIgnoreCase(provider); }

    /** Why live chat cannot run, or null when it can. */
    public String liveConfigurationProblem() {
        if (apiKey == null || apiKey.isBlank()) return "No model credentials are configured";
        if (inputUsdPerMillionTokens == null || outputUsdPerMillionTokens == null) return "Token prices for the spending controls are not configured";
        if (dailySpendLimitUsd == null || monthlySpendLimitUsd == null) return "Daily and monthly spending limits are not configured";
        if (dailySpendLimitUsd.signum() <= 0 || monthlySpendLimitUsd.signum() <= 0) return "Spending limits must be above zero";
        return null;
    }
}
