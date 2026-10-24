package com.bte.entity;

import com.bte.enums.GameCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "player_category_stats", uniqueConstraints = @UniqueConstraint(
		name = "uk_player_category_stats_user_category",
		columnNames = {"user_id", "game_category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerCategoryStats {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "game_category", nullable = false, length = 32)
	private GameCategory gameCategory;

	@Builder.Default
	@Min(0)
	@Column(name = "matches_played", nullable = false)
	private int matchesPlayed = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "matches_won", nullable = false)
	private int matchesWon = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "total_kills", nullable = false)
	private int totalKills = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "reputation_score", nullable = false)
	private int reputationScore = 0;

	@Builder.Default
	@DecimalMin("0.00")
	@Digits(integer = 3, fraction = 2)
	@Column(name = "win_rate_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal winRatePercent = new BigDecimal("0.00");
}
