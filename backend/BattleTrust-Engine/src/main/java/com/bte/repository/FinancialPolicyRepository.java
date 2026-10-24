package com.bte.repository;
import com.bte.entity.FinancialPolicy;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface FinancialPolicyRepository extends JpaRepository<FinancialPolicy, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select f from FinancialPolicy f where f.id = 1")
    java.util.Optional<FinancialPolicy> locked();
}
