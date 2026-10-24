package com.bte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(
		name = "uk_wallet_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
	@Builder.Default
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 16, scale = 2, columnDefinition = "decimal(16,2) not null default 0.00")
	private BigDecimal organizerStakeBalance = BigDecimal.ZERO;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Builder.Default
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal availableBalance = BigDecimal.ZERO;

	@Builder.Default
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal lockedBalance = BigDecimal.ZERO;

	@Builder.Default
	@DecimalMin("0.00")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal totalEarned = BigDecimal.ZERO;
}
