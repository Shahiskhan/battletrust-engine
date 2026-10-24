package com.bte.service;

import com.bte.dto.LeaderboardEntryResponse;
import com.bte.entity.PlayerCategoryStats;
import com.bte.entity.User;
import com.bte.enums.GameCategory;
import com.bte.enums.Role;
import com.bte.repository.PlayerCategoryStatsRepository;
import com.bte.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PlayerCategoryStatsRepository categoryStatsRepository;

	private LeaderboardService leaderboardService;

	@BeforeEach
	void setUp() {
		leaderboardService = new LeaderboardService(userRepository, categoryStatsRepository);
	}

	@Test
	void ranksOverallLeaderboardByRequestedSort() {
		User first = User.builder()
				.id(1L)
				.name("Ace")
				.role(Role.ROLE_PLAYER)
				.matchesWon(12)
				.reputationScore(200)
				.winRatePercent(new BigDecimal("80.00"))
				.build();
		when(userRepository.findByRoleInAndDemoAccountFalse(any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(first)));

		Page<LeaderboardEntryResponse> page = leaderboardService.getLeaderboard(0, 20, "wins", null);

		assertEquals(1, page.getContent().getFirst().rank());
		assertEquals(12, page.getContent().getFirst().matchesWon());
		assertEquals(1L, page.getContent().getFirst().userId());
	}

	@Test
	void filtersLeaderboardByGameCategory() {
		User player = User.builder().id(3L).name("Sniper").role(Role.ROLE_PLAYER).build();
		PlayerCategoryStats stats = PlayerCategoryStats.builder()
				.user(player)
				.gameCategory(GameCategory.VALORANT)
				.matchesWon(4)
				.reputationScore(90)
				.winRatePercent(new BigDecimal("50.00"))
				.build();
		when(categoryStatsRepository.findByGameCategoryAndUserDemoAccountFalseAndUserRoleIn(
				eq(GameCategory.VALORANT), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(stats)));

		Page<LeaderboardEntryResponse> page = leaderboardService.getLeaderboard(
				0, 10, "reputation", GameCategory.VALORANT);

		assertEquals(GameCategory.VALORANT, page.getContent().getFirst().gameCategory());
		assertEquals(4, page.getContent().getFirst().matchesWon());
		assertEquals(1, page.getContent().getFirst().rank());
	}
}
