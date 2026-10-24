package com.bte.controller;

import com.bte.dto.TournamentResponse;
import com.bte.entity.Tournament;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.service.LeaderTournamentService;
import com.bte.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPlatformController {
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final PlatformVaultRepository vaults;
    private final RegistrationRepository registrations;
    private final LeaderTournamentService service;
    private final com.bte.service.TournamentAccessService access;

    public record Listing(TournamentResponse tournament, long registeredPlayers, BigDecimal collectedPool, BigDecimal potentialPool) {}
    private Listing listing(Tournament t) {
        long count = registrations.findByTournamentId(t.getId()).stream()
                .filter(r -> r.getPaymentStatus()==PaymentStatus.PAID || r.getPaymentStatus()==PaymentStatus.PENDING).mapToInt(com.bte.entity.Registration::reservedPlayers).sum();
        BigDecimal collected = vaults.findByTournamentId(t.getId()).stream().map(v -> v.getTotalCollectedPkr()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Listing(service.toResponse(t), count, collected, t.getEntryFeePkr().multiply(BigDecimal.valueOf(t.getMaxPlayers())));
    }
    @GetMapping("/tournaments")
    public List<Listing> list() { return tournaments.findAll(org.springframework.data.domain.Sort.by("startTime").ascending()).stream().filter(t -> !t.isArchived() && "PUBLIC".equals(t.getVisibility())).map(this::listing).toList(); }
    @GetMapping("/tournaments/{id}")
    public Listing detail(@PathVariable Long id,@RequestHeader(value="X-Room-Invite",required=false)String invite,org.springframework.security.core.Authentication auth) {
        var t=tournaments.findById(id).orElseThrow(()->new ResourceNotFoundException("Tournament not found"));
        if(t.isArchived())throw new ResourceNotFoundException("Tournament not found");
        if("PRIVATE".equals(t.getVisibility()) && (t.getRoomInviteToken()==null || !java.util.Objects.equals(t.getRoomInviteToken(),invite)))access.requireAccess(t,auth,null);
        return listing(t);
    }
    @GetMapping("/games")
    public List<GameCategory> games() { return List.of(GameCategory.values()); }
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var all = vaults.findAll().stream().filter(v -> !v.getTournament().isArchived()).toList();
        return Map.of("totalUsers", users.countByDemoAccountFalseAndRoleNot(Role.ROLE_GUEST), "completedTournaments", tournaments.findAll().stream().filter(t -> !t.isArchived() && t.getStatus() == TournamentStatus.COMPLETED).count(),
                "totalPrizePooled", all.stream().map(v -> v.getTotalCollectedPkr()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
