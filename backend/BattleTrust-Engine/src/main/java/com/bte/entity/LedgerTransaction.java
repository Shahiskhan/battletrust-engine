package com.bte.entity;

import com.bte.enums.LedgerTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_transactions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_ledger_type_reference", columnNames = {"transaction_type", "reference_id"}),
		indexes = @Index(name = "idx_ledger_tournament_created", columnList = "tournament_id, created_at"))
@Getter
@NoArgsConstructor
public class LedgerTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tournament_id")
	private Long tournamentId;

	@Column(name = "sender_id")
	private Long senderId;

	@Column(name = "receiver_id")
	private Long receiverId;

	@NotNull
	@DecimalMin(value = "0.01")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 32)
	private LedgerTransactionType transactionType;

	@NotNull
	@Column(name = "reference_id", nullable = false, length = 128)
	private String referenceId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public LedgerTransaction(
			Long tournamentId,
			Long senderId,
			Long receiverId,
			BigDecimal amount,
			LedgerTransactionType transactionType,
			String referenceId) {
		this.tournamentId = tournamentId;
		this.senderId = senderId;
		this.receiverId = receiverId;
		this.amount = amount;
		this.transactionType = transactionType;
		this.referenceId = referenceId;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	@PreUpdate
	@PreRemove
	void preventMutation() {
		throw new UnsupportedOperationException("Ledger transactions are immutable");
	}
}
