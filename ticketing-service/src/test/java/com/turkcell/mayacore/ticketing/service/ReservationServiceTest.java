package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.ticketing.domain.Event;
import com.turkcell.mayacore.ticketing.domain.Reservation;
import com.turkcell.mayacore.ticketing.domain.ReservationStatus;
import com.turkcell.mayacore.ticketing.dto.ReservationResponse;
import com.turkcell.mayacore.ticketing.repository.EventRepository;
import com.turkcell.mayacore.ticketing.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository, eventRepository, auditService, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void create_shouldCreatePendingReservation() {
        Event event = publishedEvent(1L, 10, 0);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReservationResponse response = reservationService.create(7L, 1L, 2, "ip", "ua");

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.seats()).isEqualTo(2);
        assertThat(event.getReservedSeats()).isEqualTo(2);
    }

    @Test
    void create_shouldThrow_whenNotPublished() {
        Event event = publishedEvent(1L, 10, 0);
        event.setPublished(false);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> reservationService.create(7L, 1L, 1, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("EVENT_NOT_PUBLISHED");
    }

    @Test
    void create_shouldThrow_whenCapacityExceeded() {
        Event event = publishedEvent(1L, 5, 4);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> reservationService.create(7L, 1L, 2, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("NOT_ENOUGH_CAPACITY");
    }

    @Test
    void create_shouldIncrementReservedSeats() {
        Event event = publishedEvent(1L, 10, 3);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        reservationService.create(7L, 1L, 2, "ip", "ua");

        assertThat(event.getReservedSeats()).isEqualTo(5);
    }

    @Test
    void confirm_shouldSetStatusConfirmed() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 2);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.confirm(7L, 20L, "ip", "ua");

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirm_shouldThrow_whenNotOwner() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 2);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(99L, 20L, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("RESERVATION_FORBIDDEN");
    }

    @Test
    void confirm_shouldThrow_whenNotPending() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(7L, 20L, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("RESERVATION_INVALID_STATUS");
    }

    @Test
    void cancel_shouldSetStatusCancelled() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 2);
        Event event = publishedEvent(1L, 10, 2);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.cancel(7L, 20L, "ip", "ua");

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancel_shouldDecrementReservedSeats() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 3);
        Event event = publishedEvent(1L, 10, 5);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.cancel(7L, 20L, "ip", "ua");

        assertThat(event.getReservedSeats()).isEqualTo(2);
    }

    @Test
    void cancel_alreadyCancelled_shouldReturnCancelled() {
        Reservation reservation = pendingReservation(20L, 7L, 1L, 2);
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.cancel(7L, 20L, "ip", "ua");

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    private Event publishedEvent(Long id, int capacity, int reserved) {
        Event event = new Event();
        event.setId(id);
        event.setOwnerId(1L);
        event.setTitle("Show");
        event.setVenue("Hall");
        event.setStartsAt(java.time.LocalDateTime.now().plusDays(1));
        event.setEndsAt(java.time.LocalDateTime.now().plusDays(1).plusHours(2));
        event.setCapacity(capacity);
        event.setReservedSeats(reserved);
        event.setPublished(true);
        event.setVersion(0L);
        return event;
    }

    private Reservation pendingReservation(Long id, Long userId, Long eventId, int seats) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId(userId);
        reservation.setEventId(eventId);
        reservation.setSeats(seats);
        reservation.setStatus(ReservationStatus.PENDING);
        return reservation;
    }
}
