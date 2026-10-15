package com.sltours.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sltours.activities.repository.ActivityRepository;
import com.sltours.activities.service.ActivityViews;
import com.sltours.tours.repository.TourRepository;
import org.junit.jupiter.api.Test;

/** Staging (indexing off) keeps every page out of search results but still names the production host as canonical. */
class SeoIndexingTest {
    private SeoService seo(boolean indexing) {
        return new SeoService(mock(ActivityRepository.class), mock(TourRepository.class), "https://sl-tours.com/", mock(ActivityViews.class), indexing,
            mock(com.sltours.siteimages.SiteImageService.class));
    }

    @Test void stagingMarksPublicPagesNoindexAndKeepsProductionCanonical() {
        assertThat(seo(false).head("/about")).contains("<meta name=\"robots\" content=\"noindex\" />", "href=\"https://sl-tours.com/about\"");
        assertThat(seo(true).head("/about")).doesNotContain("noindex").contains("href=\"https://sl-tours.com/about\"");
    }

    @Test void stagingHidesTheSitemapAndDoesNotAdvertiseIt() {
        assertThat(seo(false).sitemap()).isEmpty();
        assertThat(seo(false).robots()).doesNotContain("Sitemap").doesNotContain("Disallow");
        assertThat(seo(true).robots()).contains("Sitemap: https://sl-tours.com/sitemap.xml");
        assertThat(seo(true).sitemap()).hasValueSatisfying(xml -> assertThat(xml).contains("<loc>https://sl-tours.com/about</loc>"));
    }

    @Test void onlyTheHomepagePreloadsTheHeroImage() {
        assertThat(seo(true).head("/")).contains("<link rel=\"preload\" as=\"image\" href=\"/assets/home/slider-1.webp\" />");
        assertThat(seo(true).head("/experiences")).doesNotContain("preload");
    }

    @Test void onlyKnownPagesExist() {
        SeoService seo = seo(true);
        assertThat(seo.exists("/")).isTrue();
        assertThat(seo.exists("/experiences?category=CITY")).isTrue();
        assertThat(seo.exists("/operations/bookings")).isTrue();
        assertThat(seo.exists("/tours")).isFalse();
        assertThat(seo.exists("/experiences/no-such-tour")).isFalse();
        assertThat(seo.exists("/index.html")).isFalse();
    }
}
