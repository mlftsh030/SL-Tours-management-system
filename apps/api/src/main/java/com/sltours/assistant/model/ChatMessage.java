package com.sltours.assistant.model;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Provider-neutral conversation message. Tool calls and results are blocks so any tool-calling model can be adapted. */
public record ChatMessage(Role role, List<Block> blocks) {
    public enum Role { USER, ASSISTANT }
    public sealed interface Block permits Text, ToolUse, ToolResult {}
    public record Text(String text) implements Block {}
    public record ToolUse(String id, String name, JsonNode input) implements Block {}
    public record ToolResult(String toolUseId, String content, boolean error) implements Block {}

    public static ChatMessage user(String text) { return new ChatMessage(Role.USER, List.of(new Text(text))); }
    public static ChatMessage assistant(String text) { return new ChatMessage(Role.ASSISTANT, List.of(new Text(text))); }
    public String text() {
        return blocks.stream().filter(Text.class::isInstance).map(b -> ((Text) b).text()).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
    public List<ToolUse> toolUses() { return blocks.stream().filter(ToolUse.class::isInstance).map(ToolUse.class::cast).toList(); }
}
