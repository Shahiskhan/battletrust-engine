package com.bte.repository;
import com.bte.entity.EmailVerification;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface EmailVerificationRepository extends JpaRepository<EmailVerification,Long> {
    java.util.Optional<EmailVerification> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from EmailVerification v where v.userId=:id")
    java.util.Optional<EmailVerification> locked(Long id);
}
