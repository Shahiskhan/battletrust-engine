package com.bte.controller;

import com.bte.dto.ChallengeCreateRequest;
import com.bte.dto.ChallengeResponse;
import com.bte.service.ChallengeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

	private final ChallengeService challengeService;

	public ChallengeController(ChallengeService challengeService) {
		this.challengeService = challengeService;
	}

	@PostMapping("/create")
	public ResponseEntity<ChallengeResponse> create(
			@Valid @RequestBody ChallengeCreateRequest request,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(challengeService.create(authentication.getName(), request));
	}

	@GetMapping("/{challengeId}")
	public ChallengeResponse get(
			@PathVariable @Min(1) Long challengeId,
			Authentication authentication) {
		return challengeService.getById(challengeId, authentication.getName());
	}

	@PostMapping("/{challengeId}/accept")
	public ChallengeResponse accept(
			@PathVariable @Min(1) Long challengeId,
			Authentication authentication) {
		return challengeService.accept(challengeId, authentication.getName());
	}

	@PostMapping("/{challengeId}/decline")
	public ChallengeResponse decline(
			@PathVariable @Min(1) Long challengeId,
			Authentication authentication) {
		return challengeService.decline(challengeId, authentication.getName());
	}
}
