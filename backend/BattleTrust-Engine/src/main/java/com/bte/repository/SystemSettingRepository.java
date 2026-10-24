package com.bte.repository;
import com.bte.entity.SystemSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SystemSetting s where s.id = 'organizer.staking'")
    java.util.Optional<SystemSetting> stakingForUpdate();
}
