package com.bte.controller;

import com.bte.dto.TournamentCreateRequest;
import com.bte.dto.TournamentResponse;
import com.bte.service.LeaderTournamentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/leader/tournaments")
public class LeaderTournamentController {

	private final LeaderTournamentService tournamentService;

	public LeaderTournamentController(LeaderTournamentService tournamentService) {
		this.tournamentService = tournamentService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<TournamentResponse> createTournament(
			@Valid @RequestPart("tournament") TournamentCreateRequest request,
			@RequestPart(value = "logo", required = false) MultipartFile logo,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(tournamentService.create(authentication.getName(), request, logo));
	}
}
