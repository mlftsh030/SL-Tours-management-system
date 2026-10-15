package com.sltours.activities.dto;
import com.sltours.activities.model.ActivityEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ActivityDtos {
    private ActivityDtos() {}
    public record Source(UUID id, @NotBlank @Size(max = 500) @Pattern(regexp = "https?://.+", message = "Use a full http(s) link") String url,
        @NotBlank @Size(max = 160) String publisher, @NotNull SourceType sourceType, @NotBlank @Size(max = 300) String covers, @NotNull LocalDate accessedOn) {}
    public record PriceRule(@NotBlank @Size(max = 160) String label, @NotNull Audience audience, @Min(0) @Max(120) Integer minAge,
        @Min(0) @Max(120) Integer maxAge, @NotNull Residency residency, @NotNull Unit unit, @Min(0) @Max(100_000_000) int amountCents,
        @Size(max = 40) String daysOfWeek, LocalTime timeFrom, LocalTime timeTo, @Size(max = 400) String conditions, boolean estimateDefault,
        LocalDate effectiveFrom, LocalDate effectiveTo, UUID sourceId, @NotNull LocalDate verifiedOn, @Size(max = 400) String uncertaintyNote,
        boolean promotional) {}
    public record Extra(@NotBlank @Size(max = 160) String label, @Min(0) @Max(100_000_000) int amountCents, @NotNull Unit unit, boolean mandatory,
        @Size(max = 400) String conditions, LocalDate effectiveFrom, LocalDate effectiveTo, UUID sourceId, @NotNull LocalDate verifiedOn,
        @Size(max = 400) String uncertaintyNote) {}
    public record Hours(@Size(max = 80) String seasonLabel, @Pattern(regexp = "[0-1][0-9]-[0-3][0-9]") String seasonStart,
        @Pattern(regexp = "[0-1][0-9]-[0-3][0-9]") String seasonEnd, @Size(max = 40) String daysOfWeek, @NotNull LocalTime opens,
        @NotNull LocalTime closes, LocalTime lastEntry, @Size(max = 300) String notes, UUID sourceId, @NotNull LocalDate verifiedOn,
        @Size(max = 400) String uncertaintyNote, LocalDate validFrom, LocalDate validTo) {}
    public record Faq(@NotBlank @Size(max = 300) String question, @NotBlank @Size(max = 2000) String answer) {}
    public record Media(@NotNull MediaKind kind, @NotBlank @Size(max = 500) String url, @Size(max = 500) String thumbnailUrl,
        @Min(1) @Max(10000) Integer width, @Min(1) @Max(10000) Integer height, @NotBlank @Size(max = 300) String altText,
        @Size(max = 300) String caption, @NotBlank @Size(max = 300) String credit,
        @NotBlank @Size(max = 500) @Pattern(regexp = "https?://.+", message = "Use a full http(s) link") String sourcePage,
        @NotBlank @Size(max = 120) String licence, @Size(max = 500) @Pattern(regexp = "(https?://.+)?") String licenceUrl,
        @NotNull MediaPermission permission, @NotNull Depiction depiction, @NotNull LocalDate checkedOn) {}
    /** Region, place and the separately tracked facts of a listing. Null on a save keeps the stored values. */
    public record Regional(@NotNull Region region, @Size(max = 120) String town, @DecimalMin("-35.0") @DecimalMax("-30.3") BigDecimal latitude,
        @DecimalMin("17.5") @DecimalMax("24.4") BigDecimal longitude, @NotNull Kind kind, @Size(max = 400) String eventSchedule,
        @Size(max = 2000) String whatToExpect, @Size(max = 2000) String practicalInfo, @Size(max = 1000) String seasonalInfo,
        @Size(max = 1000) String accessibilityInfo, @NotNull OperatingStatus operatingStatus, @Size(max = 400) String operatingNote,
        LocalDate operatingCheckedOn, LocalDate existenceVerifiedOn, @NotNull @Size(max = 15) List<@NotNull Theme> themes,
        @NotNull @Size(max = 12) List<@Valid @NotNull Media> media) {}

    public record SaveActivityRequest(@NotNull @Min(0) Long version,
        @NotBlank @Size(max = 160) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug, @NotBlank @Size(max = 180) String title,
        @NotNull Category category, @NotBlank @Size(max = 400) String summary, @NotBlank @Size(max = 4000) String description, Area area,
        @Size(max = 300) String address, @Size(max = 500) @Pattern(regexp = "(https?://.+)?") String officialUrl,
        @Min(15) @Max(720) int durationMinutes, @NotNull ScheduleType scheduleType, boolean weatherDependent, boolean indoor,
        @Min(0) @Max(99) Integer minAge, @Size(max = 300) String accompanimentRule, @Size(max = 600) String bookingNotes,
        @NotNull PriceStatus priceStatus, @Size(max = 2000) String verificationNotes, @Size(max = 220) String heroImage,
        @Size(max = 200) String heroImageAlt, @Size(max = 180) String seoTitle, @Size(max = 300) String seoDescription,
        @NotNull @Size(max = 20) List<@Valid @NotNull Source> sources, @NotNull @Size(max = 80) List<@Valid @NotNull PriceRule> priceRules,
        @NotNull @Size(max = 20) List<@Valid @NotNull Extra> extras, @NotNull @Size(max = 40) List<@Valid @NotNull Hours> hours,
        @NotNull @Size(max = 20) List<@Valid @NotNull Faq> faqs, @NotNull ParticipationRules participationRules,
        @Size(max = 400) String participationNote, @Valid Regional regional) {}

    /** Admin view: everything, including internal verification notes. */
    public record AdminActivity(UUID id, long version, Status status, String slug, String title, Category category, String summary,
        String description, Area area, String address, String officialUrl, int durationMinutes, ScheduleType scheduleType, boolean weatherDependent,
        boolean indoor, Integer minAge, String accompanimentRule, String bookingNotes, PriceStatus priceStatus, String verificationNotes,
        LocalDate lastVerifiedOn, String verifiedBy, boolean verificationStale, String heroImage, String heroImageAlt, String seoTitle,
        String seoDescription, OffsetDateTime publishedAt, OffsetDateTime updatedAt, List<Source> sources, List<PriceRule> priceRules,
        List<Extra> extras, List<Hours> hours, List<Faq> faqs, List<String> publishProblems, ParticipationRules participationRules,
        String participationNote, List<String> recheckIssues, Regional regional) {}
    public record AdminActivitySummary(UUID id, Status status, String slug, String title, Category category, Region region, Area area, PriceStatus priceStatus,
        LocalDate lastVerifiedOn, boolean verificationStale, int priceRules, int sources, List<String> publishProblems, List<String> recheckIssues) {}

    // Public views: no internal notes.
    public record PublicSource(String url, String publisher, SourceType sourceType, String covers, LocalDate accessedOn) {}
    public record PublicPrice(String label, Audience audience, Integer minAge, Integer maxAge, Residency residency, Unit unit, int amountCents,
        String daysOfWeek, LocalTime timeFrom, LocalTime timeTo, String conditions, LocalDate effectiveFrom, LocalDate effectiveTo,
        LocalDate verifiedOn, String sourceUrl, String sourcePublisher, boolean currentlyEffective, String caution) {}
    public record PublicExtra(String label, int amountCents, Unit unit, boolean mandatory, String conditions, LocalDate verifiedOn, String sourceUrl,
        String caution) {}
    /** caution: unresolved doubts or an overdue re-check, shown next to the figure. */
    public record PublicHours(String seasonLabel, String seasonStart, String seasonEnd, String daysOfWeek, LocalTime opens, LocalTime closes,
        LocalTime lastEntry, String notes, LocalDate verifiedOn, LocalDate validFrom, LocalDate validTo, String caution) {}
    public record PublicMedia(MediaKind kind, String url, String thumbnailUrl, Integer width, Integer height, String altText, String caption,
        String credit, String sourcePage, String licence, String licenceUrl, Depiction depiction) {}
    /** Listing card. Things to Do shows no prices. */
    public record ActivityCard(String slug, String title, Category category, List<Theme> themes, Region region, String regionLabel, String town,
        Area area, String areaLabel, String summary, int durationMinutes, Kind kind, String eventSchedule, PublicMedia image, boolean indoor,
        boolean weatherDependent, OperatingStatus operatingStatus, LocalDate lastVerifiedOn) {}
    /** Detail page. Prices are deliberately absent; the day planner reads them separately. plannable: can appear in a day plan. */
    public record ActivityPage(String slug, String title, Category category, List<Theme> themes, Region region, String regionLabel, String town,
        Area area, String areaLabel, BigDecimal latitude, BigDecimal longitude, String summary, String description, String whatToExpect,
        String practicalInfo, String seasonalInfo, String accessibilityInfo, String address, String officialUrl, int durationMinutes,
        ScheduleType scheduleType, boolean weatherDependent, boolean indoor, Integer minAge, String accompanimentRule, String bookingNotes, Kind kind,
        String eventSchedule, OperatingStatus operatingStatus, String operatingNote, LocalDate operatingCheckedOn, LocalDate existenceVerifiedOn,
        LocalDate lastVerifiedOn, List<PublicMedia> media, String seoTitle, String seoDescription, List<PublicHours> hours, List<Faq> faqs,
        List<PublicSource> sources, OffsetDateTime updatedAt, ParticipationRules participationRules, String participationNote,
        /** Shown as a commercial-relationship disclosure when the experience is supplied by an SL Tours partner. */
        String supplierName, boolean plannable) {}
    /** Prices for planning tools (day planner and planning assistant). Not part of the Things to Do pages. */
    public record ActivityPricing(String slug, PriceStatus priceStatus, Integer fromPriceCents, String fromPriceLabel, List<PublicPrice> prices,
        List<PublicExtra> extras) {}
}
