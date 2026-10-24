package com.bte.service;
import com.bte.repository.*;
import com.bte.enums.*;
import com.bte.dto.EscrowSettlementRequest;
import com.bte.service.escrow.EscrowSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Service @RequiredArgsConstructor
public class ApprovedPayoutService {
    private final PayoutApprovalRepository approvals;
    private final MatchResultRepository results;
    private final DisputeRepository disputes;
    private final UserRepository users;
    private final EscrowSettlementService settlement;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired private PlatformVaultRepository vaults;
    @org.springframework.beans.factory.annotation.Autowired private TournamentRepository tournaments;
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void settleIfReady(Long id) {
        // Keep the same lock order as declaration, direct admin release, and settlement.
        // The null guard preserves existing constructor-only unit fixtures.
        if (tournaments != null) {
            var candidate = approvals.findById(id).orElseThrow(() -> new com.bte.exception.ResourceNotFoundException("Payout declaration not found"));
            tournaments.findByIdForUpdate(candidate.getTournamentId()).orElseThrow(() -> new com.bte.exception.ResourceNotFoundException("Tournament not found"));
        }
        var approval = approvals.findLockedById(id).orElseThrow();
        if (em != null) em.refresh(approval, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!approval.getStatus().equals("SCHEDULED") || approval.getReviewedBy() == null || approval.getReviewedAt() == null) return;
        if(vaults!=null && vaults.findByTournamentId(approval.getTournamentId()).stream().anyMatch(v -> Boolean.TRUE.equals(v.getIsDisbursed()))) {approval.setStatus("COMPLETED_EXTERNALLY");return;}
        var admin = users.findByEmail(approval.getReviewedBy()).orElse(null);
        if (admin == null || admin.isBlocked() || admin.getRole() != Role.ROLE_ADMIN) return;
        var matches = results.findByTournamentIdOrderByMatchNumberAsc(approval.getTournamentId());
        if (matches.isEmpty() || matches.stream().anyMatch(r -> !r.isDisputeWindowClosed() || r.getVerificationStatus() != VerificationStatus.GREEN_TICK_VERIFIED
                || r.getDisputeDeadline() == null || r.getDisputeDeadline().isAfter(java.time.LocalDateTime.now()))
                || disputes.findByTournamentId(approval.getTournamentId()).stream().anyMatch(d -> d.getStatus() == DisputeStatus.PENDING)) return;
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(admin.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            SecurityContextHolder.setContext(context);
            var request = mapper.readValue(approval.getPayload(), EscrowSettlementRequest.class);
            // Automatic execution never carries an override reason through a review window.
            settlement.settle(approval.getTournamentId(), new EscrowSettlementRequest(request.winners(), null), admin.getEmail());
            approval.setStatus("APPROVED");
        } finally { SecurityContextHolder.setContext(previous); }
    }
}
