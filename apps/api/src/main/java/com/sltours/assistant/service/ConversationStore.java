package com.sltours.assistant.service;
import com.sltours.assistant.model.Conversation;
import com.sltours.shared.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory conversations and per-session and per-address message limits. Nothing here is persisted: a restart or the
 * idle timeout removes conversations. Single-instance deployments only; a shared store is needed to scale out.
 */
@Component
public class ConversationStore {
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final AssistantProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ConversationStore(AssistantProperties properties) { this(properties, Clock.systemUTC()); }
    ConversationStore(AssistantProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }

    public Conversation create(String sessionKey) {
        purge();
        if (conversations.size() >= properties.maxActiveConversations())
            throw new LimitException("The assistant is busy. Please use the day planner or try again later.");
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        Conversation c = new Conversation(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), sessionKey, clock.instant());
        conversations.put(c.id, c);
        return c;
    }

    public Conversation get(String id, String sessionKey) {
        purge();
        Conversation c = conversations.get(id);
        // Another session's conversation is reported exactly like a missing one.
        if (c == null || !c.sessionKey.equals(sessionKey)) throw new ResourceNotFoundException("This conversation has ended. Start a new one.");
        c.lastActive = clock.instant();
        return c;
    }

    public void delete(String id, String sessionKey) {
        Conversation c = conversations.get(id);
        if (c != null && c.sessionKey.equals(sessionKey)) conversations.remove(id);
    }

    /** Counts a visitor message against the hourly limits for the session and the network address. */
    public void checkRate(String sessionKey, String address) {
        Instant now = clock.instant();
        if (!allow("s:" + sessionKey, properties.messagesPerSessionPerHour(), now) || !allow("a:" + address, properties.messagesPerAddressPerHour(), now))
            throw new LimitException("You've sent a lot of messages. Please wait a while, or use the day planner.");
    }

    private boolean allow(String key, int limit, Instant now) {
        if (recent.size() > 20_000) recent.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(now.minus(Duration.ofHours(1))));
        Deque<Instant> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) times.pollFirst();
            if (times.size() >= limit) return false;
            times.addLast(now);
            return true;
        }
    }

    private void purge() {
        Instant cutoff = clock.instant().minus(properties.conversationIdleTimeout());
        conversations.values().removeIf(c -> c.lastActive.isBefore(cutoff));
    }
    int size() { purge(); return conversations.size(); }

    public static class LimitException extends RuntimeException {
        public LimitException(String message) { super(message); }
    }
}
