package com.sltours.assistant.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.sltours.activities.dto.ItineraryDtos.Itinerary;
import jakarta.validation.constraints.*;
import java.util.*;

public final class AssistantDtos {
    private AssistantDtos() {}
    /** LIVE: a configured model. MOCK: the development mock (dev/test only). DISABLED: no chat; the planner is the fallback. */
    public enum Mode { LIVE, MOCK, DISABLED }
    public enum ReplyStatus { OK, UNAVAILABLE, LIMITED }

    public record Status(boolean available, Mode mode, String message) {}
    public record SendMessage(@NotBlank @Size(max = 2000) String message) {}
    public record OptionView(String id, String label, Itinerary itinerary) {}
    /**
     * text: the assistant's words. options: the planner's own results, rendered by the page with every cost label, source
     * and check date, so figures never depend on the model's wording. checkedFallback: the model's reply failed the
     * figure checks and was replaced with a description built from the planner results.
     */
    public record Reply(String conversationId, ReplyStatus status, Mode mode, String text, List<OptionView> options, JsonNode details,
        List<String> missing, String enquiryOptionId, String enquiryReference, String plannerLink, boolean checkedFallback) {}
    public record SubmitPlan(@NotBlank @Size(max = 4) String optionId, @NotBlank @Size(max = 160) String name,
        @NotBlank @Email @Size(max = 180) String email, @Size(max = 80) String phone, @Size(max = 800) String note,
        @AssertTrue(message = "Confirm that you want to send this plan to SL Tours") boolean confirmed, @NotNull UUID idempotencyKey,
        @jakarta.validation.Valid com.sltours.partners.dto.PartnerDtos.ReferralInput referral) {}
}
