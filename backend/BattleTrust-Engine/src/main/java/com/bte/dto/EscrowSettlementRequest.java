package com.bte.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EscrowSettlementRequest(
		@Valid @NotEmpty List<WinnerShareRequest> winners,
		@Size(max = 1000) String overrideReason
) {
}
