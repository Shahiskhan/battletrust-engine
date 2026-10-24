package com.bte.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 32) String whatsappNumber,
        @jakarta.validation.constraints.Pattern(regexp="ROLE_PLAYER|ROLE_ORGANIZER",message="Choose ROLE_PLAYER or ROLE_ORGANIZER") String role
) {
}
