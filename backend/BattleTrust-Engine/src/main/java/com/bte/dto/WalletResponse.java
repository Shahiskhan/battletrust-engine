package com.bte.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(
		Long userId,
		BigDecimal availableBalance,
		BigDecimal lockedBalance,
		BigDecimal totalEarned,
		BigDecimal organizerStakeBalance,
		BigDecimal escrowReservedBalance,
		List<LedgerTransactionResponse> ledgerHistory
) {
	public WalletResponse {
		ledgerHistory = List.copyOf(ledgerHistory);
	}
}
