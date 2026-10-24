package com.bte.service;

import com.bte.dto.LedgerTransactionResponse;
import com.bte.dto.WalletResponse;
import com.bte.dto.WithdrawalRequestDto;
import com.bte.dto.WithdrawalResponse;
import com.bte.entity.User;
import com.bte.entity.Wallet;
import com.bte.entity.WithdrawalRequest;
import com.bte.enums.LedgerTransactionType;
import com.bte.enums.WithdrawalStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.InsufficientBalanceException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.LedgerTransactionRepository;
import com.bte.repository.UserRepository;
import com.bte.repository.WalletRepository;
import com.bte.repository.WithdrawalRequestRepository;
import com.bte.service.escrow.LedgerService;
import com.bte.service.escrow.WalletAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class WalletService {

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final LedgerTransactionRepository ledgerTransactionRepository;
	private final WalletAccountService walletAccountService;
	private final LedgerService ledgerService;
	private final BigDecimal minimumWithdrawal;
	private final BigDecimal maximumWithdrawal;
	@org.springframework.beans.factory.annotation.Autowired
	private com.bte.repository.RegistrationRepository registrations;
	@org.springframework.beans.factory.annotation.Autowired
	private com.bte.repository.PlatformVaultRepository vaults;

	public WalletService(
			UserRepository userRepository,
			WalletRepository walletRepository,
			WithdrawalRequestRepository withdrawalRequestRepository,
			LedgerTransactionRepository ledgerTransactionRepository,
			WalletAccountService walletAccountService,
			LedgerService ledgerService,
			@Value("${app.wallet.minimum-withdrawal:100.00}") BigDecimal minimumWithdrawal,
			@Value("${app.wallet.maximum-withdrawal:1000000.00}") BigDecimal maximumWithdrawal) {
		this.userRepository = userRepository;
		this.walletRepository = walletRepository;
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.ledgerTransactionRepository = ledgerTransactionRepository;
		this.walletAccountService = walletAccountService;
		this.ledgerService = ledgerService;
		this.minimumWithdrawal = minimumWithdrawal;
		this.maximumWithdrawal = maximumWithdrawal;
		if (minimumWithdrawal.signum() <= 0 || maximumWithdrawal.compareTo(minimumWithdrawal) < 0) {
			throw new IllegalArgumentException("Wallet withdrawal limits are invalid");
		}
	}

	@Transactional
	public WalletResponse getWallet(String email, int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
		}
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User account was not found"));
		Wallet wallet = walletAccountService.getOrCreateLocked(user.getId());
		var references = new java.util.ArrayList<String>();
		references.add("");
		var userIds = new java.util.ArrayList<Long>(); userIds.add(user.getId());
		registrations.findByOwnerUserId(user.getId()).forEach(r -> { references.add("registration:" + r.getId()); if (r.getGuestAccountId() != null) userIds.add(r.getGuestAccountId()); });
		registrations.findMemberEntries(user.getId()).forEach(r -> r.getSquadMembers().stream().filter(m -> user.getId().equals(m.getWalletUserId()) && m.getOriginalGuestUserId()!=null).forEach(m -> userIds.add(m.getOriginalGuestUserId())));
		BigDecimal escrowReserved=registrations.findByOwnerUserId(user.getId()).stream().filter(r -> r.getPaymentStatus()==com.bte.enums.PaymentStatus.PAID)
				.filter(r -> vaults.findByTournamentId(r.getTournament().getId()).stream().anyMatch(v -> !Boolean.TRUE.equals(v.getIsDisbursed())))
				.map(r -> r.getTournament().getEntryFeePkr().multiply(BigDecimal.valueOf(r.reservedPlayers()))).reduce(BigDecimal.ZERO,BigDecimal::add);
		var ledgerPage = ledgerTransactionRepository.findForOwner(userIds, references, PageRequest.of(page, size));
		return new WalletResponse(
				user.getId(),
				money(wallet.getAvailableBalance()),
				money(wallet.getLockedBalance()),
				money(wallet.getTotalEarned()),
				money(wallet.getOrganizerStakeBalance()),
				money(escrowReserved),
				ledgerPage.getContent().stream().map(this::toLedgerResponse).toList());
	}

	@Transactional
	@PreAuthorize("hasAnyAuthority('ROLE_PLAYER', 'ROLE_LEADER', 'ROLE_ORGANIZER', 'ROLE_ADMIN')")
	public WithdrawalResponse requestWithdrawal(String email, WithdrawalRequestDto request) {
		BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
		if (amount.compareTo(minimumWithdrawal) < 0 || amount.compareTo(maximumWithdrawal) > 0) {
			throw new BadRequestException("Withdrawal amount is outside configured limits");
		}
		User user = userRepository.findByEmailForUpdate(email)
				.orElseThrow(() -> new ResourceNotFoundException("User account was not found"));
		Wallet wallet = walletAccountService.getOrCreateLocked(user.getId());
		BigDecimal available = money(wallet.getAvailableBalance());
		if (available.compareTo(amount) < 0) {
			throw new InsufficientBalanceException("Insufficient available wallet balance");
		}
		wallet.setAvailableBalance(available.subtract(amount));
		wallet.setLockedBalance(money(wallet.getLockedBalance()).add(amount));
		WithdrawalRequest withdrawal = withdrawalRequestRepository.save(WithdrawalRequest.builder()
				.user(user)
				.amount(amount)
				.paymentMethod(request.paymentMethod())
				.accountDetails(request.accountDetails().trim())
				.status(WithdrawalStatus.PENDING)
				.build());
		return toWithdrawalResponse(withdrawal);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public org.springframework.data.domain.Page<WithdrawalResponse> listPendingWithdrawals(int page, int size) {
		validatePage(page, size);
		return withdrawalRequestRepository
				.findByStatusOrderByRequestedAtAsc(WithdrawalStatus.PENDING, PageRequest.of(page, size))
				.map(this::toWithdrawalResponse);
	}

	@Transactional
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public WithdrawalResponse decideWithdrawal(
			Long withdrawalId, boolean approve, String decisionNote, String adminEmail) {
		WithdrawalRequest withdrawal = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
				.orElseThrow(() -> new ResourceNotFoundException("Withdrawal request was not found"));
		if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
			throw new BadRequestException("Withdrawal request has already been decided");
		}
		User admin = userRepository.findByEmail(adminEmail)
				.orElseThrow(() -> new ResourceNotFoundException("Admin account was not found"));
		Wallet wallet = walletAccountService.getOrCreateLocked(withdrawal.getUser().getId());
		BigDecimal amount = money(withdrawal.getAmount());
		BigDecimal locked = money(wallet.getLockedBalance());
		if (locked.compareTo(amount) < 0) {
			throw new InsufficientBalanceException("Locked wallet balance is insufficient for this withdrawal");
		}
		wallet.setLockedBalance(locked.subtract(amount));
		if (approve) {
			withdrawal.setStatus(WithdrawalStatus.APPROVED);
			ledgerService.record(null, withdrawal.getUser().getId(), null, amount,
					LedgerTransactionType.WITHDRAWAL, "withdrawal:" + withdrawal.getId());
		} else {
			withdrawal.setStatus(WithdrawalStatus.REJECTED);
			wallet.setAvailableBalance(money(wallet.getAvailableBalance()).add(amount));
		}
		withdrawal.setDecisionNote(decisionNote == null ? null : decisionNote.trim());
		withdrawal.setReviewedBy(admin);
		withdrawal.setReviewedAt(Instant.now());
		return toWithdrawalResponse(withdrawalRequestRepository.save(withdrawal));
	}

	private LedgerTransactionResponse toLedgerResponse(
			com.bte.entity.LedgerTransaction transaction) {
		return new LedgerTransactionResponse(
				transaction.getId(),
				transaction.getTournamentId(),
				transaction.getSenderId(),
				transaction.getReceiverId(),
				transaction.getAmount(),
				transaction.getTransactionType(),
				transaction.getReferenceId(),
				transaction.getCreatedAt());
	}

	private WithdrawalResponse toWithdrawalResponse(WithdrawalRequest withdrawal) {
		return new WithdrawalResponse(
				withdrawal.getId(),
				withdrawal.getUser().getId(),
				withdrawal.getUser().getEmail(),
				withdrawal.getAmount(),
				withdrawal.getPaymentMethod(),
				withdrawal.getAccountDetails(),
				withdrawal.getStatus(),
				withdrawal.getRequestedAt(),
				withdrawal.getDecisionNote(),
				withdrawal.getReviewedBy() == null ? null : withdrawal.getReviewedBy().getId(),
				withdrawal.getReviewedAt());
	}

	private BigDecimal money(BigDecimal amount) {
		return amount == null ? new BigDecimal("0.00") : amount.setScale(2, RoundingMode.UNNECESSARY);
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
		}
	}
}
