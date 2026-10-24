package com.bte.dto;

import com.bte.enums.TournamentStatus;
import com.bte.enums.GameCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TournamentResponse(
		Long id,
		String title,
		String gameType,
		GameCategory gameCategory,
		String tournamentLogoUrl,
		String rules,
		Long leaderId,
		String leaderName,
		BigDecimal entryFeePkr,
		Integer maxPlayers,
		LocalDateTime startTime,
		BigDecimal winnersSharePercent,
		BigDecimal leaderSharePercent,
		BigDecimal systemSharePercent,
		TournamentStatus status,
		LocalDateTime createdAt,
		Integer squadSize,
		BigDecimal organizerStakeAmount,
		com.bte.enums.OrganizerStakeStatus organizerStakeStatus,
		boolean automaticPayoutsEnabled,
        String visibility,boolean roomChatEnabled,int plannedRounds,boolean registrationOpen
) {
}
