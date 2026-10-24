package com.bte.service.escrow;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.PlatformTreasuryRepository;
import com.bte.service.StakingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class OrganizerStakeService {
    private final StakingSettingsService settings;
    private final WalletAccountService wallets;
    private final LedgerService ledger;
    private final PlatformTreasuryRepository treasury;

    // Caller owns the tournament transaction. Settings and wallet locks serialize creation against toggles/spending.
    public void lock(Tournament tournament, SystemSetting policy) {
        BigDecimal amount = settings.quote(policy, tournament.getEntryFeePkr());
        tournament.setOrganizerStakeAmount(amount);
        if (amount.signum() == 0) { tournament.setOrganizerStakeStatus(OrganizerStakeStatus.NONE); return; }
        var wallet = wallets.getOrCreateLocked(tournament.getLeader().getId());
        if (wallet.getAvailableBalance().compareTo(amount) < 0) throw new InsufficientBalanceException("Available balance is below the required organizer security stake");
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setOrganizerStakeBalance(wallet.getOrganizerStakeBalance().add(amount));
        tournament.setOrganizerStakeStatus(OrganizerStakeStatus.ESCROW_ORGANIZER_STAKE);
        ledger.record(tournament.getId(), wallet.getUser().getId(), null, amount, LedgerTransactionType.STAKE_LOCK, "stake:" + tournament.getId());
    }
    public void release(Tournament tournament, Wallet wallet, OrganizerStakeStatus outcome) {
        if (tournament.getOrganizerStakeStatus() != OrganizerStakeStatus.ESCROW_ORGANIZER_STAKE) return;
        BigDecimal amount = tournament.getOrganizerStakeAmount();
        if (wallet.getOrganizerStakeBalance().compareTo(amount) < 0) throw new InsufficientBalanceException("Organizer stake reserve is inconsistent");
        wallet.setOrganizerStakeBalance(wallet.getOrganizerStakeBalance().subtract(amount));
        LedgerTransactionType type;
        if (outcome == OrganizerStakeStatus.FORFEITED) { creditTreasury(amount); type = LedgerTransactionType.STAKE_FORFEIT; }
        else { wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount)); type = outcome == OrganizerStakeStatus.RELEASED ? LedgerTransactionType.STAKE_RELEASE : LedgerTransactionType.STAKE_REFUND; }
        tournament.setOrganizerStakeStatus(outcome);
        ledger.record(tournament.getId(), outcome == OrganizerStakeStatus.FORFEITED ? wallet.getUser().getId() : null,
                outcome == OrganizerStakeStatus.FORFEITED ? null : wallet.getUser().getId(), amount, type, "stake:" + tournament.getId());
    }
    public void creditTreasury(BigDecimal amount) {
        if (amount.signum() <= 0) return;
        var account = treasury.locked().orElseThrow(() -> new IllegalStateException("Treasury is not initialized"));
        account.setBalance(account.getBalance().add(amount));
    }
}
