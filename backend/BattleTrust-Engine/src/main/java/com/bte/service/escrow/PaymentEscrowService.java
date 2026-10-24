package com.bte.service.escrow;

import com.bte.entity.PlatformVault;
import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.dto.RegistrationResponse;
import com.bte.enums.LedgerTransactionType;
import com.bte.enums.PaymentStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.DuplicateResourceException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.PlatformVaultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.service.RegistrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PaymentEscrowService {

	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final PlatformVaultRepository vaultRepository;
	private final LedgerService ledgerService;
	private final RegistrationService registrationService;
	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager entityManager;
	@org.springframework.beans.factory.annotation.Autowired
	private com.bte.service.RegistrationFundingService funding;

	public PaymentEscrowService(
			RegistrationRepository registrationRepository,
			TournamentRepository tournamentRepository,
			PlatformVaultRepository vaultRepository,
			LedgerService ledgerService,
			RegistrationService registrationService) {
		this.registrationRepository = registrationRepository;
		this.tournamentRepository = tournamentRepository;
		this.vaultRepository = vaultRepository;
		this.ledgerService = ledgerService;
		this.registrationService = registrationService;
	}

	@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public RegistrationResponse verifyRegistrationPayment(Long registrationId, String transactionId) {
		Registration existing = registrationRepository.findById(registrationId)
				.orElseThrow(() -> new ResourceNotFoundException("Registration was not found"));
		Tournament tournament = tournamentRepository.findByIdForUpdate(existing.getTournament().getId())
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		Registration registration = registrationRepository.findByIdForUpdate(registrationId)
				.orElseThrow(() -> new ResourceNotFoundException("Registration was not found"));
		entityManager.refresh(registration, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		String normalizedTransactionId = transactionId.trim();
		if (registration.getPaymentStatus() == PaymentStatus.PAID) {
			if (normalizedTransactionId.equals(registration.getTransactionId())) {
				return registrationService.toResponse(registration);
			}
			throw new DuplicateResourceException("Registration payment was already verified");
		}
		if (registration.getPaymentStatus() != PaymentStatus.PENDING) {
			throw new BadRequestException("Only pending registration payments can be verified");
		}
		if (registration.getTransactionId() == null
				|| !registration.getTransactionId().equals(normalizedTransactionId)) {
			throw new BadRequestException("Submitted transaction reference does not match registration");
		}

		funding.confirm(registration, normalizedTransactionId, null, true);
		return registrationService.toResponse(registration);
	}
}

