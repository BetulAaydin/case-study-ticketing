package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.ticketing.domain.Event;
import com.turkcell.mayacore.ticketing.dto.EventCreateRequest;
import com.turkcell.mayacore.ticketing.dto.EventResponse;
import com.turkcell.mayacore.ticketing.dto.EventUpdateRequest;
import com.turkcell.mayacore.ticketing.repository.EventRepository;
import com.turkcell.mayacore.ticketing.security.SessionRoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SessionRoleResolver sessionRoleResolver;

    @InjectMocks
    private EventService eventService;

    private final LocalDateTime starts = LocalDateTime.of(2026, 8, 1, 20, 0);
    private final LocalDateTime ends = LocalDateTime.of(2026, 8, 1, 23, 0);

    @Test
    void create_shouldCreateDraftEvent() {
        EventCreateRequest request = new EventCreateRequest("Concert", "Arena", starts, ends, 100);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        EventResponse response = eventService.create(10L, request, "127.0.0.1", "junit");

        assertThat(response.published()).isFalse();
        assertThat(response.reservedSeats()).isZero();
        assertThat(response.title()).isEqualTo("Concert");
        verify(auditService).log(eq(10L), eq("EVENT_CREATED"), eq("Event"), eq(1L), any(), any());
    }

    @Test
    void update_shouldUpdateOwnedEvent() {
        Event event = ownedDraft(1L, 10L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventUpdateRequest request = new EventUpdateRequest("Updated", null, null, null, null);
        EventResponse response = eventService.update(10L, "sid-1", 1L, request, "ip", "ua");

        assertThat(response.title()).isEqualTo("Updated");
    }

    @Test
    void update_shouldThrow_whenNotOwner() {
        Event event = ownedDraft(1L, 10L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(sessionRoleResolver.isAdmin("sid-2")).thenReturn(false);

        EventUpdateRequest request = new EventUpdateRequest("X", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(99L, "sid-2", 1L, request, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("EVENT_FORBIDDEN");
    }

    @Test
    void update_shouldThrow_whenPublished() {
        Event event = ownedDraft(1L, 10L);
        event.setPublished(true);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        EventUpdateRequest request = new EventUpdateRequest("X", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(10L, "sid-1", 1L, request, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("EVENT_PUBLISHED");
    }

    @Test
    void publish_shouldSetPublishedTrue() {
        Event event = ownedDraft(1L, 10L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.publish(10L, "sid-1", 1L, "ip", "ua");

        assertThat(response.published()).isTrue();
    }

    @Test
    void publish_shouldThrow_whenNotOwner() {
        Event event = ownedDraft(1L, 10L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(sessionRoleResolver.isAdmin("sid-x")).thenReturn(false);

        assertThatThrownBy(() -> eventService.publish(99L, "sid-x", 1L, "ip", "ua"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("EVENT_FORBIDDEN");
    }

    @Test
    void list_withOwnerId_shouldFilterByOwner() {
        Event event = ownedDraft(1L, 10L);
        when(eventRepository.findByOwnerId(10L)).thenReturn(List.of(event));

        List<EventResponse> result = eventService.list(10L);

        assertThat(result).hasSize(1);
        verify(eventRepository).findByOwnerId(10L);
        verify(eventRepository, never()).findAll();
    }

    @Test
    void publicSearch_shouldReturnPublishedEvents() {
        Event event = ownedDraft(1L, 10L);
        event.setPublished(true);
        when(eventRepository.searchPublished(null, null, null)).thenReturn(List.of(event));

        List<EventResponse> result = eventService.publicSearch(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().published()).isTrue();
    }

    private Event ownedDraft(Long id, Long ownerId) {
        Event event = new Event();
        event.setId(id);
        event.setOwnerId(ownerId);
        event.setTitle("Concert");
        event.setVenue("Arena");
        event.setStartsAt(starts);
        event.setEndsAt(ends);
        event.setCapacity(100);
        event.setReservedSeats(0);
        event.setPublished(false);
        return event;
    }
}
