package com.bte.service;

import com.bte.dto.MatchRankingRequest;
import com.bte.dto.MatchResultResponse;
import com.bte.dto.MatchResultSubmitRequest;
import com.bte.entity.MatchRanking;
import com.bte.entity.MatchResult;
import com.bte.entity.Tournament;
import com.bte.enums.TournamentStatus;
import com.bte.enums.VerificationStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.DuplicateResourceException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import com.bte.service.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MatchResultService {
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired private TournamentRoundService rounds;
    @org.springframework.beans.factory.annotation.Autowired private ScoreAuditService audit;
    @org.springframework.beans.factory.annotation.Autowired private com.bte.repository.DisputeRepository disputes;

	private static final int MAX_PROOF_IMAGES = 5;

	private final MatchResultRepository matchResultRepository;
	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final UserRepository userRepository;
	private final MediaService mediaService;

	public MatchResultService(
			MatchResultRepository matchResultRepository,
			TournamentRepository tournamentRepository,
			RegistrationRepository registrationRepository,
			UserRepository userRepository,
			MediaService mediaService) {
		this.matchResultRepository = matchResultRepository;
		this.tournamentRepository = tournamentRepository;
		this.registrationRepository = registrationRepository;
		this.userRepository = userRepository;
		this.mediaService = mediaService;
	}

	@Transactional
	public MatchResultResponse submit(
			String leaderEmail, MatchResultSubmitRequest request, List<MultipartFile> proofImages) {
		Tournament tournament = tournamentRepository.findByIdForUpdate(request.tournamentId())
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		var submitter=userRepository.findByEmail(leaderEmail).orElseThrow(()->new com.bte.exception.UnauthorizedException("Account not found"));
		if (submitter.getRole()!=com.bte.enums.Role.ROLE_ADMIN && (submitter.getRole()!=com.bte.enums.Role.ROLE_ORGANIZER || !tournament.getLeader().getEmail().equalsIgnoreCase(leaderEmail))) {
			throw new com.bte.exception.UnauthorizedException("Only the tournament leader can submit results");
		}
		if (tournament.getStatus() != TournamentStatus.LIVE) {
			throw new BadRequestException("Match results can only be submitted for live tournaments");
		}
        rounds.beforeSubmit(tournament,request.matchNumber());
		if (matchResultRepository.existsByTournamentIdAndMatchNumber(
				tournament.getId(), request.matchNumber())) {
			throw new DuplicateResourceException("A result for this match has already been submitted");
		}
		if (proofImages == null || proofImages.isEmpty() || proofImages.size() > MAX_PROOF_IMAGES) {
			throw new BadRequestException("Submit between 1 and 5 proof images");
		}
		validateRankings(tournament.getId(), request.rankings());
        if(registrationRepository.findByTournamentId(tournament.getId()).stream().anyMatch(r -> r.getPaymentStatus()==com.bte.enums.PaymentStatus.PAID && r.getSquadInviteToken()!=null && r.getSquadMembers().size()!=tournament.getSquadSize()))throw new BadRequestException("Every paid squad seat must be filled before publishing results");

		var players=RoundScoreRules.validate(tournament,registrationRepository.findByTournamentId(tournament.getId()),request.rankings());
        List<String> imageUrls = proofImages.stream()
				.map(file -> mediaService.uploadFile(file, "match-proofs"))
				.toList();
        List<MatchRanking> rankings=RoundScoreRules.rankings(request.rankings());
		MatchResult result = matchResultRepository.save(MatchResult.builder()
				.tournament(tournament)
				.matchNumber(request.matchNumber())
				.rankings(rankings)
                .playerScores(players)
				.proofImageUrls(imageUrls)
				.verificationStatus(VerificationStatus.YELLOW_TICK_UNVERIFIED)
				.disputeWindowClosed(false)
				.disputeDeadline(java.time.LocalDateTime.now().plusHours(2))
				.build());
        rounds.scored(tournament,request.matchNumber());audit.record(result,leaderEmail,"Initial organizer score submission");
		return toResponse(result);
	}

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatchResultResponse revise(Long id,String email,MatchResultSubmitRequest request,List<MultipartFile> proof,String reason){
        var candidate=matchResultRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Result not found"));
        var t=tournamentRepository.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();
        var result=matchResultRepository.findByIdForUpdate(id).orElseThrow();em.refresh(result,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var user=userRepository.findByEmail(email).orElseThrow();
        if(user.getRole()!=com.bte.enums.Role.ROLE_ADMIN && (user.getRole()!=com.bte.enums.Role.ROLE_ORGANIZER || !t.getLeader().getEmail().equalsIgnoreCase(email)))throw new org.springframework.security.access.AccessDeniedException("Only the room organizer may correct scores");
        if(t.getStatus()==TournamentStatus.COMPLETED || t.getStatus()==TournamentStatus.CANCELLED || t.isArchived() || result.isDisputeWindowClosed() || result.getVerificationStatus()!=VerificationStatus.YELLOW_TICK_UNVERIFIED || !result.getDisputeDeadline().isAfter(java.time.LocalDateTime.now()))throw new BadRequestException("Reviewed or closed results require admin dispute review");
        if(disputes.countByMatchResultIdAndStatus(id,com.bte.enums.DisputeStatus.PENDING)>0)throw new BadRequestException("Resolve the pending score dispute through admin review");
        if(!request.tournamentId().equals(t.getId()) || !request.matchNumber().equals(result.getMatchNumber()) || reason==null || reason.isBlank() || reason.length()>1000)throw new BadRequestException("Keep the original room/match and supply a correction reason");
        validateRankings(t.getId(),request.rankings());var players=RoundScoreRules.validate(t,registrationRepository.findByTournamentId(t.getId()),request.rankings());
        if(proof==null || proof.isEmpty() || proof.size()>5)throw new BadRequestException("Submit 1–5 replacement evidence images");
        result.setRankings(RoundScoreRules.rankings(request.rankings()));result.getPlayerScores().clear();result.getPlayerScores().addAll(players);
        result.setProofImageUrls(proof.stream().map(file->mediaService.uploadFile(file,"match-proofs")).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        result.setRevision(result.getRevision()+1);result.setDisputeDeadline(java.time.LocalDateTime.now().plusHours(2));audit.record(result,email,reason.trim());return toResponse(result);
    }

	private void validateRankings(Long tournamentId, List<MatchRankingRequest> rankings) {
		if(rankings==null || rankings.isEmpty() || rankings.stream().filter(r -> r!=null && Integer.valueOf(1).equals(r.rank())).count()!=1)throw new BadRequestException("Mark exactly one winning team with rank 1");
		Set<Long> users = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		Set<Long> registeredCaptains = new HashSet<>();
		registrationRepository.findByTournamentId(tournamentId).stream()
				.filter(r -> r.getPaymentStatus() == com.bte.enums.PaymentStatus.PAID && r.getOwnerUserId() != null)
				.forEach(r -> { registeredCaptains.add(r.getOwnerUserId()); if (r.getGuestAccountId() != null) registeredCaptains.add(r.getGuestAccountId()); });
		for (MatchRankingRequest ranking : rankings) {
			if (ranking == null || ranking.playerUserId() == null || ranking.rank() == null
					|| ranking.kills() == null || ranking.playerUserId() < 1
					|| ranking.rank() < 1 || ranking.kills() < 0
					|| !registeredCaptains.contains(ranking.playerUserId())) {
				throw new BadRequestException(
						"Each ranking must identify a registered tournament captain with valid rank and kills");
			}
			if (!users.add(ranking.playerUserId()) || !ranks.add(ranking.rank())) {
				throw new BadRequestException("Player IDs and ranking positions must be unique");
			}
		}
	}

	public MatchResultResponse toResponse(MatchResult result) {
		List<MatchRankingRequest> rankings = result.getRankings().stream()
				.map(ranking -> new MatchRankingRequest(
						ranking.getPlayerUserId(),
						ranking.getRank(),
						ranking.getKills(),
							ranking.getTeamName(),ranking.getPlacementPoints(),result.getPlayerScores().stream().filter(p->p.getCaptainUserId().equals(ranking.getPlayerUserId())).collect(java.util.stream.Collectors.toMap(com.bte.entity.MatchPlayerScore::getUserId,com.bte.entity.MatchPlayerScore::getKills))))
				.toList();
		return new MatchResultResponse(
				result.getId(),
				result.getTournament().getId(),
				result.getMatchNumber(),
				rankings,
				result.getProofImageUrls(),
				result.getVerificationStatus(),
				result.getSubmittedAt(),
				result.getDisputeDeadline(),result.getRevision());
	}
}
