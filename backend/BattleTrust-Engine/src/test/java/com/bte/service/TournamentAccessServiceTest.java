package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.exception.UnauthorizedException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TournamentAccessServiceTest {
    @Test void unpaidAdminCannotEnterPlayerChatOrLobby(){var auth=new UsernamePasswordAuthenticationToken("admin@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));assertThrows(UnauthorizedException.class,()->access.requireEntry(tournament,auth,null));}
    @Test void unpaidOwnerCannotEnterPlayerChatOrLobby(){var auth=new UsernamePasswordAuthenticationToken("organizer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));assertTrue(access.manages(tournament,auth));assertThrows(UnauthorizedException.class,()->access.requireEntry(tournament,auth,null));}
    @Test void demotedOwnerCannotManageRoom(){var auth=new UsernamePasswordAuthenticationToken("organizer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));assertFalse(access.manages(tournament,auth));}
    final RegistrationRepository registrations = mock(RegistrationRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final TournamentAccessService access = new TournamentAccessService(registrations, users);
    final Tournament tournament = Tournament.builder().id(1L).leader(User.builder().email("organizer@example.com").build()).build();
    @Test void anonymousAccessDenied() { assertThrows(UnauthorizedException.class, () -> access.requireAccess(tournament, null, null)); }
    @Test void pendingTicketDoesNotGrantChat() {
        when(registrations.findByTicketCode("pending")).thenReturn(Optional.of(Registration.builder().tournament(tournament).paymentStatus(PaymentStatus.PENDING).build()));
        assertThrows(UnauthorizedException.class, () -> access.requireAccess(tournament, null, "pending"));
    }
    @Test void paidTicketIsIsolatedToItsTournament() {
        when(registrations.findByTicketCode("paid")).thenReturn(Optional.of(Registration.builder().tournament(Tournament.builder().id(2L).build()).paymentStatus(PaymentStatus.PAID).build()));
        assertThrows(UnauthorizedException.class, () -> access.requireAccess(tournament, null, "paid"));
    }
    @Test void paidGuestHasAccessWithoutAccount() {
        when(registrations.findByTicketCode("paid")).thenReturn(Optional.of(Registration.builder().tournament(tournament).paymentStatus(PaymentStatus.PAID).build()));
        assertDoesNotThrow(() -> access.requireAccess(tournament, null, "paid"));
    }
    @Test void organizerCannotManageAnotherOrganizersTournament() {
        var auth = new UsernamePasswordAuthenticationToken("other@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
        assertFalse(access.manages(tournament, auth));
        assertThrows(UnauthorizedException.class, () -> access.requireAccess(tournament, auth, null));
    }
}
