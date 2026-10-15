package com.sltours.assistant.service;
import com.sltours.assistant.model.Conversation;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReplyGuardTest {
    private Conversation conversation() {
        Conversation c = new Conversation("id", "session", Instant.now());
        Facts.absorb(c, "{\"knownTotal\":\"R1 250.00\",\"time\":\"09:30-11:30\",\"detail\":\"Free entry every Wednesday for African citizens\"}");
        return c;
    }

    @Test void figuresFromToolsMayBeRepeatedInAnyCommonFormat() {
        Conversation c = conversation();
        assertThat(ReplyGuard.problems("Known costs come to R1 250.00, from 09:30 to 11:30.", c)).isEmpty();
        assertThat(ReplyGuard.problems("That is R1,250.00 in total, starting 9:30.", c)).isEmpty();
        assertThat(ReplyGuard.problems("About R1250 overall.", c)).isEmpty();
    }

    @Test void inventedAmountsTimesDiscountsAndCommitmentsAreRejected() {
        Conversation c = conversation();
        assertThat(ReplyGuard.problems("It costs R1 200.00.", c)).singleElement().asString().contains("amount not from tools");
        assertThat(ReplyGuard.problems("The last ferry is at 16:00.", c)).singleElement().asString().contains("time not from tools");
        assertThat(ReplyGuard.problems("Use promo code CAPE for 10% off.", c)).isNotEmpty();
        assertThat(ReplyGuard.problems("Tickets are still available and I've booked them for you.", c)).contains("booking or availability claim");
        assertThat(ReplyGuard.problems("Your plan has been sent to SL Tours.", c)).contains("claims the plan was sent");
        c.submittedReference = "SLE12345";
        assertThat(ReplyGuard.problems("Your plan has been sent to SL Tours.", c)).isEmpty();
    }

    @Test void catalogueWordingIsNotTreatedAsAnInventedDiscount() {
        assertThat(ReplyGuard.problems("There is free entry every Wednesday for African citizens.", conversation())).isEmpty();
        Conversation c = conversation();
        Facts.absorb(c, "{\"label\":\"Spring promotion\"}");
        assertThat(ReplyGuard.problems("A spring promotion price is listed.", c)).isEmpty();
    }
}
