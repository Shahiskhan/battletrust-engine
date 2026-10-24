package com.bte.dto;

import java.math.BigDecimal;

public record EscrowSettlementResponse(
		Long tournamentId,
		BigDecimal totalPool,
		BigDecimal winnersPool,
		BigDecimal leaderCommission,
		BigDecimal platformRevenue,
		boolean disbursed
) {
}
