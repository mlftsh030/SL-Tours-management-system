package com.sltours.assistant.provider;
import com.fasterxml.jackson.databind.*;
import com.sltours.assistant.model.ChatMessage;
import com.sltours.assistant.model.ChatMessage.*;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises the adapter against a local stand-in for the Messages API. */
class AnthropicChatModelTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>(), version = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "";
    private volatile long delayMillis;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            version.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) { }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private AnthropicChatModel model() { return new AnthropicChatModel("http://127.0.0.1:" + server.getAddress().getPort() + "/", "secret-key", "claude-test", json); }
    private ChatModel.Request request(Duration timeout) throws Exception {
        var history = List.of(ChatMessage.user("Plan my day"),
            new ChatMessage(ChatMessage.Role.ASSISTANT, List.of(new ToolUse("t1", "search_activities", json.readTree("{\"text\":\"penguins\"}")))),
            new ChatMessage(ChatMessage.Role.USER, List.of(new ToolResult("t1", "{\"count\":1}", false))));
        return new ChatModel.Request("system rules", history, List.of(new ChatModel.ToolSpec("search_activities", "Search", json.readTree("{\"type\":\"object\"}"))),
            500, timeout);
    }

    @Test void sendsToolsAndHistoryWithServerSideCredentialsAndParsesToolCalls() throws Exception {
        response = """
            {"content":[{"type":"text","text":"Let me plan that."},{"type":"tool_use","id":"t2","name":"plan_options","input":{}}],
             "stop_reason":"tool_use","usage":{"input_tokens":1200,"output_tokens":45}}""";
        ChatModel.Response r = model().send(request(Duration.ofSeconds(5)));
        assertThat(apiKey.get()).isEqualTo("secret-key");
        assertThat(version.get()).isEqualTo("2023-06-01");
        JsonNode sent = json.readTree(body.get());
        assertThat(sent.path("model").asText()).isEqualTo("claude-test");
        assertThat(sent.path("system").asText()).isEqualTo("system rules");
        assertThat(sent.path("tools").get(0).path("input_schema").path("type").asText()).isEqualTo("object");
        assertThat(sent.path("messages").get(1).path("content").get(0).path("type").asText()).isEqualTo("tool_use");
        assertThat(sent.path("messages").get(2).path("content").get(0).path("tool_use_id").asText()).isEqualTo("t1");
        assertThat(r.message().text()).isEqualTo("Let me plan that.");
        assertThat(r.message().toolUses()).singleElement().satisfies(u -> assertThat(u.name()).isEqualTo("plan_options"));
        assertThat(r.inputTokens()).isEqualTo(1200);
        assertThat(r.outputTokens()).isEqualTo(45);
    }

    @Test void providerErrorsAndTimeoutsAreReportedWithoutContent() throws Exception {
        status = 529; response = "{\"error\":{\"message\":\"overloaded: private detail\"}}";
        assertThatThrownBy(() -> model().send(request(Duration.ofSeconds(5)))).isInstanceOf(ChatModel.UnavailableException.class)
            .hasMessage("Model provider returned HTTP 529");
        status = 200; response = "not json";
        assertThatThrownBy(() -> model().send(request(Duration.ofSeconds(5)))).hasMessageContaining("unreadable");
        response = "{\"content\":[{\"type\":\"text\",\"text\":\"late\"}]}"; delayMillis = 1500;
        assertThatThrownBy(() -> model().send(request(Duration.ofMillis(300)))).hasMessageContaining("timed out");
    }
}
