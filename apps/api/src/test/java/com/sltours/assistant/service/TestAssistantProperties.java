package com.sltours.assistant.service;
import java.math.BigDecimal;
import java.time.Duration;

/** Assistant settings for tests, with the production defaults unless overridden. */
public class TestAssistantProperties {
    public String provider = "mock", apiKey;
    public Duration requestTimeout = Duration.ofSeconds(20), turnTimeout = Duration.ofSeconds(45), idle = Duration.ofMinutes(30);
    public int maxToolRounds = 6, maxTurns = 20, perSession = 30, perAddress = 60, maxConversations = 500, dailyRequests = 2000;
    public BigDecimal inputPrice, outputPrice, dailyLimit, monthlyLimit;

    public AssistantProperties build() {
        return new AssistantProperties(provider, "test-model", apiKey, "http://localhost", requestTimeout, turnTimeout, 900, maxToolRounds, 1000,
            maxTurns, perSession, perAddress, maxConversations, idle, dailyRequests, inputPrice, outputPrice, dailyLimit, monthlyLimit);
    }
}
