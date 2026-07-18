package com.turkcell.mayacore.auth.dto;

import com.turkcell.mayacore.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        Role role
) {
}
