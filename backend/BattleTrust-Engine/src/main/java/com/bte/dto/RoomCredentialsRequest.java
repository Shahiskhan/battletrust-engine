package com.bte.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomCredentialsRequest(
		@NotBlank @Size(max = 100) String roomId,
		@NotBlank @Size(max = 255) String roomPassword
) {
}
