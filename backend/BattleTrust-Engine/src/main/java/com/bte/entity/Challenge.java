package com.bte.entity;

import com.bte.enums.ChallengeStatus;
import com.bte.enums.GameCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenges", indexes = {
		@Index(name = "idx_challenges_challenger_status", columnList = "challenger_id,status"),
		@Index(name = "idx_challenges_opponent_status", columnList = "opponent_id,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenger_id", nullable = false)
	private User challenger;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opponent_id", nullable = false)
	private User opponent;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private GameCategory gameCategory;

	@Column(length = 500)
	private String message;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ChallengeStatus status = ChallengeStatus.PENDING;

	@Valid
	@Embedded
	private ChallengeRoom room;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime respondedAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
