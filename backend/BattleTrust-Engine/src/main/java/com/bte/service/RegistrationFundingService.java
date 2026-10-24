package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.BadRequestException;
import com.bte.repository.*;
import com.bte.service.escrow.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class RegistrationFundingService {
    private final RegistrationRepository registrations;
    private final PlatformVaultRepository vaults;
    private final PaymentVoucherRepository vouchers;
    private final LedgerService ledger;
    public void assignSlot(Registration registration) {
        if (registration.getSlotNumber() != null) return;
        var tournament = registration.getTournament();
        var used = registrations.findByTournamentId(tournament.getId()).stream().map(Registration::getSlotNumber).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        int limit = Math.min(25, tournament.getMaxPlayers() / tournament.getSquadSize());
        for (int slot = 1; slot <= limit; slot++) if (!used.contains(slot)) { registration.setSlotNumber(slot); return; }
        throw new BadRequestException("All confirmed room slots are occupied");
    }
    // Tournament + registration are locked before calling; all payment methods share slot allocation and ledger rules.
    public void confirm(Registration registration, String transaction, Long walletOwner, boolean externalDeposit) {
        var t = registration.getTournament();
        if (registration.getPaymentStatus() != PaymentStatus.PENDING) throw new BadRequestException("Entry is not pending");
        var invoice=vouchers.findByRegistrationId(registration.getId()).orElse(null);
        if(invoice!=null && !invoice.getExpiresAt().isAfter(java.time.LocalDateTime.now()) && registration.getTransactionId()==null)throw new BadRequestException("Voucher expired. Register again to obtain a new invoice");
        if (t.getStatus() != TournamentStatus.UPCOMING || !t.getStartTime().isAfter(java.time.LocalDateTime.now())) throw new BadRequestException("Tournament payments are locked");
        assignSlot(registration);
        BigDecimal amount = t.getEntryFeePkr().multiply(BigDecimal.valueOf(registration.reservedPlayers()));
        var vault = vaults.findByTournamentIdForUpdate(t.getId()).orElseGet(() -> vaults.save(PlatformVault.builder().tournament(t)
                .totalCollectedPkr(BigDecimal.ZERO).winnersShare(BigDecimal.ZERO).leaderShare(BigDecimal.ZERO).systemShare(BigDecimal.ZERO).build()));
        if (Boolean.TRUE.equals(vault.getIsDisbursed())) throw new BadRequestException("Escrow already settled");
        vault.setTotalCollectedPkr(vault.getTotalCollectedPkr().add(amount));
        registration.setPaymentStatus(PaymentStatus.PAID); registration.setTransactionId(transaction); registration.setFundsCredited(true);
        if (amount.signum() > 0) {
            String reference = "registration:" + registration.getId();
            if (externalDeposit) ledger.record(t.getId(), null, registration.getOwnerUserId(), amount, LedgerTransactionType.DEPOSIT, reference);
            ledger.record(t.getId(), walletOwner == null ? registration.getOwnerUserId() : walletOwner, null, amount, LedgerTransactionType.ESCROW_HOLD, reference);
        }
        vouchers.findByRegistrationId(registration.getId()).ifPresent(v -> v.setStatus("PAID"));
    }
}
