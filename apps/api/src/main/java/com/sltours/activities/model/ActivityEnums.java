package com.sltours.activities.model;

public final class ActivityEnums {
    private ActivityEnums() {}
    public enum Category { NATURE, WILDLIFE, MUSEUM_HISTORY, ART_CULTURE, WINE, FOOD_MARKET, BEACH, HIKING, SCENIC_DRIVE, FAMILY_LEISURE,
        GO_KARTING, ICE_SKATING, CINEMA, ADVENTURE }
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public enum Area { CITY_CENTRE, WATERFRONT, TABLE_MOUNTAIN, ATLANTIC_SEABOARD, SOUTHERN_SUBURBS, CONSTANTIA, HOUT_BAY_CHAPMANS, FALSE_BAY,
        CAPE_POINT, NORTHERN_SUBURBS }
    /** CHECK_OPERATOR: hours were not published on the pages checked, so visitors confirm them with the operator. */
    public enum ScheduleType { OPENING_HOURS, SESSIONS, SCHEDULE_VARIES, ALWAYS_OPEN, CHECK_OPERATOR }
    /** NOT_RECORDED: SL Tours has not recorded prices for this listing. It never means free. */
    public enum PriceStatus { VERIFIED, VARIES_AT_BOOKING, NOT_PUBLISHED, FREE, NOT_RECORDED }
    public enum SourceType { OFFICIAL_OPERATOR, TOURISM_AUTHORITY, PUBLIC_AUTHORITY, OTHER }
    public enum Audience { EVERYONE, ADULT, CHILD, INFANT, SENIOR, STUDENT, FAMILY, GROUP }
    public enum Residency { ANY, SA_RESIDENT, SADC, AFRICAN, INTERNATIONAL }
    public enum Unit { PER_PERSON, PER_VEHICLE, PER_GROUP, PER_ITEM }
    /** Whether the operator publishes participation limits (age, height, health): unknown limits block suitability claims. */
    public enum ParticipationRules { KNOWN, PARTLY_KNOWN, UNKNOWN }
    /** Western Cape tourism regions. Only Cape Town listings with a planning area can appear in day plans. */
    public enum Region { CAPE_TOWN, CAPE_WINELANDS, OVERBERG, WEST_COAST, GARDEN_ROUTE_KLEIN_KAROO, CAPE_KAROO }
    /** Activity categories shown as filters; independent of the region and of the legacy single category. */
    public enum Theme { ART, HERITAGE, HIKING, ADVENTURE, OCEAN, WILDLIFE, FOOD, WINE, WORKSHOPS, FAMILY, SCENIC, CYCLING_RIDING, WELLNESS, EVENTS, ESCAPES }
    /** EVENT: dated or recurring events, listed separately from places you can visit any day. */
    public enum Kind { ATTRACTION, EVENT }
    public enum OperatingStatus { OPEN, PARTLY_CLOSED, SEASONAL, CLOSED, UNKNOWN }
    public enum MediaKind { IMAGE, VIDEO }
    /** PENDING media are never shown publicly. */
    public enum MediaPermission { SL_TOURS_OWN, OPEN_LICENCE, PERMISSION_GRANTED, PENDING }
    /** ILLUSTRATIVE pictures show the kind of activity, not this place, and are labelled as such. */
    public enum Depiction { EXACT, ILLUSTRATIVE }
}
