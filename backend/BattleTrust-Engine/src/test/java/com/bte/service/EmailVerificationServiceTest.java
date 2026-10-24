package com.bte.service;
import com.bte.entity.*;import com.bte.repository.*;import com.bte.exception.*;
import org.junit.jupiter.api.*;import java.time.Instant;import java.util.*;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class EmailVerificationServiceTest {
 final EmailVerificationRepository tokens=mock(EmailVerificationRepository.class);final UserRepository users=mock(UserRepository.class);final VerificationMailService mail=mock(VerificationMailService.class);final EmailVerificationService service=new EmailVerificationService(tokens,users,mail,mock(jakarta.persistence.EntityManager.class));
 final User user=User.builder().id(1L).email("test@example.com").build();final String raw=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
 EmailVerification record(){var r=new EmailVerification();r.setUserId(1L);r.setTokenHash(EmailVerificationService.hash(raw));r.setExpiresAt(Instant.now().plusSeconds(1800));r.setLastSentAt(Instant.now());when(tokens.findByTokenHash(r.getTokenHash())).thenReturn(Optional.of(r));when(tokens.locked(1L)).thenReturn(Optional.of(r));when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));return r;}
 @Test void storedTokenIsHashedAndNeverIssuedAsAuthentication(){when(tokens.locked(1L)).thenReturn(Optional.empty());service.issue(user);var token=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(eq("test@example.com"),token.capture());var saved=org.mockito.ArgumentCaptor.forClass(EmailVerification.class);verify(tokens).save(saved.capture());assertEquals(43,token.getValue().length());assertEquals(EmailVerificationService.hash(token.getValue()),saved.getValue().getTokenHash());assertNotEquals(token.getValue(),saved.getValue().getTokenHash());assertFalse(user.isEmailVerified());}
 @Test void validTokenVerifiesExactlyOnce(){var r=record();service.verify(raw);assertTrue(user.isEmailVerified());assertNotNull(r.getUsedAt());assertThrows(BadRequestException.class,()->service.verify(raw));}
 @Test void expiredTokenCannotVerifyAccount(){var r=record();r.setExpiresAt(Instant.now().minusSeconds(1));assertThrows(BadRequestException.class,()->service.verify(raw));assertFalse(user.isEmailVerified());}
 @Test void resendCooldownPreventsRepeatedMail(){record();when(users.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));service.resend("test@example.com");verifyNoInteractions(mail);}
 @Test void placeholderSmtpFailsClosed(){var sender=mock(org.springframework.mail.javamail.JavaMailSender.class);var delivery=new VerificationMailService(sender,"your_email_here","http://localhost:3000");assertThrows(ServiceUnavailableException.class,()->delivery.send("test@example.com",raw));verifyNoInteractions(sender);}
}
