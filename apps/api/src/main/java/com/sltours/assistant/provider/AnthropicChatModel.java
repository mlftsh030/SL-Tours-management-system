package com.sltours.assistant.provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sltours.assistant.model.ChatMessage;
import com.sltours.assistant.model.ChatMessage.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * Anthropic Messages API adapter. The API key stays on the server; requests carry only the system prompt, the
 * conversation and public tool results. Errors are reported without request or response bodies.
 */
public class AnthropicChatModel implements ChatModel {
    private static final String API_VERSION = "2023-06-01";
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public AnthropicChatModel(String baseUrl, String apiKey, String model, ObjectMapper json) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = json;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/messages");
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override public String name() { return "anthropic:" + model; }

    @Override
    public Response send(Request request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", request.maxOutputTokens());
        body.put("system", request.system());
        ArrayNode tools = body.putArray("tools");
        for (ToolSpec t : request.tools())
            tools.addObject().put("name", t.name()).put("description", t.description()).set("input_schema", t.inputSchema());
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : request.messages()) {
            ObjectNode message = messages.addObject().put("role", m.role() == ChatMessage.Role.USER ? "user" : "assistant");
            ArrayNode content = message.putArray("content");
            for (Block b : m.blocks()) {
                switch (b) {
                    case Text t -> content.addObject().put("type", "text").put("text", t.text());
                    case ToolUse u -> content.addObject().put("type", "tool_use").put("id", u.id()).put("name", u.name()).set("input", u.input());
                    case ToolResult r -> content.addObject().put("type", "tool_result").put("tool_use_id", r.toolUseId())
                        .put("content", r.content()).put("is_error", r.error());
                }
            }
        }
        HttpResponse<String> response;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(request.timeout())
                .header("content-type", "application/json").header("x-api-key", apiKey).header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new UnavailableException("Model request timed out", e);
        } catch (IOException e) {
            throw new UnavailableException("Model request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("Model request interrupted", e);
        }
        if (response.statusCode() != 200) throw new UnavailableException("Model provider returned HTTP " + response.statusCode());
        try {
            JsonNode root = json.readTree(response.body());
            List<Block> blocks = new ArrayList<>();
            for (JsonNode c : root.path("content")) {
                switch (c.path("type").asText()) {
                    case "text" -> blocks.add(new Text(c.path("text").asText()));
                    case "tool_use" -> blocks.add(new ToolUse(c.path("id").asText(), c.path("name").asText(), c.path("input")));
                    default -> { } // Other block types are not used by this integration.
                }
            }
            if (blocks.isEmpty()) throw new UnavailableException("Model returned no usable content");
            JsonNode usage = root.path("usage");
            return new Response(new ChatMessage(ChatMessage.Role.ASSISTANT, blocks), root.path("stop_reason").asText(),
                usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt());
        } catch (IOException e) {
            throw new UnavailableException("Model returned an unreadable response", e);
        }
    }
}
