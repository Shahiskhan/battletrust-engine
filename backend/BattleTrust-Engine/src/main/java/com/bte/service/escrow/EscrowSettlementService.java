package com.bte.service.escrow;

import com.bte.dto.EscrowSettlementRequest;
import com.bte.dto.EscrowSettlementResponse;
import com.bte.dto.WinnerShareRequest;
import com.bte.entity.PlatformVault;
import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.entity.Wallet;
import com.bte.enums.LedgerTransactionType;
import com.bte.enums.PaymentStatus;
import com.bte.enums.TournamentStatus;
import com.bte.enums.VerificationStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.DuplicateResourceException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.PlatformVaultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import com.bte.service.PlayerPerformanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EscrowSettlementService {
    @org.springframework.beans.factory.annotation.Autowired private com.bte.repository.DisputeRepository scoreDisputes;

	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
	@org.springframework.beans.factory.annotation.Autowired
	private OrganizerStakeService organizerStakes;

	private final PlatformVaultRepository vaultRepository;
	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final MatchResultRepository matchResultRepository;
	private final WalletAccountService walletAccountService;
	private final LedgerService ledgerService;
	private final UserRepository userRepository;
	private final PlayerPerformanceService playerPerformanceService;
	private final BigDecimal winnersPercent;
	private final BigDecimal leaderPercent;
	private final BigDecimal platformPercent;

	public EscrowSettlementService(
			PlatformVaultRepository vaultRepository,
			TournamentRepository tournamentRepository,
			RegistrationRepository registrationRepository,
			MatchResultRepository matchResultRepository,
			WalletAccountService walletAccountService,
			LedgerService ledgerService,
			UserRepository userRepository,
			PlayerPerformanceService playerPerformanceService,
			@Value("${app.escrow.winners-percent:85.00}") BigDecimal winnersPercent,
			@Value("${app.escrow.leader-percent:10.00}") BigDecimal leaderPercent,
			@Value("${app.escrow.platform-percent:5.00}") BigDecimal platformPercent) {
		this.vaultRepository = vaultRepository;
		this.tournamentRepository = tournamentRepository;
		this.registrationRepository = registrationRepository;
		this.matchResultRepository = matchResultRepository;
		this.walletAccountService = walletAccountService;
		this.ledgerService = ledgerService;
		this.userRepository = userRepository;
		this.playerPerformanceService = playerPerformanceService;
		this.winnersPercent = winnersPercent;
		this.leaderPercent = leaderPercent;
		this.platformPercent = platformPercent;
		if (winnersPercent.signum() < 0 || leaderPercent.signum() < 0 || platformPercent.signum() < 0
				|| winnersPercent.add(leaderPercent).add(platformPercent).compareTo(HUNDRED) != 0) {
			throw new IllegalArgumentException("Escrow split percentages must be non-negative and total 100");
		}
	}

	@Transactional
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public EscrowSettlementResponse settle(
			Long tournamentId, EscrowSettlementRequest request, String adminEmail) {
		Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		PlatformVault vault = vaultRepository.findByTournamentIdForUpdate(tournamentId)
				.orElseThrow(() -> new ResourceNotFoundException("Tournament escrow vault was not found"));
		if (Boolean.TRUE.equals(vault.getIsDisbursed())) {
			throw new DuplicateResourceException("Tournament escrow has already been disbursed");
		}
		if (tournament.getStatus() == TournamentStatus.UPCOMING || tournament.getStatus() == TournamentStatus.CANCELLED)
			throw new BadRequestException("Only started tournaments may be settled");

		var allResults = matchResultRepository.findByTournamentIdOrderByMatchNumberAsc(tournamentId);
		if(tournament.getPlannedRounds()>0 && (allResults.size()!=tournament.getPlannedRounds() || allResults.stream().anyMatch(r -> !r.isDisputeWindowClosed() || r.getVerificationStatus()!=VerificationStatus.GREEN_TICK_VERIFIED || r.getDisputeDeadline().isAfter(java.time.LocalDateTime.now())) || scoreDisputes.findByTournamentId(tournamentId).stream().anyMatch(d -> d.getStatus()==com.bte.enums.DisputeStatus.PENDING)))throw new BadRequestException("All configured rounds must finish and clear review/disputes before settlement");
        boolean verifiedResult = !allResults.isEmpty() && allResults.stream()
				.allMatch(result -> result.getVerificationStatus() == VerificationStatus.GREEN_TICK_VERIFIED && result.isDisputeWindowClosed());
		String overrideReason = request.overrideReason() == null ? "" : request.overrideReason().trim();
		if (!verifiedResult && overrideReason.isBlank()) {
			throw new BadRequestException(
					"A green-tick verified match result or an admin override reason is required");
		}

		List<Registration> registrations = registrationRepository.findByTournamentId(tournamentId).stream().filter(r -> r.getPaymentStatus()==PaymentStatus.PAID).toList();
		if(registrations.stream().anyMatch(r -> r.getSquadInviteToken()!=null && r.getSquadMembers().size()!=r.getTournament().getSquadSize()))throw new BadRequestException("Fill every paid squad seat before settlement");
        if (registrations.isEmpty()) {
			throw new BadRequestException("A tournament needs verified paid entries before settlement");
		}
        com.bte.service.PayoutDeclarationRules.validate(tournament,registrations,allResults,request);
		int registeredPlayers = registrations.stream()
				.mapToInt(registration -> registration.getSquadMembers() == null
						|| registration.getSquadMembers().isEmpty()
						? 1
						: registration.getSquadMembers().size())
				.sum();
		BigDecimal expectedPool = tournament.getEntryFeePkr()
				.multiply(BigDecimal.valueOf(registeredPlayers))
				.setScale(2, RoundingMode.UNNECESSARY);
		BigDecimal totalPool = money(vault.getTotalCollectedPkr());
		if (expectedPool.compareTo(totalPool) != 0) {
			throw new BadRequestException("Vault total does not match paid player entries");
		}
		validateWinnerShares(request.winners());

		BigDecimal effectiveWinners = tournament.getWinnersSharePercent() == null ? winnersPercent : tournament.getWinnersSharePercent();
		BigDecimal effectiveLeader = tournament.getLeaderSharePercent() == null ? leaderPercent : tournament.getLeaderSharePercent();
		BigDecimal effectivePlatform = tournament.getSystemSharePercent() == null ? platformPercent : tournament.getSystemSharePercent();
		if (effectiveWinners.signum()<0 || effectiveLeader.signum()<0 || effectivePlatform.signum()<0 || effectiveWinners.add(effectiveLeader).add(effectivePlatform).compareTo(HUNDRED) != 0)
			throw new BadRequestException("Invalid tournament prize split");
		var distribution=PoolDistribution.calculate(totalPool,effectivePlatform,effectiveLeader);
		BigDecimal winnersPool = distribution.winners();
		BigDecimal leaderCommission = distribution.organizer();
		BigDecimal platformRevenue = distribution.platform();
		if (winnersPool.signum() <= 0) {
			throw new BadRequestException("Tournament pool is too small to distribute a winner payout");
		}
		List<WinnerShareRequest> winners = request.winners().stream()
				.map(w -> new WinnerShareRequest(registrations.stream().filter(r -> w.userId().equals(r.getGuestAccountId()))
						.map(Registration::getOwnerUserId).findFirst().orElse(w.userId()), w.rank(), w.winnersPoolPercent()))
				.sorted(Comparator.comparing(WinnerShareRequest::rank))
				.toList();
		validateWinnerShares(winners);
		if (verifiedResult && (tournament.getPlannedRounds()>0 || overrideReason.isBlank())) {
			var rankingByOwner = new java.util.HashMap<Long, Integer>();
			if(tournament.getPlannedRounds()>0){for(var row:com.bte.service.TournamentStandingsService.aggregate(allResults,registrations))rankingByOwner.put(row.captainUserId(),row.rank());}
            else for (var ranking : allResults.getLast().getRankings()) {
				Long owner = registrations.stream().filter(r -> ranking.getPlayerUserId().equals(r.getGuestAccountId()))
						.map(Registration::getOwnerUserId).findFirst().orElse(ranking.getPlayerUserId());
				rankingByOwner.put(owner, ranking.getRank());
			}
			if (winners.stream().anyMatch(w -> !w.rank().equals(rankingByOwner.get(w.userId()))))
				throw new BadRequestException("Winner payout ranks must match the verified cumulative tournament standings");
		}
		java.util.Set<Long> accountIds = new java.util.TreeSet<>();
		accountIds.add(tournament.getLeader().getId());
		for(var winner:winners) {
			var team=registrations.stream().filter(r -> winner.userId().equals(r.getOwnerUserId())).findFirst().orElseThrow(()->new BadRequestException("Winner must identify a paid squad captain"));
			SquadPayoutAllocator.split(team,winnersPool).forEach(a -> accountIds.add(a.userId()));
		}
		Map<Long, Wallet> wallets = walletAccountService.getOrCreateLockedInOrder(accountIds);
		for (WinnerShareRequest winner : winners) {
			boolean registeredCaptain = registrations.stream().anyMatch(registration ->
					winner.userId().equals(registration.getOwnerUserId()));
			if (!registeredCaptain) {
				throw new BadRequestException(
						"Winner payout recipients must be registered tournament captains");
			}
		}
		Wallet leaderWallet = wallets.get(tournament.getLeader().getId());
		BigDecimal distributed = ZERO_MONEY;
		for (int index = 0; index < winners.size(); index++) {
			WinnerShareRequest winner = winners.get(index);
			BigDecimal amount = index == winners.size() - 1
					? winnersPool.subtract(distributed)
					: percentage(winnersPool, winner.winnersPoolPercent());
			if (amount.signum() <= 0) {
				throw new BadRequestException("Tournament pool is too small for the requested winner distribution");
			}
			distributed = distributed.add(amount);
			var team=registrations.stream().filter(r -> winner.userId().equals(r.getOwnerUserId())).findFirst().orElseThrow();
			for(var allocation:SquadPayoutAllocator.split(team,amount)) {
				Wallet wallet=wallets.get(allocation.userId());
				wallet.setAvailableBalance(money(wallet.getAvailableBalance()).add(allocation.amount()));
				wallet.setTotalEarned(money(wallet.getTotalEarned()).add(allocation.amount()));
				if(allocation.amount().signum()>0)ledgerService.record(tournamentId,null,allocation.userId(),allocation.amount(),LedgerTransactionType.WINNING_PAYOUT,"tournament:"+tournamentId+":team:"+team.getId()+":member:"+allocation.memberIndex());
			}
		}

		leaderWallet.setAvailableBalance(money(leaderWallet.getAvailableBalance()).add(leaderCommission));
		leaderWallet.setTotalEarned(money(leaderWallet.getTotalEarned()).add(leaderCommission));
		if (organizerStakes != null) organizerStakes.release(tournament, leaderWallet, com.bte.enums.OrganizerStakeStatus.RELEASED);
		if (leaderCommission.signum() > 0) {
			ledgerService.record(tournamentId, null, tournament.getLeader().getId(), leaderCommission,
					LedgerTransactionType.ORGANIZER_COMMISSION,
					"tournament:" + tournamentId + ":leader");
		}
		if (platformRevenue.signum() > 0) {
			if (organizerStakes != null) organizerStakes.creditTreasury(platformRevenue);
			ledgerService.record(tournamentId, null, null, platformRevenue,
					LedgerTransactionType.PLATFORM_FEE,
					"tournament:" + tournamentId + ":platform");
		}

		vault.setWinnersShare(winnersPool);
		vault.setLeaderShare(leaderCommission);
		vault.setSystemShare(platformRevenue);
		vault.setIsDisbursed(true);
		User admin = userRepository.findByEmail(adminEmail)
				.orElseThrow(() -> new ResourceNotFoundException("Admin account was not found"));
		vault.setSettledBy(admin);
		vault.setSettledAt(java.time.Instant.now());
		vault.setSettlementReason(verifiedResult
				? (overrideReason.isBlank() ? "Settlement after verified match result" : overrideReason)
				: overrideReason);
		tournament.setStatus(TournamentStatus.COMPLETED);
		Set<Long> winnerUserIds = new HashSet<>();
		for(var winner:winners){var team=registrations.stream().filter(r -> winner.userId().equals(r.getOwnerUserId())).findFirst().orElseThrow();SquadPayoutAllocator.split(team,winnersPool).forEach(a -> winnerUserIds.add(a.userId()));}
		playerPerformanceService.applyTournamentSettlement(tournament, registrations, winnerUserIds);
		return new EscrowSettlementResponse(
				tournamentId, totalPool, winnersPool, leaderCommission, platformRevenue, true);
	}

	private void validateWinnerShares(List<WinnerShareRequest> winners) {
		if (winners == null || winners.isEmpty()) {
			throw new BadRequestException("At least one winner payout is required");
		}
		Set<Long> userIds = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		BigDecimal totalShares = BigDecimal.ZERO;
		for (WinnerShareRequest winner : winners) {
			if (winner == null || winner.userId() == null || winner.userId() <= 0
					|| winner.rank() == null || winner.rank() <= 0
					|| winner.winnersPoolPercent() == null
					|| winner.winnersPoolPercent().signum() <= 0) {
				throw new BadRequestException("Winner IDs, ranks, and pool shares must be positive");
			}
			if (!userIds.add(winner.userId()) || !ranks.add(winner.rank())) {
				throw new BadRequestException("Winner users and ranks must be unique");
			}
			totalShares = totalShares.add(winner.winnersPoolPercent());
		}
		if (totalShares.compareTo(HUNDRED) != 0) {
			throw new BadRequestException("Winner pool distribution percentages must total 100");
		}
	}

	private BigDecimal percentage(BigDecimal amount, BigDecimal percent) {
		return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal money(BigDecimal amount) {
		return amount == null ? ZERO_MONEY : amount.setScale(2, RoundingMode.UNNECESSARY);
	}
}
