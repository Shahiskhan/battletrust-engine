package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.*;
import com.bte.service.escrow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class TournamentCancellationService {
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final PaymentVoucherRepository vouchers;
    private final PlatformVaultRepository vaults;
    private final WalletAccountService wallets;
    private final OrganizerStakeService stakes;
    private final LedgerService ledger;
    private final TournamentAccessService access;
    private final LeaderTournamentService service;
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.bte.dto.TournamentResponse cancel(Long id, String reason, boolean waiveForfeiture, Authentication auth) {
        var t = tournaments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        if (!access.manages(t, auth)) throw new UnauthorizedException("Only the organizer or administrator may cancel this tournament");
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (waiveForfeiture && !admin) throw new UnauthorizedException("Only an administrator can approve a valid-cause stake waiver");
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new BadRequestException("A cancellation reason of 1–1000 characters is required");
        if (t.getStatus() == TournamentStatus.CANCELLED) return service.toResponse(t);
        if (t.getStatus() == TournamentStatus.COMPLETED) throw new BadRequestException("Completed tournaments cannot be deleted");
        var entries = registrations.findByTournamentId(id);
        var vault = vaults.findByTournamentIdForUpdate(id).orElse(null);
        if (vault != null && Boolean.TRUE.equals(vault.getIsDisbursed())) throw new BadRequestException("Escrow has already been settled");
        Set<Long> ids = new TreeSet<>(); ids.add(t.getLeader().getId());
        entries.stream().filter(r -> r.getPaymentStatus() == PaymentStatus.PAID).forEach(r -> { if (r.getOwnerUserId() != null) ids.add(r.getOwnerUserId()); });
        var accounts = wallets.getOrCreateLockedInOrder(ids);
        BigDecimal refunds = BigDecimal.ZERO;
        for (Registration r : entries) {
            if (r.getPaymentStatus() == PaymentStatus.PAID) {
                BigDecimal amount = t.getEntryFeePkr().multiply(BigDecimal.valueOf(r.reservedPlayers()));
                if (amount.signum() > 0) {
                    if (r.getOwnerUserId() == null) throw new BadRequestException("Legacy paid guest entry must be linked before cancellation refunds");
                    var wallet = accounts.get(r.getOwnerUserId());
                    wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
                    ledger.record(id, null, r.getOwnerUserId(), amount, LedgerTransactionType.REFUND, "registration:" + r.getId());
                    refunds = refunds.add(amount);
                }
                r.setPaymentStatus(PaymentStatus.REFUNDED);
            } else if (r.getPaymentStatus() == PaymentStatus.PENDING) r.setPaymentStatus(PaymentStatus.CANCELLED);
            vouchers.findByRegistrationId(r.getId()).ifPresent(v -> v.setStatus("CANCELLED"));
        }
        if (refunds.signum() > 0 && (vault == null || vault.getTotalCollectedPkr().compareTo(refunds) != 0)) throw new BadRequestException("Escrow balance does not match cancellation refunds");
        if (vault != null) { vault.setTotalCollectedPkr(BigDecimal.ZERO); vault.setIsDisbursed(true); vault.setSettlementReason("Cancellation: " + reason.trim()); vault.setSettledAt(java.time.Instant.now()); }
        stakes.release(t, accounts.get(t.getLeader().getId()), entries.isEmpty() || waiveForfeiture ? OrganizerStakeStatus.REFUNDED : OrganizerStakeStatus.FORFEITED);
        vouchers.findByTournamentIdOrderByInventoryNumberAsc(id).forEach(v -> v.setStatus("CANCELLED"));
        t.setStatus(TournamentStatus.CANCELLED); t.setCancellationReason(reason.trim()); t.setCancelledBy(auth.getName());
        return service.toResponse(t);
    }
}
