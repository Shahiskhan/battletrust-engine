package com.bte.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WithdrawalDecisionRequest(
		@NotNull Boolean approve,
		@Size(max = 1000) String decisionNote
) {
}
