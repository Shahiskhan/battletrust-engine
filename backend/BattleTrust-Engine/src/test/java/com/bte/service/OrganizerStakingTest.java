package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.service.escrow.*;
import com.bte.exception.InsufficientBalanceException;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrganizerStakingTest {
    final StakingSettingsService settings = new StakingSettingsService(mock(SystemSettingRepository.class));
    final WalletAccountService wallets = mock(WalletAccountService.class);
    final LedgerService ledger = mock(LedgerService.class);
    final PlatformTreasuryRepository treasury = mock(PlatformTreasuryRepository.class);
    final OrganizerStakeService service = new OrganizerStakeService(settings, wallets, ledger, treasury);
    final SystemSetting policy = new SystemSetting();
    final User organizer = User.builder().id(1L).build();
    final Tournament t = Tournament.builder().id(2L).leader(organizer).entryFeePkr(new BigDecimal("100")).build();
    final Wallet wallet = Wallet.builder().user(organizer).availableBalance(new BigDecimal("1000")).build();
    @BeforeEach void setup() { when(wallets.getOrCreateLocked(1L)).thenReturn(wallet); }
    @Test void disabledPolicyDoesNotTouchWallet() { service.lock(t, policy); verifyNoInteractions(wallets, ledger); assertEquals(OrganizerStakeStatus.NONE, t.getOrganizerStakeStatus()); }
    @Test void fixedStakeMovesFundsIntoSeparateReserve() {
        policy.setEnabled(true); service.lock(t, policy);
        assertEquals(0, wallet.getAvailableBalance().compareTo(new BigDecimal("500")));
        assertEquals(0, wallet.getOrganizerStakeBalance().compareTo(new BigDecimal("500")));
        assertEquals(0, wallet.getLockedBalance().signum());
        assertEquals(OrganizerStakeStatus.ESCROW_ORGANIZER_STAKE, t.getOrganizerStakeStatus());
    }
    @Test void insufficientFundsCannotLockStake() {
        policy.setEnabled(true); wallet.setAvailableBalance(new BigDecimal("499"));
        assertThrows(InsufficientBalanceException.class, () -> service.lock(t, policy));
        assertEquals(0, wallet.getOrganizerStakeBalance().signum()); verifyNoInteractions(ledger);
    }
    @Test void multiplierUsesMoneyRounding() {
        policy.setEnabled(true); policy.setMode("ENTRY_FEE_MULTIPLIER"); policy.setMultiplier(new BigDecimal("1.25"));
        assertEquals(new BigDecimal("12.49"), settings.quote(policy, new BigDecimal("9.99")));
    }
    @Test void releaseIsIdempotentAndNotEarnings() {
        policy.setEnabled(true); service.lock(t, policy); service.release(t, wallet, OrganizerStakeStatus.RELEASED); service.release(t, wallet, OrganizerStakeStatus.RELEASED);
        assertEquals(0, wallet.getAvailableBalance().compareTo(new BigDecimal("1000"))); assertEquals(0, wallet.getOrganizerStakeBalance().signum()); assertEquals(0, wallet.getTotalEarned().signum());
        verify(ledger, times(1)).record(eq(2L), isNull(), eq(1L), any(), eq(LedgerTransactionType.STAKE_RELEASE), eq("stake:2"));
    }
    @Test void forfeitedStakeCreditsTreasuryWithoutRefund() {
        var account = new PlatformTreasury(); when(treasury.locked()).thenReturn(java.util.Optional.of(account));
        policy.setEnabled(true); service.lock(t, policy); service.release(t, wallet, OrganizerStakeStatus.FORFEITED);
        assertEquals(0, account.getBalance().compareTo(new BigDecimal("500"))); assertEquals(0, wallet.getAvailableBalance().compareTo(new BigDecimal("500")));
        assertEquals(OrganizerStakeStatus.FORFEITED, t.getOrganizerStakeStatus());
    }
}
