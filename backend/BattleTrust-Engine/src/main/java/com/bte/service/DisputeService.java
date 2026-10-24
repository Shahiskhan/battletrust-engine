package com.bte.service;

import com.bte.dto.DisputeClaimRequest;
import com.bte.dto.DisputeResolveRequest;
import com.bte.dto.DisputeResponse;
import com.bte.dto.EscrowSettlementRequest;
import com.bte.dto.EscrowSettlementResponse;
import com.bte.dto.MatchRankingRequest;
import com.bte.dto.WinnerShareRequest;
import com.bte.entity.Dispute;
import com.bte.entity.MatchRanking;
import com.bte.entity.MatchResult;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.enums.DisputeStatus;
import com.bte.enums.VerificationStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.DisputeRepository;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import com.bte.service.escrow.EscrowSettlementService;
import com.bte.service.media.MediaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DisputeService {
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired private ScoreAuditService scoreAudit;

	private final DisputeRepository disputeRepository;
	private final MatchResultRepository matchResultRepository;
	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final MediaService mediaService;
	private final MatchResultService matchResultService;
	private final EscrowSettlementService settlementService;

	public DisputeService(
			DisputeRepository disputeRepository,
			MatchResultRepository matchResultRepository,
			RegistrationRepository registrationRepository,
			TournamentRepository tournamentRepository,
			UserRepository userRepository,
			MediaService mediaService,
			MatchResultService matchResultService,
			EscrowSettlementService settlementService) {
		this.disputeRepository = disputeRepository;
		this.matchResultRepository = matchResultRepository;
		this.registrationRepository = registrationRepository;
		this.tournamentRepository = tournamentRepository;
		this.userRepository = userRepository;
		this.mediaService = mediaService;
		this.matchResultService = matchResultService;
		this.settlementService = settlementService;
	}

	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public DisputeResponse fileClaim(
			String reporterEmail, DisputeClaimRequest request, MultipartFile screenshot) {
		var candidate=matchResultRepository.findById(request.matchResultId()).orElseThrow(()->new ResourceNotFoundException("Result not found"));
        tournamentRepository.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();
        MatchResult result = matchResultRepository.findByIdForUpdate(request.matchResultId())
				.orElseThrow(() -> new ResourceNotFoundException("Match result was not found"));
		em.refresh(result,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        Tournament tournament = result.getTournament();
		User reporter = userRepository.findByEmail(reporterEmail).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
		if (!registrationRepository.existsByTournamentIdAndOwnerUserIdAndPaymentStatus(tournament.getId(), reporter.getId(), com.bte.enums.PaymentStatus.PAID)
				&& registrationRepository.findMemberEntries(reporter.getId()).stream().noneMatch(r -> r.getTournament().getId().equals(tournament.getId()) && r.getPaymentStatus()==com.bte.enums.PaymentStatus.PAID)) {
			throw new com.bte.exception.UnauthorizedException(
					"Only a paid participant in this room can dispute this match result");
		}
		if (result.isDisputeWindowClosed() || !result.getDisputeDeadline().isAfter(LocalDateTime.now())
				|| result.getVerificationStatus() != VerificationStatus.YELLOW_TICK_UNVERIFIED) {
			throw new BadRequestException("The dispute window for this result is closed");
		}
		if (screenshot == null || screenshot.isEmpty()) {
			throw new BadRequestException("Evidence screenshot is required");
		}
		String screenshotUrl = mediaService.uploadFile(screenshot, "dispute-evidence");
		Dispute dispute = disputeRepository.save(Dispute.builder()
				.tournament(tournament)
				.matchResult(result)
				.reporterEmail(reporterEmail)
				.reason(request.reason().trim())
				.screenshotUrl(screenshotUrl)
				.status(DisputeStatus.PENDING)
				.build());
		return toResponse(dispute);
	}

	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public DisputeResolution resolve(Long disputeId, DisputeResolveRequest request, String adminEmail) {
		Dispute initialDispute = disputeRepository.findById(disputeId)
				.orElseThrow(() -> new ResourceNotFoundException("Dispute was not found"));
		tournamentRepository.findByIdForUpdate(initialDispute.getTournament().getId()).orElseThrow();
        MatchResult lockedResult = initialDispute.getMatchResult() == null
				? null
				: matchResultRepository.findByIdForUpdate(initialDispute.getMatchResult().getId())
						.orElseThrow(() -> new ResourceNotFoundException("Match result was not found"));
		Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
				.orElseThrow(() -> new ResourceNotFoundException("Dispute was not found"));
		em.refresh(dispute,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(lockedResult!=null)em.refresh(lockedResult,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (dispute.getMatchResult() != null
				&& (lockedResult == null || !dispute.getMatchResult().getId().equals(lockedResult.getId()))) {
			throw new BadRequestException("The dispute match result changed; retry the resolution");
		}
		if (dispute.getStatus() != DisputeStatus.PENDING) {
			throw new BadRequestException("Dispute has already been resolved");
		}
		if (request.status() != DisputeStatus.RESOLVED && request.status() != DisputeStatus.REJECTED) {
			throw new BadRequestException("Dispute status must be RESOLVED or REJECTED");
		}
		User admin = userRepository.findByEmail(adminEmail)
				.orElseThrow(() -> new ResourceNotFoundException("Admin account was not found"));
		dispute.setStatus(request.status());
		dispute.setResolutionNote(request.resolutionNote() == null ? null : request.resolutionNote().trim());
		dispute.setReviewedBy(admin);
		dispute.setResolvedAt(LocalDateTime.now());

		if (request.status() == DisputeStatus.RESOLVED) {
			MatchResult result = lockedResult;
			if (result == null) {
				throw new BadRequestException("Dispute is not linked to a match result");
			}
			Tournament tournament = tournamentRepository.findByIdForUpdate(dispute.getTournament().getId())
					.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
			List<MatchRankingRequest> rankings = request.overrideRankings() == null
					|| request.overrideRankings().isEmpty()
					? matchResultService.toResponse(result).rankings()
                    : request.overrideRankings();
			validateRankings(tournament.getId(), rankings);
			var players=RoundScoreRules.validate(tournament,registrationRepository.findByTournamentId(tournament.getId()),rankings);
            result.setRankings(RoundScoreRules.rankings(rankings));if(tournament.getPlannedRounds()>0){result.getPlayerScores().clear();result.getPlayerScores().addAll(players);}result.setRevision(result.getRevision()+1);scoreAudit.record(result,adminEmail,"Admin dispute correction: "+request.resolutionNote());
            result.setVerificationStatus(VerificationStatus.GREEN_TICK_VERIFIED);
			result.setDisputeWindowClosed(true);
			matchResultRepository.flush();
			for (Dispute otherDispute : disputeRepository.findByMatchResultIdAndStatus(
					result.getId(), DisputeStatus.PENDING)) {
				otherDispute.setStatus(DisputeStatus.REJECTED);
				otherDispute.setResolutionNote("Closed after another claim for this result was resolved");
				otherDispute.setReviewedBy(admin);
				otherDispute.setResolvedAt(LocalDateTime.now());
			}
			// Score adjudication never releases funds. The organizer must declare
			// winners and admin must separately review/release that declaration.
		} else if (dispute.getMatchResult() != null) {
			MatchResult result = lockedResult;
			disputeRepository.saveAndFlush(dispute);
			if (!result.getDisputeDeadline().isAfter(LocalDateTime.now())
					&& disputeRepository.countByMatchResultIdAndStatus(
							result.getId(), DisputeStatus.PENDING) == 0) {
				result.setDisputeWindowClosed(true);
				result.setVerificationStatus(VerificationStatus.GREEN_TICK_VERIFIED);
				if (result.getTournament().getPlannedRounds()==0 && result.getTournament().getStatus() == com.bte.enums.TournamentStatus.UNDER_REVIEW) {
					result.getTournament().setStatus(com.bte.enums.TournamentStatus.LIVE);
				}
			}
		}
		return new DisputeResolution(toResponse(dispute), null);
	}

	public DisputeResponse toResponse(Dispute dispute) {
		return new DisputeResponse(
				dispute.getId(),
				dispute.getTournament().getId(),
				dispute.getMatchResult() == null ? null : dispute.getMatchResult().getId(),
				dispute.getReporterEmail(),
				dispute.getReason(),
				dispute.getScreenshotUrl(),
				dispute.getStatus(),
				dispute.getResolutionNote(),
				dispute.getCreatedAt(),
				dispute.getResolvedAt());
	}

	private void validateRankings(Long tournamentId, List<MatchRankingRequest> rankings) {
		if (rankings == null || rankings.isEmpty()) {
			throw new BadRequestException("At least one ranking is required");
		}
		Set<Long> playerIds = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		Set<Long> registeredCaptainIds = new HashSet<>();
		registrationRepository.findByTournamentId(tournamentId).stream()
				.filter(r -> r.getPaymentStatus() == com.bte.enums.PaymentStatus.PAID && r.getOwnerUserId() != null)
				.forEach(r -> { registeredCaptainIds.add(r.getOwnerUserId()); if (r.getGuestAccountId() != null) registeredCaptainIds.add(r.getGuestAccountId()); });
		for (MatchRankingRequest ranking : rankings) {
			if (ranking == null || ranking.playerUserId() == null || ranking.rank() == null
					|| ranking.kills() == null || ranking.playerUserId() < 1
					|| ranking.rank() < 1 || ranking.kills() < 0
					|| !registeredCaptainIds.contains(ranking.playerUserId())
					|| !playerIds.add(ranking.playerUserId()) || !ranks.add(ranking.rank())) {
				throw new BadRequestException(
						"Rankings must contain unique registered captains and positive ranks");
			}
		}
	}

	private void validatePayoutRanks(
			List<MatchRankingRequest> rankings, List<WinnerShareRequest> winnerShares) {
		Map<Long, Integer> actualRanks = rankings.stream()
				.collect(Collectors.toMap(MatchRankingRequest::playerUserId, MatchRankingRequest::rank));
		for (WinnerShareRequest share : winnerShares) {
			if (share == null || !share.rank().equals(actualRanks.get(share.userId()))) {
				throw new BadRequestException("Winner shares must match the resolved player rankings");
			}
		}
	}

	public record DisputeResolution(DisputeResponse dispute, EscrowSettlementResponse settlement) {
	}
}
