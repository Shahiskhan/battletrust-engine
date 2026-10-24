package com.bte.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record StakingSettingsRequest(@NotNull Boolean enabled,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
    @NotBlank @Pattern(regexp = "FIXED|ENTRY_FEE_MULTIPLIER") String mode,
    @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal multiplier) {
    public StakingSettingsRequest {
        if (mode == null) mode = "FIXED";
        if (multiplier == null) multiplier = BigDecimal.ONE;
    }
}
