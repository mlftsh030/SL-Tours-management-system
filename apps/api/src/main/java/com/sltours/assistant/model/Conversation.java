package com.sltours.assistant.model;
import java.time.Instant;
import java.util.*;

/**
 * Server-side conversation state, held in memory only and discarded after the idle timeout or when the visitor clears it.
 * Bound to the visitor's HTTP session so another visitor cannot read or continue it.
 */
public class Conversation {
    public final String id;
    public final String sessionKey;
    public Instant lastActive;
    public final List<ChatMessage> messages = new ArrayList<>();
    public final TripBrief brief = new TripBrief();
    public final LinkedHashMap<String, PlanOption> options = new LinkedHashMap<>();
    /** Figures the visitor may be shown: every amount and time that appeared in tool results or the visitor's own details. */
    public final Set<Long> allowedAmountsCents = new HashSet<>();
    public final Set<String> allowedTimes = new HashSet<>();
    public final StringBuilder toolText = new StringBuilder();
    public int turns;
    public String proposedOptionId;
    public String submittedReference;

    public Conversation(String id, String sessionKey, Instant now) { this.id = id; this.sessionKey = sessionKey; this.lastActive = now; }
}
