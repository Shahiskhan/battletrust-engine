package com.bte.service;

import com.bte.dto.ChallengeCreateRequest;
import com.bte.dto.ChallengeResponse;
import com.bte.entity.Challenge;
import com.bte.entity.User;
import com.bte.enums.ChallengeRoomStatus;
import com.bte.enums.ChallengeStatus;
import com.bte.enums.GameCategory;
import com.bte.enums.Role;
import com.bte.exception.BadRequestException;
import com.bte.repository.ChallengeRepository;
import com.bte.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

	@Mock
	private ChallengeRepository challengeRepository;
	@Mock
	private UserRepository userRepository;

	private ChallengeService challengeService;
	private User challenger;
	private User opponent;

	@BeforeEach
	void setUp() {
		challengeService = new ChallengeService(challengeRepository, userRepository, 3, 50);
		challenger = User.builder()
				.id(10L)
				.name("Challenger")
				.email("challenger@example.com")
				.role(Role.ROLE_PLAYER)
				.matchesPlayed(5)
				.reputationScore(80)
				.build();
		opponent = User.builder()
				.id(20L)
				.name("Opponent")
				.email("opponent@example.com")
				.role(Role.ROLE_PLAYER)
				.build();
	}

	@Test
	void createsPendingChallengeWithSubRoom() {
		when(userRepository.findByEmail("challenger@example.com")).thenReturn(Optional.of(challenger));
		when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(challenger));
		when(userRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(opponent));
		when(challengeRepository.existsOpenChallengeBetween(any(), any(), any())).thenReturn(false);
		when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> {
			Challenge challenge = invocation.getArgument(0);
			challenge.setId(44L);
			return challenge;
		});

		ChallengeResponse response = challengeService.create(
				"challenger@example.com",
				new ChallengeCreateRequest(20L, GameCategory.FREE_FIRE, "1v1 now"));

		assertEquals(44L, response.id());
		assertEquals(ChallengeStatus.PENDING, response.status());
		assertEquals(ChallengeRoomStatus.PENDING, response.roomStatus());
		assertNotNull(response.roomId());
		assertNull(response.roomPassword());
		ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
		verify(challengeRepository).save(captor.capture());
		assertEquals(ChallengeRoomStatus.PENDING, captor.getValue().getRoom().getStatus());
	}

	@Test
	void rejectsChallengerBelowScoreThreshold() {
		challenger.setMatchesPlayed(1);
		challenger.setReputationScore(10);
		when(userRepository.findByEmail("challenger@example.com")).thenReturn(Optional.of(challenger));
		when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(challenger));
		when(userRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(opponent));

		assertThrows(BadRequestException.class, () -> challengeService.create(
				"challenger@example.com",
				new ChallengeCreateRequest(20L, GameCategory.PUBG, null)));
	}
}
