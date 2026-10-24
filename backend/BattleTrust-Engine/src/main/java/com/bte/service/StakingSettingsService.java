package com.bte.service;
import com.bte.dto.StakingSettingsRequest;
import com.bte.entity.SystemSetting;
import com.bte.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;

@Service @RequiredArgsConstructor
public class StakingSettingsService {
    private final SystemSettingRepository settings;
    @Transactional(readOnly = true)
    public SystemSetting get() { return settings.findById("organizer.staking").orElseThrow(() -> new IllegalStateException("Staking settings not initialized")); }
    @Transactional
    public SystemSetting update(StakingSettingsRequest request, String admin) {
        var setting = locked(); setting.setEnabled(request.enabled()); setting.setAmount(request.amount());
        setting.setMode(request.mode()); setting.setMultiplier(request.multiplier()); setting.setUpdatedBy(admin); setting.setUpdatedAt(java.time.Instant.now());
        return setting;
    }
    public SystemSetting locked() { return settings.stakingForUpdate().orElseThrow(() -> new IllegalStateException("Staking settings not initialized")); }
    public BigDecimal quote(SystemSetting s, BigDecimal fee) {
        BigDecimal amount = !s.isEnabled() ? BigDecimal.ZERO.setScale(2) : (s.getMode().equals("FIXED") ? s.getAmount() : fee.multiply(s.getMultiplier())).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) throw new com.bte.exception.BadRequestException("Calculated stake exceeds the supported money limit");
        return amount;
    }
}
