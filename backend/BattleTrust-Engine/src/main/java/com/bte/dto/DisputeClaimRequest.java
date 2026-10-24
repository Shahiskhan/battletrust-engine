package com.bte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DisputeClaimRequest(
		@NotNull @Positive Long matchResultId,
		@NotBlank @Size(max = 2000) String reason
) {
}
