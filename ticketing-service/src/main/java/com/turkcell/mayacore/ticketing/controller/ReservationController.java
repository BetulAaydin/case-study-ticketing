package com.turkcell.mayacore.ticketing.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.mayacore.commonlibrary.dto.ApiResponse;
import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import com.turkcell.mayacore.commonlibrary.util.HashUtil;
import com.turkcell.mayacore.ticketing.dto.ReservationCreateRequest;
import com.turkcell.mayacore.ticketing.dto.ReservationResponse;
import com.turkcell.mayacore.ticketing.service.CachedResponse;
import com.turkcell.mayacore.ticketing.service.IdempotencyService;
import com.turkcell.mayacore.ticketing.service.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reservations", description = "Reservation management endpoints")
public class ReservationController {

    private final ReservationService reservationService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ReservationController(ReservationService reservationService,
                                 IdempotencyService idempotencyService,
                                 ObjectMapper objectMapper) {
        this.reservationService = reservationService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/events/{eventId}/reservations")
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @RequestHeader(GatewayHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable Long eventId,
            @Valid @RequestBody ReservationCreateRequest request,
            HttpServletRequest httpRequest) throws JsonProcessingException {

        String endpoint = "/events/" + eventId + "/reservations";
        String requestHash = HashUtil.sha256(objectMapper.writeValueAsString(request));

        CachedResponse cached = idempotencyService.checkIdempotency(idempotencyKey, endpoint, requestHash);
        if (cached != null) {
            ReservationResponse body = objectMapper.readValue(cached.responseBody(), ReservationResponse.class);
            return ResponseEntity.status(cached.statusCode()).body(ApiResponse.success(body));
        }

        try {
            ReservationResponse created = reservationService.create(
                    userId, eventId, request.seats(),
                    clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
            String responseJson = objectMapper.writeValueAsString(created);
            idempotencyService.complete(idempotencyKey, endpoint, responseJson, HttpStatus.CREATED.value());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
        } catch (BusinessException ex) {
            idempotencyService.fail(idempotencyKey, endpoint);
            throw ex;
        } catch (RuntimeException ex) {
            idempotencyService.fail(idempotencyKey, endpoint);
            throw ex;
        }
    }

    @PostMapping("/reservations/{id}/confirm")
    public ApiResponse<ReservationResponse> confirm(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(reservationService.confirm(
                userId, id, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ApiResponse<ReservationResponse> cancel(
            @RequestHeader(GatewayHeaders.USER_ID) Long userId,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(reservationService.cancel(
                userId, id, clientIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(GatewayHeaders.FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
