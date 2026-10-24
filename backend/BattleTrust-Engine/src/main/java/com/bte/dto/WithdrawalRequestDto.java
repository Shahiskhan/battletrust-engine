package com.bte.dto;

import com.bte.enums.WithdrawalPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WithdrawalRequestDto(
		@NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
		@NotNull WithdrawalPaymentMethod paymentMethod,
		@NotBlank @Size(max = 1000) String accountDetails
) {
}
