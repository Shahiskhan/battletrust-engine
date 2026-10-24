package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.service.escrow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.List;
@Service @RequiredArgsConstructor
public class GuestAccountTransferService {
    private final UserRepository users;
    private final WalletAccountService accounts;
    private final LedgerService ledger;
    private final PlayerCategoryStatsRepository categories;
    private final jakarta.persistence.EntityManager em;
    // Runs inside the caller's tournament + registration lock transaction.
    public void merge(Long guestId,Long targetId,Long tournamentId,String reference) {
        var guest=users.findById(guestId).orElseThrow();
        if(guest.getRole()!=Role.ROLE_GUEST) throw new com.bte.exception.UnauthorizedException("Identity already claimed");
        var wallets=accounts.getOrCreateLockedInOrder(List.of(guestId,targetId));
        var from=wallets.get(guestId); var to=wallets.get(targetId);
        if(from.getAvailableBalance().signum()>0) ledger.record(tournamentId,guestId,targetId,from.getAvailableBalance(),LedgerTransactionType.ACCOUNT_TRANSFER,reference);
        to.setAvailableBalance(to.getAvailableBalance().add(from.getAvailableBalance()));to.setTotalEarned(to.getTotalEarned().add(from.getTotalEarned()));
        from.setAvailableBalance(BigDecimal.ZERO);from.setTotalEarned(BigDecimal.ZERO);
        var target=users.findByIdForUpdate(targetId).orElseThrow();em.refresh(target,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        target.setMatchesPlayed(target.getMatchesPlayed()+guest.getMatchesPlayed());target.setMatchesWon(target.getMatchesWon()+guest.getMatchesWon());
        target.setTotalKills(target.getTotalKills()+guest.getTotalKills());target.setReputationScore(target.getReputationScore()+guest.getReputationScore());target.setWinRatePercent(rate(target.getMatchesWon(),target.getMatchesPlayed()));
        for(GameCategory game:GameCategory.values()) {
            var old=categories.findByUserIdAndGameCategoryForUpdate(guestId,game).orElse(null);if(old==null)continue;
            var current=categories.findByUserIdAndGameCategoryForUpdate(targetId,game).orElseGet(()->categories.save(PlayerCategoryStats.builder().user(target).gameCategory(game).build()));
            current.setMatchesPlayed(current.getMatchesPlayed()+old.getMatchesPlayed());current.setMatchesWon(current.getMatchesWon()+old.getMatchesWon());
            current.setTotalKills(current.getTotalKills()+old.getTotalKills());current.setReputationScore(current.getReputationScore()+old.getReputationScore());current.setWinRatePercent(rate(current.getMatchesWon(),current.getMatchesPlayed()));
        }
    }
    private BigDecimal rate(int wins,int played){return played==0?BigDecimal.ZERO:BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(played),2,RoundingMode.HALF_UP);}
}
