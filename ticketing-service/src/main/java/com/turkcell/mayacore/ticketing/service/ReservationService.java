package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.ticketing.domain.Event;
import com.turkcell.mayacore.ticketing.domain.Reservation;
import com.turkcell.mayacore.ticketing.domain.ReservationStatus;
import com.turkcell.mayacore.ticketing.dto.ReservationResponse;
import com.turkcell.mayacore.ticketing.repository.EventRepository;
import com.turkcell.mayacore.ticketing.repository.ReservationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReservationService {

    private static final int MAX_RETRIES = 3;

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public ReservationService(ReservationRepository reservationRepository,
                              EventRepository eventRepository,
                              AuditService auditService,
                              TransactionTemplate transactionTemplate) {
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    public ReservationResponse create(Long userId, Long eventId, int seats,
                                      String ip, String userAgent) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        createOnce(userId, eventId, seats, ip, userAgent));
            } catch (OptimisticLockingFailureException ignored) {
                // retry
            }
        }
        throw new BusinessException(
                "RESERVATION_CONFLICT",
                "Could not reserve seats due to concurrent updates",
                HttpStatus.CONFLICT);
    }

    public ReservationResponse confirm(Long userId, Long reservationId, String ip, String userAgent) {
        return transactionTemplate.execute(status -> {
            Reservation reservation = findOwnedReservation(userId, reservationId);

            if (reservation.getStatus() != ReservationStatus.PENDING) {
                throw new BusinessException(
                        "RESERVATION_INVALID_STATUS",
                        "Only PENDING reservations can be confirmed",
                        HttpStatus.CONFLICT);
            }

            reservation.setStatus(ReservationStatus.CONFIRMED);
            Reservation saved = reservationRepository.save(reservation);
            auditService.log(userId, "RESERVATION_CONFIRMED", "Reservation",
                    saved.getId(), ip, userAgent);
            return ReservationResponse.from(saved);
        });
    }

    public ReservationResponse cancel(Long userId, Long reservationId, String ip, String userAgent) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        cancelOnce(userId, reservationId, ip, userAgent));
            } catch (OptimisticLockingFailureException ignored) {
                // retry
            }
        }
        throw new BusinessException(
                "RESERVATION_CONFLICT",
                "Could not cancel reservation due to concurrent updates",
                HttpStatus.CONFLICT);
    }

    private ReservationResponse createOnce(Long userId, Long eventId, int seats,
                                           String ip, String userAgent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND", "Event not found: " + eventId, HttpStatus.NOT_FOUND));

        if (!event.isPublished()) {
            throw new BusinessException(
                    "EVENT_NOT_PUBLISHED", "Event is not published", HttpStatus.CONFLICT);
        }

        if (event.getCapacity() - event.getReservedSeats() < seats) {
            throw new BusinessException(
                    "NOT_ENOUGH_CAPACITY", "Not enough capacity", HttpStatus.CONFLICT);
        }

        event.setReservedSeats(event.getReservedSeats() + seats);
        eventRepository.saveAndFlush(event);

        Reservation reservation = new Reservation();
        reservation.setEventId(eventId);
        reservation.setUserId(userId);
        reservation.setSeats(seats);
        reservation.setStatus(ReservationStatus.PENDING);
        Reservation saved = reservationRepository.save(reservation);

        auditService.log(userId, "RESERVATION_CREATED", "Reservation",
                saved.getId(), ip, userAgent);
        return ReservationResponse.from(saved);
    }

    private ReservationResponse cancelOnce(Long userId, Long reservationId,
                                           String ip, String userAgent) {
        Reservation reservation = findOwnedReservation(userId, reservationId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationResponse.from(reservation);
        }

        Event event = eventRepository.findById(reservation.getEventId())
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND",
                        "Event not found: " + reservation.getEventId(),
                        HttpStatus.NOT_FOUND));

        event.setReservedSeats(Math.max(0, event.getReservedSeats() - reservation.getSeats()));
        eventRepository.saveAndFlush(event);

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        auditService.log(userId, "RESERVATION_CANCELLED", "Reservation",
                saved.getId(), ip, userAgent);
        return ReservationResponse.from(saved);
    }

    private Reservation findOwnedReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found: " + reservationId,
                        HttpStatus.NOT_FOUND));

        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(
                    "RESERVATION_FORBIDDEN",
                    "Not allowed to manage this reservation",
                    HttpStatus.FORBIDDEN);
        }
        return reservation;
    }
}
