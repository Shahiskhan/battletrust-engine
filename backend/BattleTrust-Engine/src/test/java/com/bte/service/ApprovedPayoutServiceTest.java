package com.bte.service;
import com.bte.repository.*;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.dto.*;
import com.bte.service.escrow.EscrowSettlementService;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.mockito.Mockito.*;

class ApprovedPayoutServiceTest {
    final PayoutApprovalRepository approvals = mock(PayoutApprovalRepository.class);
    final MatchResultRepository results = mock(MatchResultRepository.class);
    final DisputeRepository disputes = mock(DisputeRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final EscrowSettlementService settlement = mock(EscrowSettlementService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final ApprovedPayoutService service = new ApprovedPayoutService(approvals, results, disputes, users, settlement, mapper);
    final PayoutApproval approval = new PayoutApproval();
    @BeforeEach void setup() {
        approval.setId(1L); approval.setTournamentId(2L); approval.setStatus("SCHEDULED"); approval.setReviewedBy("admin@example.com"); approval.setReviewedAt(java.time.Instant.now());
        approval.setPayload(mapper.writeValueAsString(new EscrowSettlementRequest(List.of(new WinnerShareRequest(4L, 1, new java.math.BigDecimal("100"))), "Do not propagate override")));
        when(approvals.findLockedById(1L)).thenReturn(Optional.of(approval));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(User.builder().email("admin@example.com").role(Role.ROLE_ADMIN).build()));
        when(disputes.findByTournamentId(2L)).thenReturn(List.of());
    }
    @Test void waitsForClosedVerifiedResults() {
        when(results.findByTournamentIdOrderByMatchNumberAsc(2L)).thenReturn(List.of(MatchResult.builder().disputeWindowClosed(false).verificationStatus(VerificationStatus.YELLOW_TICK_UNVERIFIED).build()));
        service.settleIfReady(1L);
        verifyNoInteractions(settlement);
        Assertions.assertEquals("SCHEDULED", approval.getStatus());
    }
    @Test void pendingDisputeBlocksAutomaticPayout() {
        when(results.findByTournamentIdOrderByMatchNumberAsc(2L)).thenReturn(List.of(MatchResult.builder().disputeDeadline(java.time.LocalDateTime.now().minusSeconds(1)).disputeWindowClosed(true).verificationStatus(VerificationStatus.GREEN_TICK_VERIFIED).build()));
        when(disputes.findByTournamentId(2L)).thenReturn(List.of(Dispute.builder().status(DisputeStatus.PENDING).build()));
        service.settleIfReady(1L); verifyNoInteractions(settlement);
    }
    @Test void verifiedApprovalUsesAdminAuthorityWithoutOverride() {
        when(results.findByTournamentIdOrderByMatchNumberAsc(2L)).thenReturn(List.of(MatchResult.builder().disputeDeadline(java.time.LocalDateTime.now().minusSeconds(1)).disputeWindowClosed(true).verificationStatus(VerificationStatus.GREEN_TICK_VERIFIED).build()));
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        service.settleIfReady(1L);
        verify(settlement).settle(eq(2L), argThat(r -> r.overrideReason() == null), eq("admin@example.com"));
        Assertions.assertEquals("APPROVED", approval.getStatus());
        Assertions.assertSame(previous, org.springframework.security.core.context.SecurityContextHolder.getContext());
    }
    @Test void incompleteAdminReviewNeverReleasesFunds() {
        approval.setReviewedAt(null);
        service.settleIfReady(1L);
        verifyNoInteractions(settlement);
    }
    @Test void blockedReviewerNeverReleasesFunds() {
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(User.builder().email("admin@example.com").role(Role.ROLE_ADMIN).blocked(true).build()));
        service.settleIfReady(1L);
        verifyNoInteractions(settlement);
    }
    @Test void scheduledApprovalPreservesAllThreeDeclaredWinners() {
        approval.setPayload(mapper.writeValueAsString(new EscrowSettlementRequest(List.of(
                new WinnerShareRequest(4L, 1, new java.math.BigDecimal("60")),
                new WinnerShareRequest(5L, 2, new java.math.BigDecimal("30")),
                new WinnerShareRequest(6L, 3, new java.math.BigDecimal("10"))), "Discard this override")));
        when(results.findByTournamentIdOrderByMatchNumberAsc(2L)).thenReturn(List.of(MatchResult.builder().disputeDeadline(java.time.LocalDateTime.now().minusSeconds(1)).disputeWindowClosed(true).verificationStatus(VerificationStatus.GREEN_TICK_VERIFIED).build()));
        service.settleIfReady(1L);
        verify(settlement).settle(eq(2L), argThat(r -> r.overrideReason() == null && r.winners().size() == 3
                && r.winners().get(1).userId().equals(5L) && r.winners().get(2).winnersPoolPercent().compareTo(new java.math.BigDecimal("10")) == 0), eq("admin@example.com"));
    }
}
