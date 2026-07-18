package com.turkcell.mayacore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank String refreshToken
) {
}
