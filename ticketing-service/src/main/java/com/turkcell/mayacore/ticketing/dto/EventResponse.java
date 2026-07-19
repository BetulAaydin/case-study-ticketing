package com.turkcell.mayacore.ticketing.dto;

import com.turkcell.mayacore.ticketing.domain.Event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        Long ownerId,
        String title,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int capacity,
        int reservedSeats,
        boolean published
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getOwnerId(),
                event.getTitle(),
                event.getVenue(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                event.getReservedSeats(),
                event.isPublished()
        );
    }
}
