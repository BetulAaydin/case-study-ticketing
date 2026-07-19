package com.turkcell.mayacore.ticketing.controller;

import com.turkcell.mayacore.commonlibrary.dto.ApiResponse;
import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import com.turkcell.mayacore.ticketing.dto.EventCreateRequest;
import com.turkcell.mayacore.ticketing.dto.EventResponse;
import com.turkcell.mayacore.ticketing.dto.EventUpdateRequest;
import com.turkcell.mayacore.ticketing.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
@Tag(name = "Events", description = "Event management endpoints")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ApiResponse<EventResponse> create(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @RequestHeader(value = GatewayHeaders.SESSION_ID, required = false) String sessionId,
            @Valid @RequestBody EventCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(eventService.create(
                userId, request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PutMapping("/{id}")
    public ApiResponse<EventResponse> update(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @RequestHeader(value = GatewayHeaders.SESSION_ID, required = false) String sessionId,
            @PathVariable Long id,
            @Valid @RequestBody EventUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(eventService.update(
                userId, sessionId, id, request, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<EventResponse> publish(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @RequestHeader(value = GatewayHeaders.SESSION_ID, required = false) String sessionId,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(eventService.publish(
                userId, sessionId, id, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @GetMapping
    public ApiResponse<List<EventResponse>> list(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @RequestParam(required = false) Long ownerId) {
        return ApiResponse.success(eventService.list(ownerId));
    }

    @GetMapping("/public")
    public ApiResponse<List<EventResponse>> publicSearch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q) {
        return ApiResponse.success(eventService.publicSearch(from, to, q));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(GatewayHeaders.FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
