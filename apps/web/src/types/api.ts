export type PricingType = "PER_PERSON" | "PER_TRIP" | "CUSTOM";

export type TourCategory =
  | "PENINSULA"
  | "WINELANDS"
  | "CITY"
  | "CULTURE"
  | "GARDEN_ROUTE"
  | "TRANSFER"
  | "SAFARI"
  | "PRIVATE"
  | "NATURE";

export interface TourSummary {
  slug: string;
  title: string;
  shortDescription: string;
  duration: string;
  basePriceCents: number;
  pricingType: PricingType;
  category: TourCategory;
  featured: boolean;
  heroImage: string;
  cardNote: string | null;
  tags: string[];
  heroImageAlt: string | null;
  /** As published, e.g. "Minimum 5 guests", "Any group size", "Private". */
  groupSizeLabel: string | null;
  minimumGuests: number;
}

/** Which enquiry form a tour shows: standard tours, airport transfers (flight and luggage) or custom tours (dates and interests). */
export type TourEnquiryForm = "STANDARD" | "AIRPORT" | "CUSTOM";

export interface TourDetail extends TourSummary {
  fullDescription: string;
  minimumGuests: number;
  maximumGuests: number;
  locations: string[];
  highlights: string[];
  included: string[];
  excluded: string[];
  itinerary: string[];
  faqs: string[];
  pickupAvailable: boolean;
  bannerImage: string | null;
  seoTitle: string;
  seoDescription: string;
  pickupSummary: string | null;
  availabilityLabel: string | null;
  tourTypeLabel: string | null;
  /** Shown above the itinerary, e.g. that times are a sample. */
  itineraryNote: string | null;
  /** Practical points, including details customers should confirm when enquiring. */
  practicalInfo: string[];
  enquiryForm: TourEnquiryForm;
}

export interface BookingResponse {
  reference: string;
  status: string;
  tourTitle: string;
  travelDate: string;
  guests: number;
  totalAmountCents: number;
  amountPaidCents: number;
  message: string;
}

export interface TripPlanningResponse {
  reference: string;
  status: string;
  message: string;
}

export interface OperationsDashboard {
  bookingsToday: number;
  awaitingPayment: number;
  confirmedBookings: number;
  enquiries: number;
  revenueCents: number;
  activeGuides: number;
  activeVehicles: number;
  newEnquiries: number;
  privateTripRequests: number;
  inspectionsAwaitingReview: number;
  openVehicleRestrictions: number;
  tripsNeedingAttention: number;
}

export interface OperationsBooking {
  reference: string;
  guest: string;
  tour: string;
  travelDate: string;
  guests: number;
  totalAmountCents: number;
  amountPaidCents: number;
  status: string;
  guide: string;
  vehicle: string;
  email: string;
  phone: string | null;
  country: string | null;
  pickupLocation: string | null;
  notes: string | null;
  registeredCustomer: boolean;
  createdAt: string;
  version: number;
  bookingType: "TOUR" | "PRIVATE_TRIP";
  driver: string;
  assignmentStatus: AssignmentStatus | null;
  demoData: boolean;
}

export interface PageResult<T> { content: T[]; number: number; totalPages: number; totalElements: number; }
export interface Session { authenticated: boolean; email: string | null; name: string | null; role: "ADMIN" | "STAFF" | "CUSTOMER" | "DRIVER" | "PARTNER" | null; emailVerified?: boolean; }
export type TourStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
/** Labels, practical information and the admin-only review notes of a tour. */
export interface TourExtras { pickupSummary: string | null; groupSizeLabel: string | null; availabilityLabel: string | null; tourTypeLabel: string | null;
  heroImageAlt: string | null; itineraryNote: string | null; practicalInfo: string[]; reviewNotes: string | null; sourceUrl: string | null; enquiryForm: TourEnquiryForm; }
export interface AdminTour { id: string; version: number; status: TourStatus; displayOrder: number; cardNote: string | null; detail: TourDetail; extras: TourExtras; }
type TourDetailExtraFields = "heroImageAlt" | "groupSizeLabel" | "pickupSummary" | "availabilityLabel" | "tourTypeLabel" | "itineraryNote" | "practicalInfo" | "enquiryForm";
/** extras: null keeps the stored labels, practical information and review notes. */
export interface SaveTour extends Omit<TourDetail, "cardNote" | TourDetailExtraFields> { version: number; status: TourStatus; displayOrder: number;
  cardNote: string | null; extras?: TourExtras | null; }
export type EnquiryStatus = "NEW" | "IN_PROGRESS" | "AWAITING_CUSTOMER" | "CONVERTED" | "CLOSED";
export type EnquirySource = "CONTACT_FORM" | "TRIP_PLANNER" | "PRIVATE_TRIP_QUOTE" | "ASSISTANT";
export type EnquiryType = "GENERAL" | "TOUR" | "PRIVATE_TRIP";
export interface EnquiryReceipt { reference: string; status: EnquiryStatus; message: string; duplicate: boolean; }
export interface EnquirySummary {
  reference: string; source: EnquirySource; type: EnquiryType; status: EnquiryStatus; customerName: string; customerEmail: string;
  customerPhone: string | null; messagePreview: string; tourTitle: string | null; requestedDate: string | null; passengers: number | null; createdAt: string; demoData: boolean;
}
export interface EnquiryDetail {
  reference: string; version: number; source: EnquirySource; type: EnquiryType; status: EnquiryStatus; customerName: string; customerEmail: string;
  customerPhone: string | null; message: string; tourSlug: string | null; tourTitle: string | null; requestedDate: string | null; requestedTime: string | null;
  durationMinutes: number | null; pickupLocation: string | null; destination: string | null; passengers: number | null;
  convertedBookingReference: string | null; allowedStatuses: EnquiryStatus[]; notes: Array<{ author: string; body: string; createdAt: string }>;
  createdAt: string; updatedAt: string;
}

// Fleet, drivers, assignments and inspections
export type VehicleCategory = "SEDAN" | "SUV" | "VAN" | "LUXURY_VAN" | "MINIBUS" | "COACH";
export type OperationalStatus = "ACTIVE" | "OUT_OF_SERVICE" | "RETIRED";
export type ListingStatus = "LISTED" | "HIDDEN";
export type RestrictionType = "SAFETY" | "MAINTENANCE";
export interface VehicleRestriction {
  id: string; version: number; type: RestrictionType; reason: string; sourceInspectionId: string | null; openedBy: string; openedAt: string;
  clearedBy: string | null; clearedAt: string | null; clearanceNote: string | null;
}
export interface VehicleImage { id: string; mediaId: string; url: string; altText: string; sortOrder: number; }
export interface VehicleSummary {
  id: string; registration: string; displayName: string; category: VehicleCategory; capacity: number; luggageCapacity: number | null;
  operationalStatus: OperationalStatus; listingStatus: ListingStatus; openRestrictions: VehicleRestriction[]; imageUrl: string | null; hourlyRateCents: number | null;
  demoData: boolean;
}
export interface VehicleDetail extends Omit<VehicleSummary, "openRestrictions" | "imageUrl"> {
  version: number; publicDescription: string | null; operatingNotes: string | null; notes: string | null; minimumMinutes: number | null;
  amenities: string[]; images: VehicleImage[]; restrictions: VehicleRestriction[]; upcomingAssignments: AdminAssignment[];
  /** Published vehicle option (model) this vehicle is; only then is it offered to customers who chose that model. */
  vehicleOptionSlug: string | null;
}
export type SaveVehicle = Omit<VehicleDetail, "id" | "images" | "restrictions" | "upcomingAssignments" | "demoData">;
export type DriverVerification = "PENDING" | "VERIFIED" | "REJECTED" | "SUSPENDED";
export type DriverDocumentType = "DRIVING_LICENCE" | "PRDP" | "IDENTITY_DOCUMENT" | "OTHER";
export interface DriverSummary {
  id: string; name: string; contact: string | null; email: string | null; accountEnabled: boolean; status: "ACTIVE" | "INACTIVE";
  verificationStatus: DriverVerification; licenceExpiresOn: string | null; prdpExpiresOn: string | null; eligibilityProblems: string[]; documentsAwaitingReview: number; demoData: boolean;
}
export interface DriverDocument {
  id: string; version: number; type: DriverDocumentType; mediaId: string; contentType: string; expiresOn: string | null;
  status: "PENDING_REVIEW" | "ACCEPTED" | "REJECTED"; reviewNotes: string | null; reviewedBy: string | null; reviewedAt: string | null; createdAt: string;
}
export interface DriverDetail {
  id: string; version: number; name: string; contact: string | null; email: string | null; accountEnabled: boolean; status: "ACTIVE" | "INACTIVE";
  notes: string | null; verificationStatus: DriverVerification; verificationNotes: string | null; verifiedBy: string | null; verifiedAt: string | null;
  licenceNumber: string | null; licenceExpiresOn: string | null; prdpExpiresOn: string | null; eligibilityProblems: string[]; documents: DriverDocument[]; demoData: boolean;
  upcomingAssignments: AdminAssignment[];
}
export type AssignmentStatus = "SCHEDULED" | "AWAITING_RELEASE" | "RELEASED" | "RETURNED" | "CLOSED" | "CANCELLED";
export interface AdminAssignment {
  id: string; version: number; status: AssignmentStatus; bookingReference: string; bookingType: string; title: string; startsAt: string; endsAt: string;
  blockedUntil: string; passengers: number; vehicleId: string | null; vehicleName: string | null; registration: string | null; driverId: string | null;
  driverName: string | null; collectionState: string; returnState: string; problems: string[];
}
export interface ResourceOption { id: string; name: string; detail: string | null; available: boolean; reasons: string[]; }
export interface ResourceOptions { vehicles: ResourceOption[]; drivers: ResourceOption[]; turnaroundMinutes: number; }
export type ChecklistItem = string;
export type FuelLevel = "EMPTY" | "QUARTER" | "HALF" | "THREE_QUARTERS" | "FULL";
export interface ChecklistDefinition { item: ChecklistItem; label: string; safetyCritical: boolean; }
export interface ChecklistLine { item: ChecklistItem; label: string; result: "OK" | "DEFECT"; severity: "MINOR" | "SAFETY" | null; note: string | null; }
export interface InspectionView {
  id: string; version: number; type: "COLLECTION" | "RETURN"; assignmentId: string; driverId: string; driverName: string; vehicleId: string; vehicleName: string;
  registration: string; submittedBy: string; submittedAt: string; odometerKm: number; fuelLevel: FuelLevel; notes: string | null; checklist: ChecklistLine[];
  hasDefect: boolean; hasSafetyDefect: boolean; selfieUrl: string | null; photos: Array<{ mediaId: string; url: string; kind: "VEHICLE" | "DEFECT"; checklistItem: string | null }>;
  reviewStatus: "PENDING_REVIEW" | "APPROVED" | "REJECTED"; reviewedBy: string | null; reviewedAt: string | null; reviewNotes: string | null;
}
export interface InspectionComparison {
  assignmentId: string; assignmentStatus: AssignmentStatus; collection: InspectionView | null; returned: InspectionView | null; distanceKm: number | null;
  fuelChangePercent: number | null; newlyReportedDefects: ChecklistLine[]; persistingDefects: ChecklistLine[];
}
export interface PendingReview {
  inspectionId: string; type: "COLLECTION" | "RETURN"; assignmentId: string; bookingReference: string; driverName: string; vehicleName: string;
  submittedAt: string; hasDefect: boolean; hasSafetyDefect: boolean;
}

// Driver portal
export interface DriverProfile { name: string; verificationStatus: DriverVerification; licenceExpiresOn: string | null; prdpExpiresOn: string | null; eligibilityProblemsToday: string[]; }
export interface DriverAssignmentSummary {
  id: string; status: AssignmentStatus; bookingReference: string; title: string; startsAt: string; endsAt: string; pickupLocation: string | null;
  passengers: number; vehicleName: string | null; registration: string | null; nextAction: string;
}
export interface DriverStep { state: string; submittedAt: string | null; odometerKm: number | null; fuelLevel: FuelLevel | null; photoUrls: string[]; }
export interface DriverAssignmentDetail {
  id: string; status: AssignmentStatus;
  trip: { bookingReference: string; type: string; title: string; startsAt: string; endsAt: string; pickupLocation: string | null; dropOff: string | null;
    itinerary: string[]; passengers: number; customerName: string; customerPhone: string | null; specialRequests: string | null };
  vehicle: { displayName: string; registration: string; category: VehicleCategory; capacity: number; luggageCapacity: number | null; amenities: string[];
    operatingNotes: string | null; imageUrl: string | null };
  collection: DriverStep; returned: DriverStep; blockers: string[]; nextAction: string; canCollect: boolean; canReturn: boolean; minimumVehiclePhotos: number;
}
export interface SubmissionResult { inspectionId: string; type: "COLLECTION" | "RETURN"; assignmentStatus: AssignmentStatus; outcome: string; }

// Private trips and pricing
export interface FeeLine { name: string; type: "FIXED" | "PER_BILLABLE_HOUR"; amountCents: number; totalCents: number; }
export interface PriceQuote {
  hourlyRateCents: number | null; rateSource: "VEHICLE" | "CATEGORY" | "MANUAL"; sampleRate: boolean; requestedMinutes: number; minimumMinutes: number;
  billableMinutes: number; incrementMinutes: number; minimumApplied: boolean; roundedUp: boolean; baseCents: number; fees: FeeLine[]; feesCents: number; totalCents: number;
}
export interface VehicleCard {
  id: string; name: string; category: VehicleCategory; capacity: number; luggageCapacity: number | null; amenities: string[]; description: string | null;
  imageUrl: string | null; imageAlt: string; hourlyRateCents: number | null; minimumMinutes: number | null; sampleRate: boolean; quote: PriceQuote | null;
  quoteOnlyReason: string | null;
  /** Published model this fleet vehicle is, if any. Without its own photo the card shows the model's picture. */
  optionSlug: string | null; illustrativeImage: boolean; imageCredit: string | null;
}
export interface PricingDisclosures {
  billingIncrementMinutes: number; includedKmPerHour: number | null; excessKmRateCents: number | null; overtimeHourlyRateCents: number | null;
  fees: Array<{ name: string; type: "FIXED" | "PER_BILLABLE_HOUR"; amountCents: number }>; sampleRatesShown: boolean;
}
export interface PrivateTripSearch { startsAt: string; endsAt: string; durationMinutes: number; passengers: number; vehicles: VehicleCard[]; disclosures: PricingDisclosures; }
export interface PrivateTripReceipt { reference: string; status: string; message: string; quote: PriceQuote; startsAt: string; endsAt: string; vehicleName: string | null; duplicate: boolean; }
export interface AdminPrivateTrip {
  reference: string; bookingVersion: number; status: string; customerName: string; customerEmail: string; customerPhone: string | null; passengers: number;
  startsAt: string; endsAt: string; durationMinutes: number; pickupLocation: string | null; destination: string; specialRequests: string | null;
  requestedVehicleId: string | null; requestedVehicleName: string | null; requestedCategory: VehicleCategory; quote: PriceQuote; assignment: AdminAssignment | null;
}
export interface PricingView {
  settings: { version: number; turnaroundMinutes: number; billingIncrementMinutes: number; includedKmPerHour: number | null; excessKmRateCents: number | null;
    overtimeHourlyRateCents: number | null; updatedBy: string; updatedAt: string };
  rates: Array<{ category: VehicleCategory; version: number; hourlyRateCents: number | null; minimumMinutes: number; rateStatus: "SAMPLE" | "LIVE"; updatedBy: string; updatedAt: string }>;
  fees: Array<{ id: string; version: number; name: string; type: "FIXED" | "PER_BILLABLE_HOUR"; amountCents: number; active: boolean; sortOrder: number }>;
  sampleRatesShownPublicly: boolean;
}
export interface SiteImage { id: string; version: number; slot: "HOME_HERO" | "GALLERY"; url: string; altText: string; title: string | null; subtitle: string | null; body: string | null; sortOrder: number; active: boolean; }

// Explore Cape Town catalogue and day plans
export type ActivityCategory = "NATURE" | "WILDLIFE" | "MUSEUM_HISTORY" | "ART_CULTURE" | "WINE" | "FOOD_MARKET" | "BEACH" | "HIKING" | "SCENIC_DRIVE"
  | "FAMILY_LEISURE" | "GO_KARTING" | "ICE_SKATING" | "CINEMA" | "ADVENTURE";
export type ActivityArea = "CITY_CENTRE" | "WATERFRONT" | "TABLE_MOUNTAIN" | "ATLANTIC_SEABOARD" | "SOUTHERN_SUBURBS" | "CONSTANTIA" | "HOUT_BAY_CHAPMANS"
  | "FALSE_BAY" | "CAPE_POINT" | "NORTHERN_SUBURBS";
export type PriceStatus = "VERIFIED" | "VARIES_AT_BOOKING" | "NOT_PUBLISHED" | "FREE" | "NOT_RECORDED";
export type Residency = "ANY" | "SA_RESIDENT" | "SADC" | "AFRICAN" | "INTERNATIONAL";
export type ScheduleType = "OPENING_HOURS" | "SESSIONS" | "SCHEDULE_VARIES" | "ALWAYS_OPEN" | "CHECK_OPERATOR";
export type ActivityRegion = "CAPE_TOWN" | "CAPE_WINELANDS" | "OVERBERG" | "WEST_COAST" | "GARDEN_ROUTE_KLEIN_KAROO" | "CAPE_KAROO";
export type ActivityTheme = "ART" | "HERITAGE" | "HIKING" | "ADVENTURE" | "OCEAN" | "WILDLIFE" | "FOOD" | "WINE" | "WORKSHOPS" | "FAMILY" | "SCENIC"
  | "CYCLING_RIDING" | "WELLNESS" | "EVENTS" | "ESCAPES";
export type OperatingStatus = "OPEN" | "PARTLY_CLOSED" | "SEASONAL" | "CLOSED" | "UNKNOWN";
/** A picture or video. ILLUSTRATIVE pictures show the kind of activity, not the place itself, and must be labelled. */
export interface ActivityMediaItem {
  kind: "IMAGE" | "VIDEO"; url: string; thumbnailUrl: string | null; width: number | null; height: number | null; altText: string; caption: string | null;
  credit: string; sourcePage: string; licence: string; licenceUrl: string | null; depiction: "EXACT" | "ILLUSTRATIVE";
}
export type PriceAudience = "EVERYONE" | "ADULT" | "CHILD" | "INFANT" | "SENIOR" | "STUDENT" | "FAMILY" | "GROUP";
export type PriceUnit = "PER_PERSON" | "PER_VEHICLE" | "PER_GROUP" | "PER_ITEM";
export type SourceType = "OFFICIAL_OPERATOR" | "TOURISM_AUTHORITY" | "PUBLIC_AUTHORITY" | "OTHER";
/** Things to Do listing card. No prices are shown in Things to Do. area is set only for Cape Town listings that day plans can use. */
export interface ActivityCard {
  slug: string; title: string; category: ActivityCategory; themes: ActivityTheme[]; region: ActivityRegion; regionLabel: string; town: string | null;
  area: ActivityArea | null; areaLabel: string | null; summary: string; durationMinutes: number; kind: "ATTRACTION" | "EVENT"; eventSchedule: string | null;
  image: ActivityMediaItem | null; indoor: boolean; weatherDependent: boolean; operatingStatus: OperatingStatus; lastVerifiedOn: string | null;
}
export interface PublicPrice {
  label: string; audience: PriceAudience; minAge: number | null; maxAge: number | null; residency: Residency; unit: PriceUnit; amountCents: number;
  daysOfWeek: string | null; timeFrom: string | null; timeTo: string | null; conditions: string | null; effectiveFrom: string | null;
  effectiveTo: string | null; verifiedOn: string; sourceUrl: string | null; sourcePublisher: string | null; currentlyEffective: boolean;
  /** Unresolved doubt or overdue re-check, shown with the figure. */
  caution: string | null;
}
export interface ActivityHoursRow { seasonLabel: string | null; seasonStart: string | null; seasonEnd: string | null; daysOfWeek: string | null; opens: string; closes: string; lastEntry: string | null; notes: string | null;
  verifiedOn: string; validFrom: string | null; validTo: string | null; caution: string | null; }
export type ParticipationRules = "KNOWN" | "PARTLY_KNOWN" | "UNKNOWN";
export interface ActivityPage {
  slug: string; title: string; category: ActivityCategory; themes: ActivityTheme[]; region: ActivityRegion; regionLabel: string; town: string | null;
  area: ActivityArea | null; areaLabel: string | null; latitude: number | null; longitude: number | null; summary: string; description: string;
  whatToExpect: string | null; practicalInfo: string | null; seasonalInfo: string | null;
  /** null: the operator does not publish accessibility information. */
  accessibilityInfo: string | null; address: string | null; officialUrl: string | null; durationMinutes: number; scheduleType: ScheduleType;
  weatherDependent: boolean; indoor: boolean; minAge: number | null; accompanimentRule: string | null; bookingNotes: string | null;
  kind: "ATTRACTION" | "EVENT"; eventSchedule: string | null; operatingStatus: OperatingStatus; operatingNote: string | null;
  operatingCheckedOn: string | null; existenceVerifiedOn: string | null; lastVerifiedOn: string | null; media: ActivityMediaItem[];
  seoTitle: string; seoDescription: string; hours: ActivityHoursRow[]; faqs: Array<{ question: string; answer: string }>;
  sources: Array<{ url: string; publisher: string; sourceType: SourceType; covers: string; accessedOn: string }>; updatedAt: string;
  participationRules: ParticipationRules; participationNote: string | null;
  /** Set when an SL Tours supplier partner provides this experience; shown as a commercial-relationship disclosure. */
  supplierName: string | null;
  /** Cape Town listings in a planning area can be added to a day plan. */
  plannable: boolean;
}
export type CostKind = "VERIFIED" | "ASSUMPTION" | "ESTIMATE" | "UNKNOWN" | "EXCLUDED";
export interface ItineraryCostLine { label: string; quantity: number; unitAmountCents: number | null; amountCents: number | null; kind: CostKind; detail: string | null; sourceUrl: string | null; verifiedOn: string | null; }
export interface ItineraryStop { order: number; type: "ACTIVITY" | "LUNCH" | "RETURN"; slug: string | null; title: string; area: ActivityArea; areaLabel: string; start: string; end: string; travelMinutes: number; notes: string[]; costs: ItineraryCostLine[]; cautions: string[]; }
export interface ItineraryConfidence { pricesConfirmed: boolean; scheduleConfirmed: boolean; suitabilityConfirmed: boolean; reasons: string[]; }
export interface Itinerary {
  date: string; travellers: string; stops: ItineraryStop[]; transport: ItineraryCostLine[]; food: ItineraryCostLine[];
  totals: { verifiedCents: number; assumptionCents: number; estimatedCents: number; knownTotalCents: number; unknownItems: number };
  budgetCents: number | null; verdict: "WITHIN_BUDGET" | "ESTIMATED_WITHIN_BUDGET" | "OVER_BUDGET" | "CANNOT_CONFIRM" | "NO_BUDGET";
  verdictMessage: string; assumptions: string[]; exclusions: string[]; warnings: string[]; skipped: string[]; confidence: ItineraryConfidence;
}
export type AssistantMode = "LIVE" | "MOCK" | "DISABLED";
export interface AssistantStatus { available: boolean; mode: AssistantMode; message: string | null; }
export interface AssistantOption { id: string; label: string; itinerary: Itinerary; }
export interface AssistantReply {
  conversationId: string; status: "OK" | "UNAVAILABLE" | "LIMITED"; mode: AssistantMode; text: string; options: AssistantOption[];
  details: Record<string, unknown>; missing: string[]; enquiryOptionId: string | null; enquiryReference: string | null; plannerLink: string;
  /** The model's wording failed the figure checks and was replaced with a description built from the planner. */
  checkedFallback: boolean;
}

/** A vehicle customers can request for a private trip. Availability is confirmed per trip. */
export interface VehicleOption {
  slug: string; name: string; typeLabel: string; category: VehicleCategory; passengerCapacity: number;
  /** false while the requested capacity still needs confirming against the vehicle's seating. */
  capacityConfirmed: boolean; capacityNote: string; description: string; imageUrl: string; imageThumbnailUrl: string | null; imageAlt: string;
  /** The picture shows the model or category, not an SL Tours vehicle. */
  illustrativeImage: boolean; imageCredit: string; imageSourcePage: string; imageLicence: string; imageLicenceUrl: string | null; checkedOn: string;
}
