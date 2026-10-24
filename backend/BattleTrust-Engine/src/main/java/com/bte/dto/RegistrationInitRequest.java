package com.bte.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegistrationInitRequest(
		@NotNull @Positive Long tournamentId,
		@NotBlank @Size(max = 100) String teamName,
		@NotBlank @Size(max = 100) String captainName,
		@NotBlank @Email @Size(max = 254) String captainEmail,
		@NotBlank @Size(max = 32) String whatsappNumber,
		@NotNull @Size(min = 1, max = 4) List<@NotNull @Valid SquadMemberRequest> squadMembers,
        @Size(max=36) String roomInviteToken
) {
}
