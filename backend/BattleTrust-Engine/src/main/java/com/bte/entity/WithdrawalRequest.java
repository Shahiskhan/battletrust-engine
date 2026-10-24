package com.bte.entity;

import com.bte.enums.WithdrawalPaymentMethod;
import com.bte.enums.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "withdrawal_requests",
		indexes = @Index(name = "idx_withdrawal_status_requested", columnList = "status, requested_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@NotNull
	@DecimalMin("0.01")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private WithdrawalPaymentMethod paymentMethod;

	@NotBlank
	@Column(nullable = false, length = 1000)
	private String accountDetails;

	@Builder.Default
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private WithdrawalStatus status = WithdrawalStatus.PENDING;

	@Column(nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(length = 1000)
	private String decisionNote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;

	private Instant reviewedAt;

	@PrePersist
	void onCreate() {
		if (requestedAt == null) {
			requestedAt = Instant.now();
		}
	}
}
