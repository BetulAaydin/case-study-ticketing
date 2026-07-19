package com.turkcell.mayacore.ticketing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @Min(1) int capacity
) {
}
