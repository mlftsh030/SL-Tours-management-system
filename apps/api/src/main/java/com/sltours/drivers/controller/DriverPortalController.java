package com.sltours.drivers.controller;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.dto.AssignmentDtos.*;
import com.sltours.assignments.service.DriverAssignmentService;
import com.sltours.drivers.dto.DriverDtos.DriverProfile;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.service.*;
import com.sltours.inspections.dto.InspectionDtos.*;
import com.sltours.inspections.service.InspectionService;
import com.sltours.media.*;
import com.sltours.shared.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Driver portal API. The security filter chain limits /api/v1/driver/** to DRIVER accounts and every handler
 * resolves the driver from the session, so a driver can only ever reach their own assignments, inspections and media.
 */
@RestController
@RequestMapping("/api/v1/driver")
public class DriverPortalController {
    private final CurrentDriver currentDriver;
    private final DriverAssignmentService assignments;
    private final InspectionService inspections;
    private final DriverEligibility eligibility;
    private final AccountRepository accounts;
    private final MediaService media;
    public DriverPortalController(CurrentDriver currentDriver, DriverAssignmentService assignments, InspectionService inspections,
            DriverEligibility eligibility, AccountRepository accounts, MediaService media) {
        this.currentDriver = currentDriver; this.assignments = assignments; this.inspections = inspections;
        this.eligibility = eligibility; this.accounts = accounts; this.media = media;
    }
    @GetMapping("/me") DriverProfile me(Authentication authentication) {
        Driver driver = currentDriver.require(authentication);
        boolean enabled = accounts.findById(driver.getAccountId()).map(Account::isEnabled).orElse(false);
        return new DriverProfile(driver.getName(), driver.getVerificationStatus(), driver.getLicenceExpiresOn(), driver.getPrdpExpiresOn(),
            eligibility.problems(driver, enabled, LocalDate.now(ZoneId.of("Africa/Johannesburg"))));
    }
    @GetMapping("/checklist") List<ChecklistDefinition> checklist() { return InspectionService.checklist(); }
    @GetMapping("/assignments") List<DriverAssignmentSummary> list(Authentication authentication) {
        return assignments.list(currentDriver.require(authentication));
    }
    @GetMapping("/assignments/{id}") DriverAssignmentDetail get(@PathVariable UUID id, Authentication authentication) {
        return assignments.get(currentDriver.require(authentication), id);
    }
    @PostMapping(path = "/assignments/{id}/collection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SubmissionResult collect(@PathVariable UUID id, @Valid @RequestPart("inspection") InspectionForm form,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos, @RequestPart(name = "selfie", required = false) MultipartFile selfie,
            Authentication authentication) {
        return inspections.submitCollection(currentDriver.require(authentication), id, form, photos == null ? List.of() : photos, selfie);
    }
    @PostMapping(path = "/assignments/{id}/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SubmissionResult giveBack(@PathVariable UUID id, @Valid @RequestPart("inspection") InspectionForm form,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos, Authentication authentication) {
        return inspections.submitReturn(currentDriver.require(authentication), id, form, photos == null ? List.of() : photos);
    }
    @GetMapping("/media/{id}") ResponseEntity<InputStreamResource> media(@PathVariable UUID id, Authentication authentication) {
        Driver driver = currentDriver.require(authentication);
        if (!inspections.driverMayView(driver, id)) throw new ResourceNotFoundException("File not found");
        MediaFile file = media.find(id);
        return MediaResponses.serve(file, media.open(file));
    }
}
