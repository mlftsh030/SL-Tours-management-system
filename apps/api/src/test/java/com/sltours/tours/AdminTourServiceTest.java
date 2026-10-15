package com.sltours.tours;

import com.sltours.audit.AuditService;
import com.sltours.tours.dto.SaveTourRequest;
import com.sltours.tours.model.*;
import com.sltours.tours.repository.TourRepository;
import com.sltours.tours.service.*;
import com.sltours.shared.error.ConflictException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AdminTourServiceTest {
    private final TourRepository tours = mock(TourRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AdminTourService service = new AdminTourService(tours, new TourMapper(), audit);
    private SaveTourRequest request(long version, String image) {
        return new SaveTourRequest(version,"new-tour","New tour","Summary","Detail","Half day",123456,
            PricingType.PER_PERSON,1,18,true,false,TourStatus.DRAFT,TourCategory.CITY,image,null,"SEO","Summary",null,100,
            List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),null);
    }
    @Test void rejectsStaleEditBeforeChangingTourOrWritingAudit() {
        UUID id = UUID.randomUUID(); Tour tour = new Tour(); tour.setBasePriceCents(99000);
        when(tours.findById(id)).thenReturn(Optional.of(tour));
        assertThatThrownBy(() -> service.update(id,request(5,"/assets/city-tour.jpg"))).isInstanceOf(ConflictException.class);
        assertThat(tour.getBasePriceCents()).isEqualTo(99000);
        verify(tours,never()).flush(); verifyNoInteractions(audit);
    }
    @Test void rejectsExternalImageOrTraversalBeforeDatabaseMutation() {
        for (String image : List.of("https://tracker.example/image.jpg", "/assets/../private.jpg", "javascript:alert(1)")) {
            assertThatThrownBy(() -> service.create(request(0,image))).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(tours,audit);
    }
}
