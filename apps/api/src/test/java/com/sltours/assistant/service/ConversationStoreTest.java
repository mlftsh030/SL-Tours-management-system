package com.sltours.assistant.service;
import com.sltours.shared.error.ResourceNotFoundException;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConversationStoreTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test void conversationsAreBoundToTheSessionAndExpireWhenIdle() {
        MutableClock clock = new MutableClock();
        ConversationStore store = new ConversationStore(new TestAssistantProperties().build(), clock);
        var c = store.create("session-a");
        assertThat(store.get(c.id, "session-a")).isSameAs(c);
        assertThatThrownBy(() -> store.get(c.id, "session-b")).isInstanceOf(ResourceNotFoundException.class);
        store.delete(c.id, "session-b");
        assertThat(store.size()).isEqualTo(1);
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        assertThatThrownBy(() -> store.get(c.id, "session-a")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(store.size()).isZero();
    }

    @Test void hourlyMessageLimitsApplyPerSessionAndPerAddress() {
        MutableClock clock = new MutableClock();
        var props = new TestAssistantProperties();
        props.perSession = 2; props.perAddress = 3;
        ConversationStore store = new ConversationStore(props.build(), clock);
        store.checkRate("s1", "10.0.0.1");
        store.checkRate("s1", "10.0.0.1");
        assertThatThrownBy(() -> store.checkRate("s1", "10.0.0.1")).isInstanceOf(ConversationStore.LimitException.class);
        store.checkRate("s2", "10.0.0.1");
        assertThatThrownBy(() -> store.checkRate("s3", "10.0.0.1")).isInstanceOf(ConversationStore.LimitException.class);
        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertThatCode(() -> store.checkRate("s1", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test void activeConversationCountIsCapped() {
        var props = new TestAssistantProperties();
        props.maxConversations = 1;
        ConversationStore store = new ConversationStore(props.build(), new MutableClock());
        store.create("a");
        assertThatThrownBy(() -> store.create("b")).isInstanceOf(ConversationStore.LimitException.class);
    }
}
