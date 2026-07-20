package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.ticketing.domain.Event;
import com.turkcell.mayacore.ticketing.dto.EventCreateRequest;
import com.turkcell.mayacore.ticketing.dto.EventResponse;
import com.turkcell.mayacore.ticketing.dto.EventUpdateRequest;
import com.turkcell.mayacore.ticketing.repository.EventRepository;
import com.turkcell.mayacore.ticketing.security.SessionRoleResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final AuditService auditService;
    private final SessionRoleResolver sessionRoleResolver;

    public EventService(EventRepository eventRepository,
                        AuditService auditService,
                        SessionRoleResolver sessionRoleResolver) {
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.sessionRoleResolver = sessionRoleResolver;
    }

    public EventResponse create(Long ownerId, EventCreateRequest request, String ip, String userAgent) {
        Event event = new Event();
        event.setOwnerId(ownerId);
        event.setTitle(request.title());
        event.setVenue(request.venue());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setCapacity(request.capacity());
        event.setReservedSeats(0);
        event.setPublished(false);

        event = eventRepository.save(event);
        auditService.log(ownerId, "EVENT_CREATED", "Event", event.getId(), ip, userAgent);
        return EventResponse.from(event);
    }

    public EventResponse update(Long userId, String sessionId, Long eventId,
                                EventUpdateRequest request, String ip, String userAgent) {
        Event event = findEvent(eventId);
        assertCanManage(userId, sessionId, event);

        if (event.isPublished()) {
            throw new BusinessException(
                    "EVENT_PUBLISHED",
                    "Published event cannot be updated",
                    HttpStatus.CONFLICT);
        }

        if (request.title() != null) {
            event.setTitle(request.title());
        }
        if (request.venue() != null) {
            event.setVenue(request.venue());
        }
        if (request.startsAt() != null) {
            event.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            event.setEndsAt(request.endsAt());
        }
        if (request.capacity() != null) {
            if (request.capacity() < event.getReservedSeats()) {
                throw new BusinessException(
                        "EVENT_CAPACITY_TOO_LOW",
                        "Capacity cannot be less than reserved seats (" + event.getReservedSeats() + ")",
                        HttpStatus.CONFLICT);
            }
            event.setCapacity(request.capacity());
        }

        event = eventRepository.save(event);
        auditService.log(userId, "EVENT_UPDATED", "Event", event.getId(), ip, userAgent);
        return EventResponse.from(event);
    }

    public EventResponse publish(Long userId, String sessionId, Long eventId,
                                 String ip, String userAgent) {
        Event event = findEvent(eventId);
        assertCanManage(userId, sessionId, event);

        if (event.isPublished()) {
            throw new BusinessException(
                    "EVENT_ALREADY_PUBLISHED",
                    "Event is already published",
                    HttpStatus.CONFLICT);
        }

        event.setPublished(true);
        event = eventRepository.save(event);
        auditService.log(userId, "EVENT_PUBLISHED", "Event", event.getId(), ip, userAgent);
        return EventResponse.from(event);
    }

    public void delete(Long userId, String sessionId, Long eventId, String ip, String userAgent) {
        Event event = findEvent(eventId);
        assertCanManage(userId, sessionId, event);

        if (event.isPublished()) {
            throw new BusinessException(
                    "EVENT_PUBLISHED",
                    "Published event cannot be deleted",
                    HttpStatus.CONFLICT);
        }
        if (event.getReservedSeats() > 0) {
            throw new BusinessException(
                    "EVENT_HAS_RESERVATIONS",
                    "Event with reserved seats cannot be deleted",
                    HttpStatus.CONFLICT);
        }

        Long id = event.getId();
        eventRepository.delete(event);
        auditService.log(userId, "EVENT_DELETED", "Event", id, ip, userAgent);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(Long ownerId) {
        List<Event> events = ownerId != null
                ? eventRepository.findByOwnerId(ownerId)
                : eventRepository.findAll();
        return events.stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> publicSearch(LocalDateTime from, LocalDateTime to, String query) {
        return eventRepository.searchPublished(from, to, query).stream()
                .map(EventResponse::from)
                .toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND",
                        "Event not found: " + eventId,
                        HttpStatus.NOT_FOUND));
    }

    private void assertCanManage(Long userId, String sessionId, Event event) {
        if (event.getOwnerId().equals(userId) || sessionRoleResolver.isAdmin(sessionId)) {
            return;
        }
        throw new BusinessException(
                "EVENT_FORBIDDEN",
                "Not allowed to manage this event",
                HttpStatus.FORBIDDEN);
    }
}
