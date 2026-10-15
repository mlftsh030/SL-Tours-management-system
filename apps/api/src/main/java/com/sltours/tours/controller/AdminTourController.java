package com.sltours.tours.controller;
import com.sltours.tours.dto.*;
import com.sltours.tours.service.AdminTourService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/admin/tours")
public class AdminTourController {
    private final AdminTourService tours;
    public AdminTourController(AdminTourService tours) { this.tours = tours; }
    @GetMapping public com.sltours.shared.dto.PageResponse<AdminTourResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return com.sltours.shared.dto.PageResponse.from(tours.list(page, size)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public AdminTourResponse create(@Valid @RequestBody SaveTourRequest request) { return tours.create(request); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public AdminTourResponse update(@PathVariable UUID id, @Valid @RequestBody SaveTourRequest request) { return tours.update(id, request); }
}
