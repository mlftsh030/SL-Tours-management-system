package com.sltours.assistant.provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.sltours.assistant.model.ChatMessage;
import java.time.Duration;
import java.util.List;

/** Adapter for a tool-calling language model. Implementations hold their own credentials and never see other data. */
public interface ChatModel {
    record ToolSpec(String name, String description, JsonNode inputSchema) {}
    record Request(String system, List<ChatMessage> messages, List<ToolSpec> tools, int maxOutputTokens, Duration timeout) {}
    record Response(ChatMessage message, String stopReason, int inputTokens, int outputTokens) {}

    Response send(Request request);
    /** Short label for logs and the status endpoint; never includes credentials. */
    String name();

    /** The provider could not be reached or returned an unusable answer. The message is safe to log (no content). */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message) { super(message); }
        public UnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
