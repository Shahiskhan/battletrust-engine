package com.bte.dto;

import com.bte.enums.WithdrawalPaymentMethod;
import com.bte.enums.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record WithdrawalResponse(
		Long id,
		Long userId,
		String userEmail,
		BigDecimal amount,
		WithdrawalPaymentMethod paymentMethod,
		String accountDetails,
		WithdrawalStatus status,
		Instant requestedAt,
		String decisionNote,
		Long reviewedById,
		Instant reviewedAt
) {
}
