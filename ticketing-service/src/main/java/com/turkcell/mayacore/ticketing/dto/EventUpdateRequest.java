package com.turkcell.mayacore.ticketing.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public record EventUpdateRequest(
        String title,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Min(1) Integer capacity
) {
}
