package com.bte.service;

import com.bte.entity.MatchRanking;
import com.bte.entity.MatchResult;
import com.bte.entity.PlayerCategoryStats;
import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.enums.GameCategory;
import com.bte.enums.Role;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.PlayerCategoryStatsRepository;
import com.bte.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerPerformanceServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private MatchResultRepository matchResultRepository;
	@Mock
	private PlayerCategoryStatsRepository categoryStatsRepository;

	private PlayerPerformanceService performanceService;
	private User winner;
	private User participant;
	private Tournament tournament;

	@BeforeEach
	void setUp() {
		performanceService = new PlayerPerformanceService(
				userRepository, matchResultRepository, categoryStatsRepository);
		winner = User.builder()
				.id(1L)
				.name("Winner")
				.email("winner@example.com")
				.role(Role.ROLE_PLAYER)
				.build();
		participant = User.builder()
				.id(2L)
				.name("Player")
				.email("player@example.com")
				.role(Role.ROLE_PLAYER)
				.build();
		tournament = Tournament.builder()
				.id(8L)
				.gameCategory(GameCategory.PUBG)
				.build();
	}

	@Test
	void incrementsWinsKillsAndReputationOnSettlement() {
		when(matchResultRepository.findByTournamentIdOrderByMatchNumberAsc(8L)).thenReturn(List.of(
				MatchResult.builder()
						.rankings(List.of(
								new MatchRanking(1L, 1, 8, "Alpha"),
								new MatchRanking(2L, 2, 3, "Bravo")))
						.build()));
		
		when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(winner));
		when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(participant));
		when(categoryStatsRepository.findByUserIdAndGameCategoryForUpdate(any(), eq(GameCategory.PUBG)))
				.thenReturn(Optional.empty());
		when(categoryStatsRepository.save(any(PlayerCategoryStats.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		performanceService.applyTournamentSettlement(
				tournament,
				List.of(
						Registration.builder().ownerUserId(1L).email("winner@example.com").captainEmail("winner@example.com").build(),
						Registration.builder().ownerUserId(2L).email("player@example.com").captainEmail("player@example.com").build()),
				Set.of(1L));

		assertEquals(1, winner.getMatchesPlayed());
		assertEquals(1, winner.getMatchesWon());
		assertEquals(8, winner.getTotalKills());
		assertEquals(33, winner.getReputationScore());
		assertEquals(new BigDecimal("100.00"), winner.getWinRatePercent());
		assertEquals(1, participant.getMatchesPlayed());
		assertEquals(0, participant.getMatchesWon());
		assertEquals(3, participant.getTotalKills());
		assertEquals(8, participant.getReputationScore());
		assertEquals(new BigDecimal("0.00"), participant.getWinRatePercent());
	}
}

