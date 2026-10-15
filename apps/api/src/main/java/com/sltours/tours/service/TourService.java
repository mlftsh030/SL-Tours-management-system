package com.sltours.tours.service;

import com.sltours.shared.error.ResourceNotFoundException;
import com.sltours.tours.dto.TourDetailResponse;
import com.sltours.tours.dto.TourSummaryResponse;
import com.sltours.tours.model.Tour;
import com.sltours.tours.model.TourCategory;
import com.sltours.tours.model.TourStatus;
import com.sltours.tours.repository.TourRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TourService {
    private final TourRepository tourRepository;
    private final TourMapper tourMapper;

    public TourService(TourRepository tourRepository, TourMapper tourMapper) {
        this.tourRepository = tourRepository;
        this.tourMapper = tourMapper;
    }

    @Transactional(readOnly = true)
    public List<TourSummaryResponse> listTours(TourCategory category, Boolean featured) {
        List<Tour> tours;
        if (Boolean.TRUE.equals(featured)) {
            tours = tourRepository.findByFeaturedTrueAndStatusOrderByDisplayOrderAscTitleAsc(TourStatus.ACTIVE);
        } else if (category != null) {
            tours = tourRepository.findByCategoryAndStatusOrderByDisplayOrderAscTitleAsc(category, TourStatus.ACTIVE);
        } else {
            // The catalogue follows the operator's published order.
            tours = tourRepository.findByStatusOrderByDisplayOrderAscTitleAsc(TourStatus.ACTIVE);
        }

        return tours.stream().map(tourMapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public TourDetailResponse getTour(String slug) {
        return tourMapper.toDetail(findActiveBySlug(slug));
    }

    @Transactional(readOnly = true)
    public Tour findActiveBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ResourceNotFoundException("Tour not found");
        }
        return tourRepository.findBySlugAndStatus(slug, TourStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Tour not found"));
    }
}
