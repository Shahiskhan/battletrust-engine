package com.bte.scheduler;
import com.bte.repository.TournamentRepository;
import com.bte.service.VoucherService;
import com.bte.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component @RequiredArgsConstructor
public class VoucherExpiryScheduler {
    private final TournamentRepository tournaments;
    private final VoucherService vouchers;
    @Scheduled(fixedDelay=30000) public void expire(){for(var t:tournaments.findByStatus(TournamentStatus.UPCOMING))vouchers.expireRoom(t.getId());}
}
