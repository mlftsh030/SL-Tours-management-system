package com.sltours.assistant.service;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * Plain-text descriptions of planner options built only from the planner's own figures. Used when the model is
 * unavailable, when a model reply fails the checks, and by the development mock.
 */
public final class Narrator {
    private Narrator() {}

    public static String options(List<JsonNode> options) {
        if (options.isEmpty()) return "I couldn't put together a plan with those details.";
        StringBuilder b = new StringBuilder();
        for (JsonNode o : options) b.append(option(o)).append("\n\n");
        b.append("Each cost below shows whether it is a published price, an assumption, an estimate or not verified, with its source and check date.");
        return b.toString().trim();
    }

    public static String option(JsonNode o) {
        StringBuilder b = new StringBuilder("Option ").append(o.path("id").asText()).append(": ").append(o.path("label").asText());
        for (JsonNode s : o.path("stops")) {
            if ("RETURN".equals(s.path("type").asText())) continue;
            b.append("\n- ").append(s.path("time").asText()).append(" ").append(s.path("title").asText()).append(" (").append(s.path("area").asText()).append(")");
            for (JsonNode c : s.path("cautions")) b.append("\n  Caution: ").append(c.asText());
        }
        b.append("\nKnown costs: ").append(o.path("knownTotal").asText());
        int unknown = o.path("costsWithoutVerifiedPrice").asInt();
        if (o.has("estimateTotal")) b.append(", of which ").append(o.path("estimateTotal").asText()).append(" is estimated");
        if (unknown > 0) b.append(". ").append(unknown).append(unknown == 1 ? " cost has" : " costs have").append(" no verified price");
        b.append(".\n").append(o.path("verdictMessage").asText());
        List<String> left = new ArrayList<>();
        o.path("leftOut").forEach(l -> left.add(l.asText()));
        if (!left.isEmpty()) b.append("\nLeft out: ").append(String.join("; ", left)).append(".");
        return b.toString();
    }
}
