package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class VoucherExpiryTest {
    final PaymentVoucherRepository vouchers=mock(PaymentVoucherRepository.class);
    final VoucherService service=new VoucherService(vouchers,mock(RegistrationRepository.class),mock(TournamentRepository.class),mock(UserRepository.class),mock(RegistrationFundingService.class),mock(RegistrationService.class),mock(jakarta.persistence.EntityManager.class),mock(org.springframework.core.env.Environment.class),mock(TournamentAccessService.class));
    final Tournament room=Tournament.builder().id(1L).status(TournamentStatus.UPCOMING).startTime(java.time.LocalDateTime.now().plusDays(1)).squadSize(4).entryFeePkr(new java.math.BigDecimal("100")).build();
    PaymentVoucher expired(Registration entry){var v=new PaymentVoucher();v.setId("1234567890");v.setRegistration(entry);v.setTournament(room);v.setInventoryNumber(1);v.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));when(vouchers.findByTournamentIdOrderByInventoryNumberAsc(1L)).thenReturn(List.of(v));return v;}
    @Test void expiredUnpaidReservationGetsNewCodeWithoutReusingOldId(){var r=Registration.builder().paymentStatus(PaymentStatus.PENDING).build();var old=expired(r);service.expireReservations(room);assertEquals(PaymentStatus.CANCELLED,r.getPaymentStatus());assertEquals("EXPIRED",old.getStatus());assertNull(old.getInventoryNumber());var argument=org.mockito.ArgumentCaptor.forClass(PaymentVoucher.class);verify(vouchers).save(argument.capture());assertEquals(1,argument.getValue().getInventoryNumber());assertNotEquals(old.getId(),argument.getValue().getId());assertEquals("AVAILABLE",argument.getValue().getStatus());}
    @Test void submittedBankReferenceIsRetainedForAdministratorReview(){var r=Registration.builder().transactionId("pending-bank-reference").paymentStatus(PaymentStatus.PENDING).build();var old=expired(r);service.expireReservations(room);assertEquals(PaymentStatus.PENDING,r.getPaymentStatus());assertEquals("PENDING",old.getStatus());verify(vouchers,never()).save(any());}
}
