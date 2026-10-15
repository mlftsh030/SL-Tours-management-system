package com.sltours.tours.controller;

import com.sltours.tours.dto.TourDetailResponse;
import com.sltours.tours.dto.TourSummaryResponse;
import com.sltours.tours.model.TourCategory;
import com.sltours.tours.service.TourService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tours")
public class TourController {
    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    List<TourSummaryResponse> listTours(
            @RequestParam(required = false) TourCategory category,
            @RequestParam(required = false) Boolean featured
    ) {
        return tourService.listTours(category, featured);
    }

    @GetMapping("/{slug}")
    TourDetailResponse getTour(@PathVariable String slug) {
        return tourService.getTour(slug);
    }
}
