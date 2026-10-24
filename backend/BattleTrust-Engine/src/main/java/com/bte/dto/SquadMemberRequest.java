package com.bte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SquadMemberRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 100) String inGameId
) {
}
