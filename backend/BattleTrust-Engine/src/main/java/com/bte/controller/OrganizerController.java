package com.bte.controller;

import com.bte.dto.*;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.*;
import com.bte.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER','ROLE_ADMIN')")
@RequestMapping({"/api/v1/organizer", "/api/v1"})
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OrganizerController {
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final LeaderTournamentService service;
    private final RegistrationService registrationService;
    private final RoomCredentialService rooms;
    private final TournamentAccessService access;
    private final PayoutApprovalRepository approvals;
    private final ObjectMapper mapper;
    private final VoucherService vouchers;
    private final MatchResultRepository matchResults;
    private final MatchResultService resultService;
    @GetMapping("/tournaments/{id}/winner-portal")
    public List<MatchResultResponse> winnerPortal(@PathVariable Long id,Authentication auth){managed(id,auth);return matchResults.findByTournamentIdOrderByMatchNumberAsc(id).stream().map(resultService::toResponse).toList();}
    private Tournament managed(Long id, Authentication auth) {
        var t = tournaments.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        if (!access.manages(t, auth)) throw new UnauthorizedException("Only the organizer or admin can manage this tournament");
        return t;
    }
    @GetMapping("/tournaments")
    public List<TournamentResponse> list(Authentication auth) {
        var items = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) ? tournaments.findAll(org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id").descending())) : tournaments.findByLeaderEmailOrderByCreatedAtDesc(auth.getName());
        return items.stream().filter(t -> !t.isArchived()).map(service::toResponse).toList();
    }
    @PostMapping("/tournaments")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ORGANIZER')")
    public TournamentResponse create(@Valid @RequestBody TournamentCreateRequest request, Authentication auth) { return service.create(auth.getName(), request, null); }
    @PutMapping("/tournaments/{id}")
    public TournamentResponse update(@PathVariable Long id, @Valid @RequestBody TournamentCreateRequest r, Authentication auth) {
        var t = managed(id, auth);
        if (t.getStatus() != TournamentStatus.UPCOMING || !t.getStartTime().isAfter(java.time.LocalDateTime.now()) || registrations.countByTournamentId(id) > 0)
            throw new BadRequestException("Tournament settings lock after the first registration or start time");
        if (r.winnersSharePercent().add(r.leaderSharePercent()).add(r.systemSharePercent()).compareTo(new java.math.BigDecimal("100")) != 0
                || r.systemSharePercent().compareTo(t.getSystemSharePercent()) != 0 || !r.startTime().isAfter(java.time.LocalDateTime.now()))
            throw new BadRequestException("Future start time and valid 100% split with the room's original platform fee required");
        int size = r.squadSize() == null ? t.getSquadSize() : r.squadSize();
        if (r.maxPlayers() % size != 0 || r.maxPlayers() / size > 25) throw new BadRequestException("Room capacity must provide at most 25 complete squad slots");
        t.setSquadSize(size); if(r.visibility()!=null)t.setVisibility(r.visibility());
        t.setTitle(r.title().trim()); t.setRules(r.rules()); t.setGameType(r.gameType()); t.setGameCategory(r.gameCategory());
        t.setEntryFeePkr(r.entryFeePkr()); t.setMaxPlayers(r.maxPlayers()); t.setStartTime(r.startTime());
        t.setWinnersSharePercent(r.winnersSharePercent()); t.setLeaderSharePercent(r.leaderSharePercent()); t.setSystemSharePercent(r.systemSharePercent());
        vouchers.prepareInventory(t);
        return service.toResponse(t);
    }
    @PostMapping("/tournaments/{id}/room-credentials")
    public void credentials(@PathVariable Long id, @Valid @RequestBody RoomCredentialsRequest request, Authentication auth) {
        var t = managed(id, auth); rooms.setCredentials(id, t.getLeader().getEmail(), request);
    }
    @GetMapping("/tournaments/{id}/registrations")
    public List<Map<String, Object>> entries(@PathVariable Long id, Authentication auth) {
        managed(id, auth);
        // Never disclose bearer tickets to organizers.
        return registrations.findByTournamentId(id).stream().map(r -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", r.getId()); entry.put("teamName", r.getTeamName()); entry.put("captainName", r.getCaptainName());
            entry.put("ownerUserId", r.getOwnerUserId()); entry.put("paymentStatus", r.getPaymentStatus()); entry.put("transactionId", r.getTransactionId());
            entry.put("slotNumber", r.getSlotNumber());
            var players=new ArrayList<Map<String,Object>>();for(int i=0;i<r.getSquadMembers().size();i++){var m=r.getSquadMembers().get(i);Map<String,Object> player=new LinkedHashMap<>();player.put("userId",i==0?r.getOwnerUserId():m.getWalletUserId());player.put("seat",i+1);player.put("name",m.getName());player.put("inGameId",m.getInGameId());players.add(player);}entry.put("players",players);
            entry.put("squadMembers", r.getSquadMembers().stream().map(m -> new SquadMemberRequest(m.getName(), m.getInGameId())).toList());
            return entry;
        }).toList();
    }
    @PostMapping("/tournaments/{id}/payout-requests")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ORGANIZER')")
    public PayoutApproval requestPayout(@PathVariable Long id, @Valid @RequestBody EscrowSettlementRequest request, Authentication auth) {
        var t = managed(id, auth);
        if (t.getStatus() == TournamentStatus.UPCOMING || t.getStatus() == TournamentStatus.CANCELLED || t.getStatus() == TournamentStatus.COMPLETED)
            throw new BadRequestException("Payout requests require a live or reviewed tournament");
        if(request.overrideReason()!=null && !request.overrideReason().isBlank())throw new BadRequestException("Organizer declarations cannot override score review");
        PayoutDeclarationRules.validate(t,registrations.findByTournamentId(id),matchResults.findByTournamentIdOrderByMatchNumberAsc(id),request);
        var approval=approvals.findByTournamentId(id).orElseGet(PayoutApproval::new);
        if(approval.getId()!=null && !approval.getStatus().equals("REJECTED")){var existing=mapper.readValue(approval.getPayload(),EscrowSettlementRequest.class);if(approval.getStatus().equals("PENDING")&&PayoutDeclarationRules.sameWinners(existing,request))return approval;throw new DuplicateResourceException("Declaration is awaiting or has completed review. Admin must reject it before changes");}
        approval.setReviewNote(null);approval.setStatus("PENDING");approval.setReviewedBy(null);approval.setReviewedAt(null);
        approval.setTournamentId(id); approval.setRequestedBy(auth.getName());approval.setRequestedAt(java.time.Instant.now());
        approval.setPayload(mapper.writeValueAsString(request)); return approvals.save(approval);
    }
}
