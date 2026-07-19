package com.turkcell.mayacore.ticketing.dto;

import jakarta.validation.constraints.Min;

public record ReservationCreateRequest(
        @Min(1) int seats
) {
}
