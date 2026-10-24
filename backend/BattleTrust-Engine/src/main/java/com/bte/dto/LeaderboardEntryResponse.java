package com.bte.dto;

import com.bte.enums.GameCategory;

import java.math.BigDecimal;

public record LeaderboardEntryResponse(
		int rank,
		Long userId,
		String name,
		String profilePictureUrl,
		GameCategory gameCategory,
		int matchesPlayed,
		int matchesWon,
		int totalKills,
		int reputationScore,
		BigDecimal winRatePercent
) {
}
