package com.bte.service;

import com.bte.dto.ChallengeCreateRequest;
import com.bte.dto.ChallengeResponse;
import com.bte.entity.Challenge;
import com.bte.entity.ChallengeRoom;
import com.bte.entity.User;
import com.bte.enums.ChallengeRoomStatus;
import com.bte.enums.ChallengeStatus;
import com.bte.enums.Role;
import com.bte.exception.BadRequestException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.ChallengeRepository;
import com.bte.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ChallengeService {

	private static final Set<Role> ELIGIBLE_ROLES = Set.of(Role.ROLE_PLAYER, Role.ROLE_LEADER);
	private static final List<ChallengeStatus> OPEN_STATUSES = List.of(
			ChallengeStatus.PENDING, ChallengeStatus.ACCEPTED);
	private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final ChallengeRepository challengeRepository;
	private final UserRepository userRepository;
	private final int minMatchesPlayed;
	private final int minReputationScore;

	public ChallengeService(
			ChallengeRepository challengeRepository,
			UserRepository userRepository,
			@Value("${app.challenges.min-matches-played:3}") int minMatchesPlayed,
			@Value("${app.challenges.min-reputation-score:50}") int minReputationScore) {
		this.challengeRepository = challengeRepository;
		this.userRepository = userRepository;
		this.minMatchesPlayed = minMatchesPlayed;
		this.minReputationScore = minReputationScore;
	}

	@Transactional
	@PreAuthorize("hasAnyAuthority('ROLE_PLAYER', 'ROLE_LEADER')")
	public ChallengeResponse create(String challengerEmail, ChallengeCreateRequest request) {
		User challengerIdentity = userRepository.findByEmail(normalizeEmail(challengerEmail))
				.orElseThrow(() -> new ResourceNotFoundException("Player account was not found"));
		if (request.opponentUserId().equals(challengerIdentity.getId())) {
			throw new BadRequestException("A player cannot challenge themselves");
		}
		Long firstId = Math.min(challengerIdentity.getId(), request.opponentUserId());
		Long secondId = Math.max(challengerIdentity.getId(), request.opponentUserId());
		User first = userRepository.findByIdForUpdate(firstId)
				.orElseThrow(() -> new ResourceNotFoundException("Opponent account was not found"));
		User second = userRepository.findByIdForUpdate(secondId)
				.orElseThrow(() -> new ResourceNotFoundException("Opponent account was not found"));
		User challenger = first.getId().equals(challengerIdentity.getId()) ? first : second;
		User opponent = first.getId().equals(challengerIdentity.getId()) ? second : first;
		assertEligibleRole(challenger);
		assertScoreThreshold(challenger);
		assertEligibleRole(opponent);

		if (challengeRepository.existsOpenChallengeBetween(firstId, secondId, OPEN_STATUSES)) {
			throw new BadRequestException("An open challenge already exists between these players");
		}

		Challenge challenge = Challenge.builder()
				.challenger(challenger)
				.opponent(opponent)
				.gameCategory(request.gameCategory())
				.message(request.message() == null || request.message().isBlank()
						? null
						: request.message().trim())
				.status(ChallengeStatus.PENDING)
				.room(ChallengeRoom.builder()
						.roomId(generateRoomId())
						.roomPassword(generateRoomPassword())
						.status(ChallengeRoomStatus.PENDING)
						.build())
				.build();
		return toResponse(challengeRepository.save(challenge));
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyAuthority('ROLE_PLAYER', 'ROLE_LEADER', 'ROLE_ADMIN')")
	public ChallengeResponse getById(Long challengeId, String requesterEmail) {
		Challenge challenge = challengeRepository.findById(challengeId)
				.orElseThrow(() -> new ResourceNotFoundException("Challenge was not found"));
		assertParticipant(challenge, requesterEmail);
		return toResponse(challenge);
	}

	@Transactional
	@PreAuthorize("hasAnyAuthority('ROLE_PLAYER', 'ROLE_LEADER')")
	public ChallengeResponse accept(Long challengeId, String opponentEmail) {
		Challenge challenge = loadOpenChallenge(challengeId);
		assertOpponent(challenge, opponentEmail);
		if (challenge.getStatus() != ChallengeStatus.PENDING) {
			throw new BadRequestException("Only pending challenges can be accepted");
		}
		challenge.setStatus(ChallengeStatus.ACCEPTED);
		challenge.getRoom().setStatus(ChallengeRoomStatus.ACCEPTED);
		challenge.setRespondedAt(LocalDateTime.now());
		return toResponse(challenge);
	}

	@Transactional
	@PreAuthorize("hasAnyAuthority('ROLE_PLAYER', 'ROLE_LEADER')")
	public ChallengeResponse decline(Long challengeId, String opponentEmail) {
		Challenge challenge = loadOpenChallenge(challengeId);
		assertOpponent(challenge, opponentEmail);
		if (challenge.getStatus() != ChallengeStatus.PENDING) {
			throw new BadRequestException("Only pending challenges can be declined");
		}
		challenge.setStatus(ChallengeStatus.DECLINED);
		challenge.getRoom().setStatus(ChallengeRoomStatus.CLOSED);
		challenge.setRespondedAt(LocalDateTime.now());
		return toResponse(challenge);
	}

	private Challenge loadOpenChallenge(Long challengeId) {
		return challengeRepository.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new ResourceNotFoundException("Challenge was not found"));
	}

	private void assertOpponent(Challenge challenge, String opponentEmail) {
		if (!challenge.getOpponent().getEmail().equalsIgnoreCase(opponentEmail)) {
			throw new UnauthorizedException("Only the challenged player can respond to this challenge");
		}
	}

	private void assertParticipant(Challenge challenge, String requesterEmail) {
		boolean participant = challenge.getChallenger().getEmail().equalsIgnoreCase(requesterEmail)
				|| challenge.getOpponent().getEmail().equalsIgnoreCase(requesterEmail);
		if (!participant) {
			throw new UnauthorizedException("Only challenge participants can view this challenge");
		}
	}

	private void assertEligibleRole(User user) {
		if (!ELIGIBLE_ROLES.contains(user.getRole())) {
			throw new BadRequestException("Only registered players can participate in 1-on-1 challenges");
		}
	}

	private void assertScoreThreshold(User user) {
		if (user.getMatchesPlayed() < minMatchesPlayed || user.getReputationScore() < minReputationScore) {
			throw new BadRequestException(
					"A minimum of " + minMatchesPlayed + " matches and a reputation score of "
							+ minReputationScore + " is required to issue a challenge");
		}
	}

	private ChallengeResponse toResponse(Challenge challenge) {
		ChallengeRoom room = challenge.getRoom();
		boolean revealCredentials = challenge.getStatus() == ChallengeStatus.ACCEPTED
				&& room != null
				&& room.getStatus() == ChallengeRoomStatus.ACCEPTED;
		return new ChallengeResponse(
				challenge.getId(),
				challenge.getChallenger().getId(),
				challenge.getChallenger().getName(),
				challenge.getOpponent().getId(),
				challenge.getOpponent().getName(),
				challenge.getGameCategory(),
				challenge.getMessage(),
				challenge.getStatus(),
				room == null ? null : room.getRoomId(),
				revealCredentials ? room.getRoomPassword() : null,
				room == null ? null : room.getStatus(),
				challenge.getCreatedAt(),
				challenge.getRespondedAt());
	}

	private String generateRoomId() {
		return "1V1-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
	}

	private String generateRoomPassword() {
		StringBuilder password = new StringBuilder(8);
		for (int index = 0; index < 8; index++) {
			password.append(PASSWORD_ALPHABET.charAt(SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length())));
		}
		return password.toString();
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
