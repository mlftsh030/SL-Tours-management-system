package com.sltours.support;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.ActivityEnums.*;
import java.time.LocalDate;
import java.util.List;

/** Region, themes and a picture for activities created in tests: published listings need at least one usable picture. */
public final class CatalogueTestData {
    private CatalogueTestData() {}
    public static Media picture() {
        return new Media(MediaKind.IMAGE, "/assets/gallery/41.jpg", null, 960, 640, "Test picture", null, "SL Tours", "https://sl-tours.com/gallery",
            "SL Tours own photograph", null, MediaPermission.SL_TOURS_OWN, Depiction.EXACT, LocalDate.now());
    }
    public static Regional capeTown(Theme... themes) {
        return new Regional(Region.CAPE_TOWN, "Cape Town", null, null, Kind.ATTRACTION, null, null, null, null, null, OperatingStatus.OPEN, null,
            LocalDate.now(), LocalDate.now(), List.of(themes), List.of(picture()));
    }
}
