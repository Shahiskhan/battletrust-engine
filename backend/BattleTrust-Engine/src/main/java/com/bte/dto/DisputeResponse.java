package com.bte.dto;

import com.bte.enums.DisputeStatus;

import java.time.LocalDateTime;

public record DisputeResponse(
		Long id,
		Long tournamentId,
		Long matchResultId,
		String reporterEmail,
		String reason,
		String screenshotUrl,
		DisputeStatus status,
		String resolutionNote,
		LocalDateTime createdAt,
		LocalDateTime resolvedAt
) {
}
