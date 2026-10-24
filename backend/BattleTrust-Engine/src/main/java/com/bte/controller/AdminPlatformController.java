package com.bte.controller;

import com.bte.dto.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.service.*;
import com.bte.service.escrow.EscrowSettlementService;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.math.BigDecimal;

@RestController @RequestMapping("/api/v1/admin") @RequiredArgsConstructor @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AdminPlatformController {
    @org.springframework.beans.factory.annotation.Autowired private TournamentRepository tournaments;
    @org.springframework.beans.factory.annotation.Autowired private RegistrationRepository registrations;
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
    private com.bte.entity.PayoutApproval lockedApproval(Long id){var candidate=approvals.findById(id).orElseThrow(()->new ResourceNotFoundException("Declaration not found"));tournaments.findByIdForUpdate(candidate.getTournamentId()).orElseThrow();var approval=approvals.findLockedById(id).orElseThrow();em.refresh(approval,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return approval;}
    private EscrowSettlementRequest declared(com.bte.entity.PayoutApproval approval){var request=mapper.readValue(approval.getPayload(),EscrowSettlementRequest.class);if(request.overrideReason()!=null&&!request.overrideReason().isBlank())throw new BadRequestException("Declarations may not bypass score review");PayoutDeclarationRules.validate(tournaments.findById(approval.getTournamentId()).orElseThrow(),registrations.findByTournamentId(approval.getTournamentId()),results.findByTournamentIdOrderByMatchNumberAsc(approval.getTournamentId()),request);return request;}
    public record Reject(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000)String reason){}
    @PostMapping("/payout-requests/{id}/reject")
    public com.bte.entity.PayoutApproval reject(@PathVariable Long id,@jakarta.validation.Valid @RequestBody Reject body,Authentication auth){var approval=lockedApproval(id);if(!approval.getStatus().equals("PENDING")&&!approval.getStatus().equals("SCHEDULED"))throw new BadRequestException("Declaration is already finalized");approval.setReviewNote(body.reason().trim());approval.setStatus("REJECTED");approval.setReviewedBy(auth.getName());approval.setReviewedAt(java.time.Instant.now());return approval;}
    private final UserRepository users;
    private final PlatformVaultRepository vaults;
    private final DisputeRepository disputes;
    private final DisputeService disputeService;
    private final PayoutApprovalRepository approvals;
    private final EscrowSettlementService settlement;
    private final MatchResultRepository results;
    private final MatchResultService resultService;
    private final ObjectMapper mapper;

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        var all = vaults.findAll();
        return Map.of("totalUsers", users.countByDemoAccountFalseAndRoleNot(Role.ROLE_GUEST), "totalVolume", all.stream().filter(v->!v.getTournament().isArchived()).map(v -> v.getTotalCollectedPkr()).reduce(BigDecimal.ZERO, BigDecimal::add),
                "platformEarnings", all.stream().map(v -> v.getSystemShare()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                "pendingDisputes", disputes.findAll().stream().filter(d -> d.getStatus() == DisputeStatus.PENDING).count());
    }
    @GetMapping("/disputes")
    public List<DisputeResponse> disputes() { return disputes.findAll().stream().map(disputeService::toResponse).toList(); }
    @GetMapping("/match-results/{id}")
    public MatchResultResponse result(@PathVariable Long id) { return resultService.toResponse(results.findById(id).orElseThrow(() -> new ResourceNotFoundException("Result not found"))); }
    @GetMapping("/payout-requests")
    public List<com.bte.entity.PayoutApproval> approvals() { return approvals.findAll(); }
    @PostMapping("/payout-requests/{id}/schedule")
    public com.bte.entity.PayoutApproval schedule(@PathVariable Long id, Authentication auth) {
        var approval = lockedApproval(id);
        if (!approval.getStatus().equals("PENDING")) throw new BadRequestException("Payout already reviewed");
        declared(approval);
        approval.setReviewNote("Scheduled after explicit Admin review");approval.setStatus("SCHEDULED"); approval.setReviewedBy(auth.getName()); approval.setReviewedAt(java.time.Instant.now());
        return approval;
    }
    @PostMapping("/payout-requests/{id}/approve")
    public EscrowSettlementResponse approve(@PathVariable Long id, Authentication auth) {
        var approval = lockedApproval(id);
        if (!approval.getStatus().equals("PENDING")) throw new BadRequestException("Payout already reviewed");
        var result = settlement.settle(approval.getTournamentId(), declared(approval), auth.getName());
        approval.setReviewNote("Final escrow released after Admin review");approval.setStatus("APPROVED"); approval.setReviewedBy(auth.getName()); approval.setReviewedAt(java.time.Instant.now()); return result;
    }
    @GetMapping("/users")
    public List<UserDto> users() { return users.findAll().stream().filter(u -> u.getRole() != Role.ROLE_GUEST).map(u -> new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getProfilePictureUrl())).toList(); }
    public record RoleChange(Role role) {}
    @PatchMapping("/users/{id}/role")
    public UserDto role(@PathVariable Long id, @RequestBody RoleChange request, Authentication auth) {
        if (request.role() != Role.ROLE_ORGANIZER && request.role() != Role.ROLE_PLAYER) throw new BadRequestException("Only player/organizer role transitions are supported");
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_GUEST) throw new BadRequestException("Admin and guest escrow roles cannot be changed here");
        user.setRole(request.role()); return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getProfilePictureUrl());
    }
}
