package com.bte.scheduler;
import com.bte.repository.PayoutApprovalRepository;
import com.bte.service.ApprovedPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component @RequiredArgsConstructor
public class ApprovedPayoutScheduler {
    private final PayoutApprovalRepository approvals;
    private final ApprovedPayoutService service;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApprovedPayoutScheduler.class);
    @Scheduled(fixedDelay = 30000)
    public void settleApproved() {
        for (var approval : approvals.findByStatus("SCHEDULED")) {
            try { service.settleIfReady(approval.getId()); }
            catch (Exception exception) { log.warn("Scheduled payout {} requires administrator review", approval.getId()); }
        }
    }
}
