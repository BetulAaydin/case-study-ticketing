package com.turkcell.mayacore.ticketing.dto;

import com.turkcell.mayacore.ticketing.domain.Reservation;
import com.turkcell.mayacore.ticketing.domain.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        ReservationStatus status,
        int seats,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getStatus(),
                reservation.getSeats(),
                reservation.getCreatedAt()
        );
    }
}
