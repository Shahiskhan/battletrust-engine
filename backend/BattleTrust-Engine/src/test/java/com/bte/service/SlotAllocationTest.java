package com.bte.service;
import com.bte.repository.*;
import com.bte.entity.*;
import com.bte.service.escrow.LedgerService;
import com.bte.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class SlotAllocationTest {
    final RegistrationRepository registrations = mock(RegistrationRepository.class);
    final RegistrationFundingService service = new RegistrationFundingService(registrations, mock(PlatformVaultRepository.class), mock(PaymentVoucherRepository.class), mock(LedgerService.class));
    final Tournament tournament = Tournament.builder().id(1L).squadSize(4).maxPlayers(100).build();
    @Test void fillsLowestUnusedSlotWithoutMovingConfirmedSlots() {
        when(registrations.findByTournamentId(1L)).thenReturn(List.of(Registration.builder().slotNumber(1).build(), Registration.builder().slotNumber(3).build()));
        var entry = Registration.builder().tournament(tournament).build(); service.assignSlot(entry); assertEquals(2, entry.getSlotNumber());
        service.assignSlot(entry); assertEquals(2, entry.getSlotNumber());
    }
    @Test void cannotAllocateBeyondTwentyFive() {
        when(registrations.findByTournamentId(1L)).thenReturn(java.util.stream.IntStream.rangeClosed(1,25).mapToObj(i -> Registration.builder().slotNumber(i).build()).toList());
        assertThrows(BadRequestException.class, () -> service.assignSlot(Registration.builder().tournament(tournament).build()));
    }
}
