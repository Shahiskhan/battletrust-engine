package com.bte.service;

import com.bte.dto.LeaderboardEntryResponse;
import com.bte.entity.PlayerCategoryStats;
import com.bte.entity.User;
import com.bte.enums.GameCategory;
import com.bte.enums.Role;
import com.bte.exception.BadRequestException;
import com.bte.repository.PlayerCategoryStatsRepository;
import com.bte.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LeaderboardService {

	private static final List<Role> PUBLIC_ROLES = List.of(Role.ROLE_PLAYER, Role.ROLE_LEADER);
	private static final String SORT_WINS = "wins";
	private static final String SORT_REPUTATION = "reputation";

	private final UserRepository userRepository;
	private final PlayerCategoryStatsRepository categoryStatsRepository;

	public LeaderboardService(
			UserRepository userRepository,
			PlayerCategoryStatsRepository categoryStatsRepository) {
		this.userRepository = userRepository;
		this.categoryStatsRepository = categoryStatsRepository;
	}

	@Transactional(readOnly = true)
	public Page<LeaderboardEntryResponse> getLeaderboard(
			int page, int size, String sortBy, GameCategory gameCategory) {
		validatePage(page, size);
		PageRequest pageRequest = PageRequest.of(page, size, sort(sortBy));
		if (gameCategory == null) {
			Page<User> users = userRepository.findByRoleInAndDemoAccountFalse(PUBLIC_ROLES, pageRequest);
			List<LeaderboardEntryResponse> entries = new ArrayList<>();
			int rank = (int) pageRequest.getOffset() + 1;
			for (User user : users.getContent()) {
				entries.add(toOverallEntry(user, rank++));
			}
			return new PageImpl<>(entries, users.getPageable(), users.getTotalElements());
		}
		Page<PlayerCategoryStats> statsPage = categoryStatsRepository
				.findByGameCategoryAndUserDemoAccountFalseAndUserRoleIn(gameCategory, PUBLIC_ROLES, pageRequest);
		List<LeaderboardEntryResponse> entries = new ArrayList<>();
		int rank = (int) pageRequest.getOffset() + 1;
		for (PlayerCategoryStats stats : statsPage.getContent()) {
			entries.add(toCategoryEntry(stats, rank++));
		}
		return new PageImpl<>(entries, statsPage.getPageable(), statsPage.getTotalElements());
	}

	private LeaderboardEntryResponse toOverallEntry(User user, int rank) {
		return new LeaderboardEntryResponse(
				rank,
				user.getId(),
				user.getName(),
				user.getProfilePictureUrl(),
				null,
				user.getMatchesPlayed(),
				user.getMatchesWon(),
				user.getTotalKills(),
				user.getReputationScore(),
				user.getWinRatePercent());
	}

	private LeaderboardEntryResponse toCategoryEntry(PlayerCategoryStats stats, int rank) {
		User user = stats.getUser();
		return new LeaderboardEntryResponse(
				rank,
				user.getId(),
				user.getName(),
				user.getProfilePictureUrl(),
				stats.getGameCategory(),
				stats.getMatchesPlayed(),
				stats.getMatchesWon(),
				stats.getTotalKills(),
				stats.getReputationScore(),
				stats.getWinRatePercent());
	}

	private Sort sort(String sortBy) {
		String normalized = sortBy == null ? SORT_REPUTATION : sortBy.trim().toLowerCase(Locale.ROOT);
		String property = switch (normalized) {
			case SORT_WINS, "matches_won", "total_wins" -> "matchesWon";
			case SORT_REPUTATION, "reputation_score" -> "reputationScore";
			default -> throw new BadRequestException(
					"Leaderboard sort must be wins or reputation");
		};
		return Sort.by(Sort.Order.desc(property), Sort.Order.desc("id"));
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
		}
	}
}
