package com.sltours.shared.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record PageResponse<T>(List<T> content, int number, int totalPages, long totalElements) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }
}
