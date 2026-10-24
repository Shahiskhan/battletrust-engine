package com.bte.service;

import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.enums.PaymentStatus;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TournamentAccessService {
    private final RegistrationRepository registrations;
    private final UserRepository users;

    public boolean manages(Tournament tournament, Authentication auth) {
        return auth != null && (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER")) && tournament.getLeader().getEmail().equalsIgnoreCase(auth.getName())));
    }

    public Registration requireEntry(Tournament tournament, Authentication auth, String ticket) {
        if (auth != null) {
            var user = users.findByEmail(auth.getName()).orElse(null);
            if (user != null) {
                var entry = registrations.findByOwnerUserId(user.getId()).stream()
                        .filter(r -> r.getTournament().getId().equals(tournament.getId()) && r.getPaymentStatus() == PaymentStatus.PAID)
                        .findFirst();
                if (entry.isPresent()) return entry.get();
                var memberEntry=registrations.findMemberEntries(user.getId()).stream().filter(r -> r.getTournament().getId().equals(tournament.getId()) && r.getPaymentStatus()==PaymentStatus.PAID).findFirst();
                if(memberEntry.isPresent())return memberEntry.get();
            }
        }
        if (ticket != null && !ticket.isBlank()) {
            var entry = registrations.findByTicketCode(ticket).orElse(null);
            if (entry != null && entry.getTournament().getId().equals(tournament.getId()) && entry.getPaymentStatus() == PaymentStatus.PAID) return entry;
        }
        throw new UnauthorizedException("A verified paid entry or private tournament ticket is required");
    }

    public void requireAccess(Tournament tournament, Authentication auth, String ticket) {
        if (!manages(tournament, auth)) requireEntry(tournament, auth, ticket);
    }
}
