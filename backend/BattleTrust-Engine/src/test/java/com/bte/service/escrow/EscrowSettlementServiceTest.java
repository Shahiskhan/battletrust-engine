package com.bte.service.escrow;

import com.bte.dto.EscrowSettlementRequest;
import com.bte.dto.EscrowSettlementResponse;
import com.bte.dto.WinnerShareRequest;
import com.bte.entity.MatchResult;
import com.bte.entity.PlatformVault;
import com.bte.entity.Registration;
import com.bte.entity.SquadMember;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.entity.Wallet;
import com.bte.enums.PaymentStatus;
import com.bte.enums.Role;
import com.bte.enums.TournamentStatus;
import com.bte.enums.VerificationStatus;
import com.bte.exception.DuplicateResourceException;
import com.bte.repository.LedgerTransactionRepository;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.PlatformVaultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import com.bte.repository.WalletRepository;
import com.bte.service.PlayerPerformanceService;
import com.bte.service.escrow.EscrowSettlementService;
import com.bte.service.escrow.LedgerService;
import com.bte.service.escrow.WalletAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscrowSettlementServiceTest {

	@Mock
	private PlatformVaultRepository vaultRepository;
	@Mock
	private TournamentRepository tournamentRepository;
	@Mock
	private RegistrationRepository registrationRepository;
	@Mock
	private MatchResultRepository matchResultRepository;
	@Mock
	private WalletRepository walletRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private LedgerTransactionRepository ledgerTransactionRepository;
	@Mock
	private PlayerPerformanceService playerPerformanceService;

	private Tournament tournament;
	private PlatformVault vault;
	private Wallet winnerOneWallet;
	private Wallet winnerTwoWallet;
	private Wallet leaderWallet;
	private EscrowSettlementService settlementService;

	@BeforeEach
	void setUp() {
		User winnerOne = user(1L, "one@example.com");
		User winnerTwo = user(2L, "two@example.com");
		User leader = user(3L, "leader@example.com");
		User admin = user(4L, "admin@example.com");
		tournament = Tournament.builder()
				.id(8L)
				.leader(leader)
				.entryFeePkr(new BigDecimal("50.00"))
				.status(TournamentStatus.UNDER_REVIEW)
				.build();
		vault = PlatformVault.builder()
				.id(10L)
				.tournament(tournament)
				.totalCollectedPkr(new BigDecimal("400.00"))
				.winnersShare(BigDecimal.ZERO)
				.leaderShare(BigDecimal.ZERO)
				.systemShare(BigDecimal.ZERO)
				.isDisbursed(false)
				.build();
		winnerOneWallet = wallet(winnerOne);
		winnerTwoWallet = wallet(winnerTwo);
		leaderWallet = wallet(leader);
		WalletAccountService walletAccounts = new WalletAccountService(walletRepository, userRepository);
		settlementService = new EscrowSettlementService(
				vaultRepository,
				tournamentRepository,
				registrationRepository,
				matchResultRepository,
				walletAccounts,
				new LedgerService(ledgerTransactionRepository),
				userRepository,
				playerPerformanceService,
				new BigDecimal("85.00"),
				new BigDecimal("10.00"),
				new BigDecimal("5.00"));

		when(tournamentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(tournament));
		when(vaultRepository.findByTournamentIdForUpdate(8L)).thenReturn(Optional.of(vault));
		when(matchResultRepository.findByTournamentIdOrderByMatchNumberAsc(8L))
				.thenReturn(List.of(MatchResult.builder()
						.verificationStatus(VerificationStatus.GREEN_TICK_VERIFIED)
						.disputeWindowClosed(true)
						.rankings(List.of(new com.bte.entity.MatchRanking(1L, 1, 7, "One"), new com.bte.entity.MatchRanking(2L, 2, 2, "Two")))
						.build()));
		when(registrationRepository.findByTournamentId(8L)).thenReturn(List.of(
				registration("one@example.com", "One"),
				registration("two@example.com", "Two")));
		when(userRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation -> {
			long id = invocation.getArgument(0);
			return switch ((int) id) {
				case 1 -> Optional.of(winnerOneWallet.getUser());
				case 2 -> Optional.of(winnerTwoWallet.getUser());
				case 3 -> Optional.of(leaderWallet.getUser());
				default -> Optional.empty();
			};
		});
		when(walletRepository.findByUserIdForUpdate(anyLong())).thenAnswer(invocation -> {
			long id = invocation.getArgument(0);
			return switch ((int) id) {
				case 1 -> Optional.of(winnerOneWallet);
				case 2 -> Optional.of(winnerTwoWallet);
				case 3 -> Optional.of(leaderWallet);
				default -> Optional.empty();
			};
		});
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
		when(ledgerTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void distributesPreciselyAndMarksVaultSettled() {
		EscrowSettlementResponse result = settlementService.settle(8L, new EscrowSettlementRequest(List.of(
				new WinnerShareRequest(1L, 1, new BigDecimal("60.00")),
				new WinnerShareRequest(2L, 2, new BigDecimal("40.00"))), null), "admin@example.com");

		assertEquals(new BigDecimal("400.00"), result.totalPool());
		assertEquals(new BigDecimal("340.00"), result.winnersPool());
		assertEquals(new BigDecimal("40.00"), result.leaderCommission());
		assertEquals(new BigDecimal("20.00"), result.platformRevenue());
		assertEquals(new BigDecimal("204.00"), winnerOneWallet.getAvailableBalance());
		assertEquals(new BigDecimal("136.00"), winnerTwoWallet.getAvailableBalance());
		assertEquals(new BigDecimal("40.00"), leaderWallet.getAvailableBalance());
		assertEquals(true, vault.getIsDisbursed());
		assertEquals(TournamentStatus.COMPLETED, tournament.getStatus());
		assertEquals("Settlement after verified match result", vault.getSettlementReason());
		verify(ledgerTransactionRepository, org.mockito.Mockito.times(4)).save(any());
		verify(playerPerformanceService).applyTournamentSettlement(any(), any(), any());
	}

	@Test
	void settlementCannotBeRepeated() {
		vault.setIsDisbursed(true);
		assertThrows(DuplicateResourceException.class, () -> settlementService.settle(
				8L,
				new EscrowSettlementRequest(List.of(
						new WinnerShareRequest(1L, 1, new BigDecimal("100.00"))), null),
				"admin@example.com"));
		verify(ledgerTransactionRepository, never()).save(any());
	}

	@Test
	void payoutRankMustMatchVerifiedResultWithoutExplicitOverride() {
		assertThrows(com.bte.exception.BadRequestException.class, () -> settlementService.settle(8L,
				new EscrowSettlementRequest(List.of(new WinnerShareRequest(1L, 2, new BigDecimal("100.00"))), null), "admin@example.com"));
		verify(ledgerTransactionRepository, never()).save(any());
	}

	private User user(Long id, String email) {
		return User.builder().id(id).name(email).email(email).role(Role.ROLE_PLAYER).build();
	}

	private Registration registration(String captainEmail, String teamName) {
		return Registration.builder()
				.ownerUserId(captainEmail.equals("one@example.com") ? 1L : 2L)
				.tournament(tournament)
				.teamName(teamName)
				.captainEmail(captainEmail)
				.paymentStatus(PaymentStatus.PAID)
				.squadMembers(List.of(
						new SquadMember(teamName + " one", "1"),
						new SquadMember(teamName + " two", "2"),
						new SquadMember(teamName + " three", "3"),
						new SquadMember(teamName + " four", "4")))
				.build();
	}

	private Wallet wallet(User user) {
		return Wallet.builder()
				.user(user)
				.availableBalance(new BigDecimal("0.00"))
				.lockedBalance(new BigDecimal("0.00"))
				.totalEarned(new BigDecimal("0.00"))
				.build();
	}
}
