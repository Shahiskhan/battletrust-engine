package com.bte.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
		Long id,
		Long tournamentId,
		String senderName,
		String senderEmail,
		String teamName,
		String messageText,
		boolean isGuest,
		String senderAvatarUrl,
		LocalDateTime timestamp
) {
}
