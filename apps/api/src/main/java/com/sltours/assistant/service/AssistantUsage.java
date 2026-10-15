package com.sltours.assistant.service;
import java.math.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Daily aggregate counters behind the spending controls. Stores no message content or visitor identifiers. */
@Component
public class AssistantUsage {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private final JdbcTemplate jdbc;
    private final AssistantProperties properties;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public AssistantUsage(JdbcTemplate jdbc, AssistantProperties properties) { this(jdbc, properties, Clock.system(ZONE)); }
    AssistantUsage(JdbcTemplate jdbc, AssistantProperties properties, Clock clock) { this.jdbc = jdbc; this.properties = properties; this.clock = clock; }

    /** Null when another model request is allowed, otherwise the limit that has been reached. */
    public String limitReached() {
        LocalDate today = LocalDate.now(clock);
        Long requests = jdbc.query("select model_requests from assistant_usage_days where usage_date = ?", rs -> rs.next() ? rs.getLong(1) : 0L, today);
        if (requests != null && requests >= properties.dailyModelRequestLimit()) return "daily request limit";
        if (!properties.live()) return null;
        long day = spent(today, today), month = spent(today.withDayOfMonth(1), today);
        if (day >= micro(properties.dailySpendLimitUsd())) return "daily spending limit";
        if (month >= micro(properties.monthlySpendLimitUsd())) return "monthly spending limit";
        return null;
    }

    public void recordRequest(int inputTokens, int outputTokens) {
        long cost = 0;
        if (properties.live()) {
            BigDecimal usd = properties.inputUsdPerMillionTokens().multiply(BigDecimal.valueOf(inputTokens))
                .add(properties.outputUsdPerMillionTokens().multiply(BigDecimal.valueOf(outputTokens)));
            cost = usd.setScale(0, RoundingMode.CEILING).longValueExact(); // USD per million tokens x tokens = micro-USD
        }
        jdbc.update("""
            insert into assistant_usage_days (usage_date, model_requests, input_tokens, output_tokens, cost_micro_usd) values (?, 1, ?, ?, ?)
            on conflict (usage_date) do update set model_requests = assistant_usage_days.model_requests + 1,
                input_tokens = assistant_usage_days.input_tokens + excluded.input_tokens,
                output_tokens = assistant_usage_days.output_tokens + excluded.output_tokens,
                cost_micro_usd = assistant_usage_days.cost_micro_usd + excluded.cost_micro_usd""",
            LocalDate.now(clock), inputTokens, outputTokens, cost);
    }
    public void recordReplaced() { increment("replies_replaced"); }
    public void recordFailure() { increment("provider_failures"); }

    private void increment(String column) {
        jdbc.update("insert into assistant_usage_days (usage_date, " + column + ") values (?, 1) on conflict (usage_date) do update set "
            + column + " = assistant_usage_days." + column + " + 1", LocalDate.now(clock));
    }
    private long spent(LocalDate from, LocalDate to) {
        Long v = jdbc.queryForObject("select coalesce(sum(cost_micro_usd), 0) from assistant_usage_days where usage_date between ? and ?", Long.class, from, to);
        return v == null ? 0 : v;
    }
    private static long micro(BigDecimal usd) { return usd.movePointRight(6).setScale(0, RoundingMode.FLOOR).longValueExact(); }
}
