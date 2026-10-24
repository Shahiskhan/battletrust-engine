package com.bte.dto;

import com.bte.enums.DisputeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DisputeResolveRequest(
		@NotNull DisputeStatus status,
		@Size(max = 2000) String resolutionNote,
		List<@NotNull @Valid MatchRankingRequest> overrideRankings,
		List<@NotNull @Valid WinnerShareRequest> winnerShares
) {
}
