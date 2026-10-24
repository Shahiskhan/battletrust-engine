package com.bte.service;
import com.bte.entity.*;
import com.bte.repository.*;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationRepository tokens;
    private final UserRepository users;
    private final VerificationMailService mail;
    private final jakarta.persistence.EntityManager em;
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    public record Pending(String email,boolean verificationRequired,String message){}
    public void issue(User user) {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var record=tokens.locked(user.getId()).orElseGet(EmailVerification::new);
        record.setUserId(user.getId());record.setTokenHash(hash(token));record.setExpiresAt(Instant.now().plusSeconds(1800));record.setLastSentAt(Instant.now());record.setUsedAt(null);
        tokens.save(record);mail.send(user.getEmail(),token);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void verify(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw new BadRequestException("Verification link is invalid or expired");
        String hash=hash(token);var candidate=tokens.findByTokenHash(hash).orElseThrow(()->new BadRequestException("Verification link is invalid or expired"));
        var user=users.findByIdForUpdate(candidate.getUserId()).orElseThrow();var record=tokens.locked(user.getId()).orElseThrow();em.refresh(record,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!java.security.MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),record.getTokenHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII)) || record.getUsedAt()!=null || !record.getExpiresAt().isAfter(Instant.now()))throw new BadRequestException("Verification link is invalid or expired");
        user.setEmailVerified(true);record.setUsedAt(Instant.now());
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void resend(String email) {
        var user=users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if(user==null || user.isEmailVerified() || user.getRole()==com.bte.enums.Role.ROLE_GUEST)return;
        var current=tokens.locked(user.getId()).orElse(null);
        if(current!=null && current.getLastSentAt().isAfter(Instant.now().minusSeconds(60)))return;
        issue(user);
    }
    public static String hash(String token){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
