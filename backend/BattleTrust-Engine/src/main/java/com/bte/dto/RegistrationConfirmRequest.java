package com.bte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrationConfirmRequest(
		@NotNull @Positive Long registrationId,
		@NotBlank @Size(min = 36, max = 36) String ticketCode,
		@NotBlank @Size(max = 128) String transactionId
) {
}
