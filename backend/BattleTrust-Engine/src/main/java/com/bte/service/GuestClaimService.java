package com.bte.service;
import com.bte.dto.RegistrationResponse;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.*;
import com.bte.service.escrow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GuestClaimService {
    private final RegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final WalletAccountService accounts;
    private final RegistrationService service;
    private final PlayerCategoryStatsRepository categories;
    private final jakarta.persistence.EntityManager entityManager;
    private final LedgerService ledger;
    private final GuestAccountTransferService transfer;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RegistrationResponse claim(String ticket, String email) {
        var candidate = registrations.findByTicketCode(ticket).orElseThrow(() -> new UnauthorizedException("Invalid ticket"));
        // Serialize claims with settlement and payment using the same tournament lock order.
        tournaments.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();
        var registration = registrations.findByIdForUpdate(candidate.getId()).orElseThrow();
        entityManager.refresh(registration, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var target = users.findByEmail(email).orElseThrow();
        if (target.getId().equals(registration.getOwnerUserId())) return service.toResponse(registration);
        if (registration.getOwnerUserId() != null && !registration.getOwnerUserId().equals(registration.getGuestAccountId()))
            throw new UnauthorizedException("This entry is already linked to another account");
        if (registration.getSquadMembers().stream().skip(1).anyMatch(m -> target.getId().equals(m.getWalletUserId()))) throw new BadRequestException("An account may represent only one player in a room");
        if (registration.getGuestAccountId() != null) transfer.merge(registration.getGuestAccountId(),target.getId(),registration.getTournament().getId(),"claim:"+registration.getId());
        registration.setOwnerUserId(target.getId());
        registration.setClaimedAt(java.time.Instant.now());
        if (!registration.getSquadMembers().isEmpty()) registration.getSquadMembers().getFirst().setWalletUserId(target.getId());
        return service.toResponse(registration);
    }
    private BigDecimal rate(int wins, int played) { return played == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(played), 2, RoundingMode.HALF_UP); }
}

