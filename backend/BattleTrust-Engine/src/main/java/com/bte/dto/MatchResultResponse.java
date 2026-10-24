package com.bte.dto;

import com.bte.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResultResponse(
		Long id,
		Long tournamentId,
		Integer matchNumber,
		List<MatchRankingRequest> rankings,
		List<String> proofImageUrls,
		VerificationStatus verificationStatus,
		LocalDateTime submittedAt,
		LocalDateTime disputeDeadline,int revision
) {
	public MatchResultResponse {
		rankings = List.copyOf(rankings);
		proofImageUrls = List.copyOf(proofImageUrls);
	}
}
