package com.bte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "platform_vaults",
		uniqueConstraints = @UniqueConstraint(name = "uk_vault_tournament", columnNames = "tournament_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformVault {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@NotNull
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal totalCollectedPkr;

	@NotNull
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal winnersShare;

	@NotNull
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal leaderShare;

	@NotNull
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal systemShare;

	@Builder.Default
	@Column(nullable = false)
	private Boolean isDisbursed = false;

	@Column(columnDefinition = "TEXT")
	private String settlementReason;

	private Instant settledAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settled_by")
	private User settledBy;
}
