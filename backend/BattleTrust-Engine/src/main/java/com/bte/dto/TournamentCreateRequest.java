package com.bte.dto;

import com.bte.enums.GameCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TournamentCreateRequest(
		@NotBlank @Size(max = 150) String title,
		@NotBlank @Size(max = 80) String gameType,
		@NotNull GameCategory gameCategory,
		@NotNull @DecimalMin(value="100.00", message="Entry fee must be at least Rs. 100") @Digits(integer = 10, fraction = 2) BigDecimal entryFeePkr,
		@NotNull @Min(1) Integer maxPlayers,
		@NotNull LocalDateTime startTime,
		@Size(max = 10000) String rules,
		@NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal winnersSharePercent,
		@NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal leaderSharePercent,
		@NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal systemSharePercent,
		@Min(1) @jakarta.validation.constraints.Max(4) Integer squadSize,
		Long stakingVersion,
        @jakarta.validation.constraints.Pattern(regexp="PUBLIC|PRIVATE") String visibility,
        @Size(min=1,max=20) java.util.List<@NotBlank @Size(max=100) String> roundMaps
) {
}
