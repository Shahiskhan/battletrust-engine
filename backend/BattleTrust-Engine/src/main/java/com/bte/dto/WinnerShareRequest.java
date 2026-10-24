package com.bte.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WinnerShareRequest(
		@NotNull @Positive Long userId,
		@NotNull @Positive Integer rank,
		@NotNull @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
		BigDecimal winnersPoolPercent
) {
}
