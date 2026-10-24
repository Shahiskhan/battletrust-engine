package com.bte.controller;

import com.bte.dto.LeaderboardEntryResponse;
import com.bte.enums.GameCategory;
import com.bte.service.LeaderboardService;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final LeaderboardService leaderboardService;

	public UserController(LeaderboardService leaderboardService) {
		this.leaderboardService = leaderboardService;
	}

	@GetMapping("/leaderboard")
	public Page<LeaderboardEntryResponse> leaderboard(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size,
			@RequestParam(defaultValue = "reputation") String sortBy,
			@RequestParam(required = false) GameCategory gameCategory) {
		return leaderboardService.getLeaderboard(page, size, sortBy, gameCategory);
	}
}
