package com.bte.dto;

import com.bte.enums.GameCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChallengeCreateRequest(
		@NotNull @Positive Long opponentUserId,
		@NotNull GameCategory gameCategory,
		@Size(max = 500) String message
) {
}
