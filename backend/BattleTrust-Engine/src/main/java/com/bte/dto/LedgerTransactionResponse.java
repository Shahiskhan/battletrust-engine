package com.bte.dto;

import com.bte.enums.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerTransactionResponse(
		Long id,
		Long tournamentId,
		Long senderId,
		Long receiverId,
		BigDecimal amount,
		LedgerTransactionType transactionType,
		String referenceId,
		Instant createdAt
) {
}
