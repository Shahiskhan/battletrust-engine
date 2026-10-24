package com.bte.controller;
import com.bte.dto.StakingSettingsRequest;
import com.bte.service.StakingSettingsService;
import com.bte.repository.PlatformTreasuryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

@RestController @RequiredArgsConstructor
public class StakingController {
    private final StakingSettingsService settings;
    private final PlatformTreasuryRepository treasury;
    @GetMapping("/api/v1/public/settings/staking")
    public java.util.Map<String, Object> publicSettings() {
        var s = settings.get();
        return java.util.Map.of("enabled", s.isEnabled(), "amount", s.getAmount(), "mode", s.getMode(), "multiplier", s.getMultiplier(), "version", s.getVersion());
    }
    @GetMapping("/api/v1/admin/settings/staking")
    public com.bte.entity.SystemSetting get() { return settings.get(); }
    @PutMapping("/api/v1/admin/settings/staking")
    public com.bte.entity.SystemSetting update(@Valid @RequestBody StakingSettingsRequest request, Authentication auth) { return settings.update(request, auth.getName()); }
    @GetMapping("/api/v1/admin/treasury")
    public java.util.Map<String, Object> treasury() { return java.util.Map.of("balance", treasury.findById(1L).orElseThrow().getBalance(), "currency", "PKR"); }
}
