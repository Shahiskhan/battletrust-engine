package com.bte.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record MatchResultSubmitRequest(
		@NotNull @Positive Long tournamentId,
		@NotNull @Min(1) Integer matchNumber,
		@NotEmpty List<@NotNull @Valid MatchRankingRequest> rankings
) {
}
