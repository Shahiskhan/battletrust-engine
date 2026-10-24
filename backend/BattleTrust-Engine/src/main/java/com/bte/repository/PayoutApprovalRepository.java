package com.bte.repository;
import com.bte.entity.PayoutApproval;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PayoutApprovalRepository extends JpaRepository<PayoutApproval, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayoutApproval> findByTournamentId(Long tournamentId);
    java.util.List<PayoutApproval> findByStatus(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutApproval p where p.id = :id")
    Optional<PayoutApproval> findLockedById(Long id);
}
