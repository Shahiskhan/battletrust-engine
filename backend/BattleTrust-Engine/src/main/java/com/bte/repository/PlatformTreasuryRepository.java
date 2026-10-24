package com.bte.repository;
import com.bte.entity.PlatformTreasury;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface PlatformTreasuryRepository extends JpaRepository<PlatformTreasury, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from PlatformTreasury t where t.id = 1")
    java.util.Optional<PlatformTreasury> locked();
}
