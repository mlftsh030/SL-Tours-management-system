package com.sltours.seo;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.repository.ActivityRepository;
import com.sltours.activities.service.ActivityViews;
import com.sltours.tours.model.*;
import com.sltours.tours.repository.TourRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-rendered head tags, core page content and the sitemap, generated from the database on every request so that
 * admin publications and edits appear immediately. Nginx includes the head and body into index.html; React then takes
 * over. Structured data only describes what the page visibly shows.
 *
 * <p>Search indexing is a deliberate per-environment choice ({@code sltours.seo.indexing}): staging and previews keep
 * every page out of search results and do not advertise the sitemap, while canonical links always name the production
 * host. Production must set the property explicitly (see ProductionSafetyCheck).
 */
@Service
public class SeoService {
    record Page(String title, String description, String canonicalPath, boolean index, String image, List<String> jsonLd, String body) {}
    private static final Map<String, String[]> STATIC = new LinkedHashMap<>();
    static {
        STATIC.put("/", new String[]{"SL Tours Cape Town | Guided Day Trips, Safaris & Transfers",
            "Book Cape Town tours and transfers with local guides. Explore Cape Point, Winelands, Garden Route, safaris, and private tour options with SL Tours.", "SL Tours Cape Town"});
        STATIC.put("/experiences", new String[]{"Cape Town Tours & Prices | SL Tours",
            "Guided Cape Town tours with published prices: Cape Point, the city, a township visit, the Winelands, the Garden Route, an Aquila safari, the West Coast, airport transfers and private tours.", "Our Tours"});
        STATIC.put("/private-tours", new String[]{"Private Tours | SL Tours Cape Town", "Tailor-made days at your own pace, with no waiting for other guests.", "Private Tours"});
        STATIC.put("/transfers", new String[]{"Transfers | SL Tours Cape Town", "Reliable airport and private transfers across Cape Town.", "Transfers"});
        STATIC.put("/book-private-trip", new String[]{"Book a Private Trip | SL Tours Cape Town",
            "Hire a vehicle with a professional SL Tours driver by the hour. See available vehicles and the full price before you request.", "Book a Private Trip"});
        STATIC.put("/plan-my-trip", new String[]{"Plan My Trip | SL Tours", "Tell us your dates, who is travelling, and what you love. We will shape a tailored plan and quote.", "Plan My Trip"});
        STATIC.put("/gallery", new String[]{"Tour Gallery | Real Moments From SL Tours Cape Town",
            "View real tour photos from SL Tours in Cape Town and surrounding destinations across South Africa.", "Gallery"});
        STATIC.put("/about", new String[]{"About SL Tours | Trusted Cape Town Tour Company",
            "SL Tours is a proudly Black-owned, Cape Town-based travel company run by professional tourist guide Lisolethu Nkcaza.", "About SL Tours"});
        STATIC.put("/contact", new String[]{"Contact SL Tours | Book Tours & Transfers in Cape Town",
            "Call, email, or WhatsApp SL Tours to book Cape Town tours, private trips, and airport transfers.", "Contact SL Tours"});
        STATIC.put("/partner-with-us", new String[]{"Partner with SL Tours | Referral Partners and Experience Suppliers",
            "Hotels, guesthouses, concierges and travel agents can refer guests to SL Tours. Safari operators, activity providers and guides can propose experiences.",
            "Partner with SL Tours"});
        STATIC.put("/privacy", new String[]{"Privacy Notice | SL Tours",
            "How SL Tours uses the personal information you share through this website, and your choices.", "Privacy Notice"});
        STATIC.put("/explore", new String[]{"Things to Do in Cape Town and the Western Cape | SL Tours",
            "Things to do in Cape Town, the Winelands, Overberg, West Coast, Garden Route and Karoo, with opening hours and details checked against official sources.",
            "Things to Do in Cape Town"});
        STATIC.put("/explore/plan", new String[]{"Plan a Day in Cape Town | SL Tours",
            "Build a practical day plan with opening times, travel between areas and transparent admission, transport and food costs.", "Plan a day in Cape Town"});
    }
    private static final List<String> PRIVATE_PREFIXES = List.of("/operations", "/driver", "/partner", "/login", "/register", "/forgot-password", "/reset-password", "/verify-email", "/account", "/my-trip", "/book/");

    private final ActivityRepository activities;
    private final TourRepository tours;
    private final String baseUrl;
    private final com.sltours.activities.service.ActivityViews views;
    private final boolean indexing;
    private final com.sltours.siteimages.SiteImageService siteImages;
    private static final Page NOT_FOUND = new Page("Page not found | SL Tours", "This page could not be found.", null, false, null, List.of(),
        "<main><h1>Page not found</h1><p>The page you asked for does not exist or has moved.</p><p><a href=\"/\">Home</a> · "
            + "<a href=\"/experiences\">Our Tours</a> · <a href=\"/explore\">Things to Do</a> · <a href=\"/contact\">Contact Us</a></p></main>");
    @org.springframework.beans.factory.annotation.Autowired
    public SeoService(ActivityRepository activities, TourRepository tours, @Value("${sltours.public-base-url:https://sl-tours.com}") String baseUrl,
            com.sltours.activities.service.ActivityViews views, @Value("${sltours.seo.indexing:}") String indexing,
            com.sltours.siteimages.SiteImageService siteImages) {
        this(activities, tours, baseUrl, views, "true".equalsIgnoreCase(indexing.trim()), siteImages);
    }
    SeoService(ActivityRepository activities, TourRepository tours, String baseUrl, com.sltours.activities.service.ActivityViews views, boolean indexing,
            com.sltours.siteimages.SiteImageService siteImages) {
        this.activities = activities; this.tours = tours; this.baseUrl = baseUrl.replaceAll("/+$", ""); this.views = views; this.indexing = indexing;
        this.siteImages = siteImages;
    }

    public boolean indexing() { return indexing; }

    /** First active HOME_HERO image, or the built-in first slide (apps/web HeroSlider). */
    private String homeHeroImage() {
        return siteImages.published(com.sltours.siteimages.SiteImage.Slot.HOME_HERO).stream().findFirst()
            .map(com.sltours.siteimages.SiteImageService.SiteImageView::url).orElse("/assets/home/slider-1.webp");
    }

    /** Whether the path is a page this site serves; unknown paths get HTTP 404 from Nginx. */
    @Transactional(readOnly = true)
    public boolean exists(String rawPath) { return page(rawPath) != NOT_FOUND; }

    /** Staging allows crawling so that crawlers can see each page's noindex, but advertises no sitemap. */
    public String robots() {
        return indexing ? "User-agent: *\nAllow: /\n\nSitemap: " + baseUrl + "/sitemap.xml\n" : "User-agent: *\nAllow: /\n";
    }

    @Transactional(readOnly = true)
    public String head(String rawPath) {
        Page p = page(rawPath);
        StringBuilder h = new StringBuilder();
        h.append("<title>").append(esc(p.title())).append("</title>\n");
        h.append("<meta name=\"description\" content=\"").append(esc(p.description())).append("\" />\n");
        if (!p.index() || !indexing) h.append("<meta name=\"robots\" content=\"noindex\" />\n");
        if (p.canonicalPath() != null) {
            String url = baseUrl + p.canonicalPath();
            h.append("<link rel=\"canonical\" href=\"").append(esc(url)).append("\" />\n");
            h.append("<meta property=\"og:url\" content=\"").append(esc(url)).append("\" />\n");
        }
        h.append("<meta property=\"og:type\" content=\"website\" />\n");
        h.append("<meta property=\"og:title\" content=\"").append(esc(p.title())).append("\" />\n");
        h.append("<meta property=\"og:description\" content=\"").append(esc(p.description())).append("\" />\n");
        h.append("<meta property=\"og:image\" content=\"").append(esc(baseUrl + (p.image() == null ? "/assets/home/slider-1.jpg" : p.image()))).append("\" />\n");
        h.append("<meta name=\"twitter:card\" content=\"summary_large_image\" />\n");
        // Only the homepage shows the hero carousel: preload its first slide there and nowhere else.
        if ("/".equals(p.canonicalPath())) h.append("<link rel=\"preload\" as=\"image\" href=\"").append(esc(homeHeroImage())).append("\" />\n");
        for (String json : p.jsonLd()) h.append("<script type=\"application/ld+json\">").append(json.replace("</", "<\\/")).append("</script>\n");
        return h.toString();
    }

    @Transactional(readOnly = true)
    public String body(String rawPath) { return page(rawPath).body(); }

    @Transactional(readOnly = true)
    public Optional<String> sitemap() {
        if (!indexing) return Optional.empty();
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        STATIC.keySet().forEach(path -> x.append("  <url><loc>").append(esc(baseUrl + path)).append("</loc></url>\n"));
        for (Tour t : tours.findByStatusOrderByFeaturedDescDisplayOrderAscTitleAsc(TourStatus.ACTIVE))
            x.append("  <url><loc>").append(esc(baseUrl + "/experiences/" + t.getSlug())).append("</loc>").append(lastmod(t.getUpdatedAt())).append("</url>\n");
        for (Activity a : activities.findByStatusOrderByTitleAsc(Status.PUBLISHED))
            x.append("  <url><loc>").append(esc(baseUrl + "/explore/" + a.getSlug())).append("</loc>").append(lastmod(a.getUpdatedAt())).append("</url>\n");
        return Optional.of(x.append("</urlset>\n").toString());
    }

    Page page(String rawPath) {
        String path = normalize(rawPath);
        if (PRIVATE_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix.replaceAll("/$", "")) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")))
            return new Page("SL Tours", "SL Tours account and administration.", null, false, null, List.of(), "");
        if (STATIC.containsKey(path)) return staticPage(path);
        if (path.startsWith("/explore/")) {
            Optional<Activity> a = activities.findBySlugAndStatus(path.substring("/explore/".length()), Status.PUBLISHED);
            if (a.isPresent()) return activityPage(a.get());
        }
        if (path.startsWith("/experiences/")) {
            Optional<Tour> t = tours.findBySlugAndStatus(path.substring("/experiences/".length()), TourStatus.ACTIVE);
            if (t.isPresent()) return tourPage(t.get());
        }
        return NOT_FOUND;
    }

    private Page staticPage(String path) {
        String[] meta = STATIC.get(path);
        StringBuilder body = new StringBuilder("<main><h1>").append(esc(meta[2])).append("</h1><p>").append(esc(meta[1])).append("</p>");
        List<String> jsonLd = new ArrayList<>();
        if (path.equals("/") || path.equals("/experiences")) {
            body.append("<h2>Tours</h2><ul>");
            for (Tour t : tours.findByStatusOrderByFeaturedDescDisplayOrderAscTitleAsc(TourStatus.ACTIVE))
                body.append("<li><a href=\"/experiences/").append(esc(t.getSlug())).append("\">").append(esc(t.getTitle())).append("</a>: ")
                    .append(esc(t.getShortDescription())).append("</li>");
            body.append("</ul>");
        }
        if (path.equals("/explore")) {
            body.append("<ul>");
            for (Activity a : activities.findByStatusOrderByTitleAsc(Status.PUBLISHED))
                body.append("<li><a href=\"/explore/").append(esc(a.getSlug())).append("\">").append(esc(a.getTitle())).append("</a>: ")
                    .append(esc(a.getSummary())).append("</li>");
            body.append("</ul><p><a href=\"/explore/plan\">Build a day plan</a></p>");
        }
        if (path.equals("/")) jsonLd.add(organisation());
        body.append("<nav><a href=\"/\">Home</a> <a href=\"/about\">About</a> <a href=\"/experiences\">Our Tours</a> ")
            .append("<a href=\"/book-private-trip\">Private Trips</a> <a href=\"/gallery\">Gallery</a> <a href=\"/explore\">Things to Do in Cape Town</a> ")
            .append("<a href=\"/contact\">Contact Us</a></nav></main>");
        return new Page(meta[0], meta[1], path, true, null, jsonLd, body.toString());
    }

    private Page activityPage(Activity a) {
        String path = "/explore/" + a.getSlug();
        String place = a.getArea() != null ? ActivityViews.areaLabel(a.getArea())
            : (a.getTown() == null ? "" : a.getTown() + ", ") + ActivityViews.regionLabel(a.getRegion());
        StringBuilder b = new StringBuilder("<main><article><p><a href=\"/explore\">Things to Do in Cape Town</a></p><h1>").append(esc(a.getTitle())).append("</h1>")
            .append("<p>").append(esc(a.getSummary())).append("</p><p>").append(esc(a.getDescription())).append("</p><dl>")
            .append("<dt>Location</dt><dd>").append(esc(place)).append("</dd>");
        if (a.getAddress() != null) b.append("<dt>Address</dt><dd>").append(esc(a.getAddress())).append("</dd>");
        if (a.getEventSchedule() != null) b.append("<dt>When</dt><dd>").append(esc(a.getEventSchedule())).append("</dd>");
        b.append("<dt>Typical visit</dt><dd>").append(a.getDurationMinutes()).append(" minutes</dd></dl>");
        if (a.getWhatToExpect() != null) b.append("<h2>What to expect</h2><p>").append(esc(a.getWhatToExpect())).append("</p>");
        if (a.getOperatingNote() != null) b.append("<p>").append(esc(a.getOperatingNote())).append("</p>");
        if (a.getParticipationNote() != null) b.append("<p>").append(esc(a.getParticipationNote())).append("</p>");
        String supplier = views.supplierName(a);
        if (supplier != null) b.append("<p>").append(esc(disclosure(supplier))).append("</p>");
        if (a.getLastVerifiedOn() != null) b.append("<p>Information checked against official sources on ").append(a.getLastVerifiedOn()).append(".</p>");
        if (!a.getFaqs().isEmpty()) {
            b.append("<h2>Questions</h2><dl>");
            a.getFaqs().forEach(f -> b.append("<dt>").append(esc(f.getQuestion())).append("</dt><dd>").append(esc(f.getAnswer())).append("</dd>"));
            b.append("</dl>");
        }
        b.append("</article></main>");
        String image = a.publicImages().stream().findFirst().map(ActivityMedia::getUrl).orElse(null);
        // TouristAttraction (or Event) with fields shown on the page. Prices are not shown on Things to Do pages.
        StringBuilder ld = new StringBuilder("{\"@context\":\"https://schema.org\",\"@type\":\"TouristAttraction\",\"name\":").append(json(a.getTitle()))
            .append(",\"description\":").append(json(a.getSummary())).append(",\"url\":").append(json(baseUrl + path));
        if (a.getAddress() != null) ld.append(",\"address\":{\"@type\":\"PostalAddress\",\"streetAddress\":").append(json(a.getAddress()))
            .append(",\"addressLocality\":").append(json(a.getTown() != null ? a.getTown() : "Cape Town"))
            .append(",\"addressRegion\":\"Western Cape\",\"addressCountry\":\"ZA\"}");
        if (a.getLatitude() != null) ld.append(",\"geo\":{\"@type\":\"GeoCoordinates\",\"latitude\":").append(a.getLatitude().toPlainString())
            .append(",\"longitude\":").append(a.getLongitude().toPlainString()).append("}");
        if (a.getPriceStatus() == PriceStatus.FREE) ld.append(",\"isAccessibleForFree\":true");
        if (image != null) ld.append(",\"image\":").append(json(baseUrl + image));
        ld.append("}");
        return new Page(ActivityViews.seoTitle(a), ActivityViews.seoDescription(a), path, true, image,
            List.of(ld.toString(), breadcrumbs("Things to Do in Cape Town", "/explore", a.getTitle(), path)), b.toString());
    }

    private Page tourPage(Tour t) {
        String path = "/experiences/" + t.getSlug();
        String price = switch (t.getPricingType()) {
            case PER_PERSON -> "R" + String.format(java.util.Locale.ROOT, "%,d", t.getBasePriceCents() / 100) + " per person";
            case PER_TRIP -> "R" + String.format(java.util.Locale.ROOT, "%,d", t.getBasePriceCents() / 100) + " per trip";
            case CUSTOM -> "Quote on request";
        };
        StringBuilder body = new StringBuilder("<main><article><p><a href=\"/experiences\">Our Tours</a></p><h1>").append(esc(t.getTitle())).append("</h1><p>")
            .append(esc(t.getShortDescription())).append("</p><p>").append(esc(t.getFullDescription())).append("</p><dl><dt>Price</dt><dd>").append(esc(price))
            .append("</dd><dt>Duration</dt><dd>").append(esc(t.getDuration())).append("</dd>");
        if (t.getGroupSizeLabel() != null) body.append("<dt>Group size</dt><dd>").append(esc(t.getGroupSizeLabel())).append("</dd>");
        if (t.getPickupSummary() != null) body.append("<dt>Pickup</dt><dd>").append(esc(t.getPickupSummary())).append("</dd>");
        body.append("</dl>");
        if (!t.getHighlights().isEmpty()) { body.append("<h2>Highlights</h2><ul>"); t.getHighlights().forEach(h -> body.append("<li>").append(esc(h)).append("</li>")); body.append("</ul>"); }
        if (!t.getItinerary().isEmpty()) {
            body.append("<h2>Sample itinerary</h2>");
            if (t.getItineraryNote() != null) body.append("<p>").append(esc(t.getItineraryNote())).append("</p>");
            body.append("<ol>"); t.getItinerary().forEach(i -> body.append("<li>").append(esc(i)).append("</li>")); body.append("</ol>");
        }
        body.append("<p><a href=\"").append(esc(path)).append("#enquire\">Send an enquiry</a></p></article></main>");
        // Only facts shown on the page: no offers, ratings or availability claims.
        StringBuilder ld = new StringBuilder("{\"@context\":\"https://schema.org\",\"@type\":\"TouristTrip\",\"name\":").append(json(t.getTitle()))
            .append(",\"description\":").append(json(t.getShortDescription())).append(",\"url\":").append(json(baseUrl + path))
            .append(",\"image\":").append(json(baseUrl + t.getHeroImage()));
        ld.append(",\"provider\":{\"@type\":\"TravelAgency\",\"name\":\"SL Tours\",\"url\":").append(json(baseUrl)).append("}}");
        return new Page(t.getSeoTitle(), t.getSeoDescription(), path, true, t.getHeroImage(), List.of(ld.toString(), breadcrumbs("Our Tours", "/experiences", t.getTitle(), path)), body.toString());
    }

    private String organisation() {
        return "{\"@context\":\"https://schema.org\",\"@type\":\"TravelAgency\",\"name\":\"SL Tours\",\"url\":" + json(baseUrl) + ",\"logo\":"
            + json(baseUrl + "/assets/logo.png") + ",\"image\":" + json(baseUrl + "/assets/home/slider-1.jpg")
            + ",\"description\":\"SL Tours is a Cape Town-based tour operator offering guided tours and excursions across South Africa, including Cape Point, Winelands, and the Garden Route.\""
            + ",\"address\":{\"@type\":\"PostalAddress\",\"streetAddress\":\"100 Example Street\",\"addressLocality\":\"Cape Town\",\"addressRegion\":\"Western Cape\",\"addressCountry\":\"ZA\"}"
            + ",\"areaServed\":{\"@type\":\"City\",\"name\":\"Cape Town\"},\"telephone\":\"+27 67 012 7776\",\"email\":\"tours.st@outlook.com\""
            + ",\"sameAs\":[\"https://www.facebook.com/sl.tours.ct\",\"https://www.instagram.com/sl_tours/\",\"https://www.tiktok.com/@sl_tours\",\"https://g.page/r/CRyeNopih97yEAE/review\"]}";
    }

    private String breadcrumbs(String parentName, String parentPath, String name, String path) {
        return "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\",\"itemListElement\":["
            + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Home\",\"item\":" + json(baseUrl + "/") + "},"
            + "{\"@type\":\"ListItem\",\"position\":2,\"name\":" + json(parentName) + ",\"item\":" + json(baseUrl + parentPath) + "},"
            + "{\"@type\":\"ListItem\",\"position\":3,\"name\":" + json(name) + ",\"item\":" + json(baseUrl + path) + "}]}";
    }

    static String normalize(String raw) {
        String path = raw == null || raw.isBlank() ? "/" : raw;
        int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#'); if (h >= 0) path = path.substring(0, h);
        if (!path.startsWith("/")) path = "/" + path;
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.length() > 300 ? path.substring(0, 300) : path;
    }
    private static String lastmod(OffsetDateTime t) { return t == null ? "" : "<lastmod>" + t.toLocalDate() + "</lastmod>"; }
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String json(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t"); case '<' -> out.append("\\u003c"); default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
        }
        return out.append('"').toString();
    }

    /** The same commercial-relationship wording as the activity page. */
    public static String disclosure(String supplier) {
        return "This experience is provided by " + supplier + ", an SL Tours supplier partner. SL Tours has a commercial relationship with this supplier.";
    }
}
