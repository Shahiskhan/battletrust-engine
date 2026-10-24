package com.bte.dto;

import com.bte.enums.ChallengeRoomStatus;
import com.bte.enums.ChallengeStatus;
import com.bte.enums.GameCategory;

import java.time.LocalDateTime;

public record ChallengeResponse(
		Long id,
		Long challengerUserId,
		String challengerName,
		Long opponentUserId,
		String opponentName,
		GameCategory gameCategory,
		String message,
		ChallengeStatus status,
		String roomId,
		String roomPassword,
		ChallengeRoomStatus roomStatus,
		LocalDateTime createdAt,
		LocalDateTime respondedAt
) {
}
