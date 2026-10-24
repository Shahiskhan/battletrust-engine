package com.bte.controller;

import com.bte.dto.*;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.*;
import com.bte.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class PlayerPlatformController {
    private final RegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final MatchResultRepository results;
    private final RegistrationService registrationService;
    private final MatchResultService resultService;
    private final TournamentAccessService access;
    private final GuestClaimService claims;
    private final VoucherService vouchers;

    public record Claim(@NotBlank String ticketCode) {}
    public record Entry(RegistrationResponse registration, TournamentResponse tournament) {}
    private final LeaderTournamentService tournamentService;

    @GetMapping("/api/v1/me")
    public Map<String, Object> me(Authentication auth) {
        var user = users.findByEmail(auth.getName()).orElseThrow();
        if(user.getRole()==com.bte.enums.Role.ROLE_ADMIN)return Map.of("user",new UserDto(user.getId(),user.getName(),user.getEmail(),user.getRole(),null));
        return Map.of("user", new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getProfilePictureUrl()),
                "matchesPlayed", user.getMatchesPlayed(), "matchesWon", user.getMatchesWon(), "totalKills", user.getTotalKills(),
                "reputationScore", user.getReputationScore(), "winRatePercent", user.getWinRatePercent());
    }
    @GetMapping("/api/v1/me/registrations")
    public List<Entry> entries(Authentication auth) {
        var user = users.findByEmail(auth.getName()).orElseThrow();
        return registrations.findByOwnerUserId(user.getId()).stream().map(r -> new Entry(registrationService.toResponse(r), tournamentService.toResponse(r.getTournament()))).toList();
    }
    @PostMapping("/api/v1/me/registrations/claim")
    public RegistrationResponse claim(@Valid @RequestBody Claim request, Authentication auth) {
        return claims.claim(request.ticketCode(), auth.getName());
    }
    @PostMapping("/api/v1/tournaments/{id}/join")
    public RegistrationResponse join(@PathVariable Long id, @Valid @RequestBody RegistrationInitRequest request, Authentication auth) {
        if (!id.equals(request.tournamentId())) throw new BadRequestException("Tournament IDs must match");
        var user = users.findByEmail(auth.getName()).orElseThrow();
        var locked=tournaments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        vouchers.expireReservations(locked);
        if (registrations.existsByTournamentIdAndOwnerUserIdAndPaymentStatus(id,user.getId(),PaymentStatus.PAID) || registrations.existsByTournamentIdAndOwnerUserIdAndPaymentStatus(id,user.getId(),PaymentStatus.PENDING)) throw new DuplicateResourceException("Already registered");
        var response = registrationService.initialize(request);
        var registration = registrations.findById(response.id()).orElseThrow();
        registration.setOwnerUserId(user.getId());
        return registrationService.toResponse(registration);
    }
    @GetMapping("/api/v1/public/tournaments/{id}/room")
    public Map<String, String> room(@PathVariable Long id, Authentication auth,
            @RequestHeader(value = "X-Registration-Ticket", required = false) String ticket) {
        var tournament = tournaments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        access.requireEntry(tournament, auth, ticket);
        return Map.of("roomId", Objects.requireNonNullElse(tournament.getRoomId(), ""), "roomPassword", Objects.requireNonNullElse(tournament.getRoomPassword(), ""));
    }
    @GetMapping("/api/v1/public/registrations/ticket")
    public RegistrationResponse ticket(@RequestHeader("X-Registration-Ticket") String ticket) {
        return registrationService.toResponse(registrations.findByTicketCode(ticket).orElseThrow(() -> new UnauthorizedException("Invalid ticket")));
    }
    @GetMapping("/api/v1/public/tournaments/{id}/results")
    public List<MatchResultResponse> results(@PathVariable Long id, Authentication auth, @RequestHeader(value = "X-Registration-Ticket", required = false) String ticket) {
        var tournament = tournaments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        access.requireAccess(tournament, auth, ticket);
        return results.findByTournamentIdOrderByMatchNumberAsc(id).stream().map(resultService::toResponse).toList();
    }
}
