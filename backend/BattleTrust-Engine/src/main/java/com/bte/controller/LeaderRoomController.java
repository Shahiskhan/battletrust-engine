package com.bte.controller;

import com.bte.dto.RoomCredentialsRequest;
import com.bte.service.RoomCredentialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/leader/tournaments")
public class LeaderRoomController {

	private final RoomCredentialService roomCredentialService;

	public LeaderRoomController(RoomCredentialService roomCredentialService) {
		this.roomCredentialService = roomCredentialService;
	}

	@PostMapping("/{tournamentId}/room-credentials")
	public ResponseEntity<Void> setCredentials(
			@PathVariable @Min(1) Long tournamentId,
			@Valid @RequestBody RoomCredentialsRequest request,
			Authentication authentication) {
		roomCredentialService.setCredentials(tournamentId, authentication.getName(), request);
		return ResponseEntity.noContent().build();
	}
}
