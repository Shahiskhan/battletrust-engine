package com.bte.service;
import com.bte.dto.RegistrationResponse;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.exception.*;
import com.bte.service.escrow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class WalletEntryService {
    private final RegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final PlatformVaultRepository vaults;
    private final WalletAccountService accounts;
    private final LedgerService ledger;
    private final RegistrationService service;
    private final jakarta.persistence.EntityManager entityManager;
    private final RegistrationFundingService funding;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RegistrationResponse pay(Long id, String email) {
        var initial = registrations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        var tournament = tournaments.findByIdForUpdate(initial.getTournament().getId()).orElseThrow();
        var registration = registrations.findByIdForUpdate(id).orElseThrow();
        // A pre-lock lookup can leave a stale entity in the persistence context under MySQL REPEATABLE_READ.
        entityManager.refresh(registration, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var user = users.findByEmail(email).orElseThrow();
        if (!user.getId().equals(registration.getOwnerUserId())) throw new UnauthorizedException("Entry belongs to another player");
        if (registration.getPaymentStatus() == PaymentStatus.PAID) return service.toResponse(registration);
        if (registration.getPaymentStatus() != PaymentStatus.PENDING || !tournament.getStartTime().isAfter(java.time.LocalDateTime.now()) || tournament.getStatus() != TournamentStatus.UPCOMING)
            throw new BadRequestException("Entry payment is locked");
        BigDecimal amount = tournament.getEntryFeePkr().multiply(BigDecimal.valueOf(registration.reservedPlayers()));
        var wallet = accounts.getOrCreateLocked(user.getId());
        if (wallet.getAvailableBalance().compareTo(amount) < 0) throw new InsufficientBalanceException("Insufficient wallet balance");
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        funding.confirm(registration, "wallet:" + id, user.getId(), false);
        return service.toResponse(registration);
    }
}

