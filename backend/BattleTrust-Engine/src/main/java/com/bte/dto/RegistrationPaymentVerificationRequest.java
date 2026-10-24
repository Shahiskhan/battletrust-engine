package com.bte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationPaymentVerificationRequest(
		@NotBlank @Size(max = 128) String transactionId
) {
}
