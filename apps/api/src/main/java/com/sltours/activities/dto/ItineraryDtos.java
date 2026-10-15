package com.sltours.activities.dto;
import com.sltours.activities.model.ActivityEnums.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ItineraryDtos {
    private ItineraryDtos() {}
    public enum Transport { OWN_VEHICLE, SL_TOURS_PRIVATE_VEHICLE }
    public enum CostKind { VERIFIED, ASSUMPTION, ESTIMATE, UNKNOWN, EXCLUDED }
    public enum StopType { ACTIVITY, LUNCH, RETURN }
    public enum Verdict { WITHIN_BUDGET, ESTIMATED_WITHIN_BUDGET, OVER_BUDGET, CANNOT_CONFIRM, NO_BUDGET }

    public record ItineraryRequest(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
        @Min(1) @Max(20) int adults, @NotNull @Size(max = 15) List<@NotNull @Min(0) @Max(17) Integer> childAges,
        @NotNull Residency residency, @NotNull @Size(max = 14) List<@NotNull Category> interests, @NotNull Area startArea,
        @NotNull Transport transport, @Min(0) @Max(10_000_000) Integer foodPerPersonCents, @Min(0) @Max(100_000_000) Integer budgetCents,
        @Min(1) @Max(5) int maxStops, boolean onlyPublishedPrices, @NotNull @Size(max = 5) List<@NotBlank @Size(max = 160) String> mustInclude,
        @Size(max = 25) List<@NotBlank @Size(max = 160) String> exclude) {
        public ItineraryRequest {
            if (exclude == null) exclude = List.of();
        }
    }

    /** amountCents is null when the cost is unknown or excluded. */
    public record CostLine(String label, int quantity, Integer unitAmountCents, Integer amountCents, CostKind kind, String detail,
        String sourceUrl, LocalDate verifiedOn) {}
    /** cautions: what is not confirmed for this stop (stale or conflicting hours, unpublished participation limits). */
    public record Stop(int order, StopType type, String slug, String title, Area area, String areaLabel, LocalTime start, LocalTime end,
        int travelMinutes, List<String> notes, List<CostLine> costs, List<String> cautions) {}
    /** What the plan can and cannot claim. A false flag means the matching claim must not be made confidently. */
    public record Confidence(boolean pricesConfirmed, boolean scheduleConfirmed, boolean suitabilityConfirmed, List<String> reasons) {}
    public record Totals(int verifiedCents, int assumptionCents, int estimatedCents, int knownTotalCents, int unknownItems) {}
    public record Itinerary(LocalDate date, String travellers, List<Stop> stops, List<CostLine> transport, List<CostLine> food, Totals totals,
        Integer budgetCents, Verdict verdict, String verdictMessage, List<String> assumptions, List<String> exclusions, List<String> warnings,
        List<String> skipped, Confidence confidence) {}
}
