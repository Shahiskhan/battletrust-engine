package com.bte.service;

import com.bte.dto.TournamentCreateRequest;
import com.bte.dto.TournamentResponse;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.enums.TournamentStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import com.bte.service.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Service
public class LeaderTournamentService {

	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final MediaService mediaService;
	@org.springframework.beans.factory.annotation.Autowired
	private StakingSettingsService stakingSettings;
	@org.springframework.beans.factory.annotation.Autowired
	private com.bte.service.escrow.OrganizerStakeService stakes;
	@org.springframework.beans.factory.annotation.Autowired
	private FinancialPolicyService finance;
    @org.springframework.beans.factory.annotation.Autowired private TournamentRoundService rounds;
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<VoucherService> voucherService;

	public LeaderTournamentService(
			TournamentRepository tournamentRepository,
			UserRepository userRepository,
			MediaService mediaService) {
		this.tournamentRepository = tournamentRepository;
		this.userRepository = userRepository;
		this.mediaService = mediaService;
	}

	@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	@PreAuthorize("hasAuthority('ROLE_ORGANIZER')")
	public TournamentResponse create(
			String leaderEmail, TournamentCreateRequest request, MultipartFile logo) {
		if(request.entryFeePkr()==null || request.entryFeePkr().compareTo(ONE_HUNDRED)<0)throw new BadRequestException("Entry fee must be at least Rs. 100");
		if (request.startTime().isBefore(java.time.LocalDateTime.now())) {
			throw new BadRequestException("Tournament start time must be in the future");
		}
		BigDecimal distributionTotal = request.winnersSharePercent()
				.add(request.leaderSharePercent())
				.add(request.systemSharePercent());
		if (distributionTotal.compareTo(ONE_HUNDRED) != 0) {
			throw new BadRequestException("Prize distribution percentages must total 100");
		}
		var financialPolicy = finance.locked();
		if (request.systemSharePercent().compareTo(financialPolicy.getPlatformFeePercent()) != 0) {
			throw new BadRequestException("Platform fee policy changed. Refresh the creation form");
		}
		User leader = userRepository.findByEmail(leaderEmail)
				.orElseThrow(() -> new ResourceNotFoundException("Leader account was not found"));
		int squadSize = request.squadSize() == null ? 4 : request.squadSize();
		if (request.maxPlayers() % squadSize != 0 || request.maxPlayers() / squadSize > 25)
			throw new BadRequestException("Room capacity must be a multiple of squad size with at most 25 slots");
		var policy = stakingSettings.locked();
		if (request.stakingVersion() != null && request.stakingVersion() != policy.getVersion())
			throw new com.bte.exception.DuplicateResourceException("Creation policy changed. Review the refreshed stake requirement and submit again");

		String logoUrl = logo == null || logo.isEmpty()
				? null
				: mediaService.uploadFile(logo, "tournament-logos");
		Tournament tournament = Tournament.builder()
				.automaticPayoutsEnabled(financialPolicy.isAutomaticPayoutsEnabled())
				.squadSize(squadSize)
				.title(request.title().trim())
				.gameType(request.gameType().trim())
				.gameCategory(request.gameCategory())
				.tournamentLogoUrl(logoUrl)
				.rules(request.rules())
				.leader(leader)
				.entryFeePkr(request.entryFeePkr())
				.maxPlayers(request.maxPlayers())
				.startTime(request.startTime())
				.winnersSharePercent(request.winnersSharePercent())
				.leaderSharePercent(request.leaderSharePercent())
				.systemSharePercent(request.systemSharePercent())
				.visibility(request.visibility()==null?"PUBLIC":request.visibility()).roomInviteToken(java.util.UUID.randomUUID().toString())
.status(TournamentStatus.UPCOMING)
				.build();
		tournamentRepository.save(tournament);
        if(request.roundMaps()!=null)rounds.configure(tournament,request.roundMaps());
		stakes.lock(tournament, policy);
		voucherService.getObject().prepareInventory(tournament);
		return toResponse(tournament);
	}

	public TournamentResponse toResponse(Tournament tournament) {
		return new TournamentResponse(
				tournament.getId(),
				tournament.getTitle(),
				tournament.getGameType(),
				tournament.getGameCategory(),
				tournament.getTournamentLogoUrl(),
				tournament.getRules(),
				tournament.getLeader().getId(),
				tournament.getLeader().getName(),
				tournament.getEntryFeePkr(),
				tournament.getMaxPlayers(),
				tournament.getStartTime(),
				tournament.getWinnersSharePercent(),
				tournament.getLeaderSharePercent(),
				tournament.getSystemSharePercent(),
				tournament.getStatus(),
				tournament.getCreatedAt(), tournament.getSquadSize(), tournament.getOrganizerStakeAmount(), tournament.getOrganizerStakeStatus(), tournament.isAutomaticPayoutsEnabled(),tournament.getVisibility(),tournament.isRoomChatEnabled(),tournament.getPlannedRounds(),tournament.isRegistrationOpen());
	}
}
