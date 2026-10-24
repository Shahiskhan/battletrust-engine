package com.bte.entity;

import com.bte.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
		@Index(name = "idx_users_reputation_score", columnList = "reputation_score"),
		@Index(name = "idx_users_matches_won", columnList = "matches_won")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Builder.Default @Column(nullable=false,columnDefinition="boolean not null default false") private boolean blocked=false;
    @Column(length=1000) private String blockReason;
    private java.time.Instant blockedAt;
    @Column(length=254) private String blockedBy;
    @JsonIgnore @Builder.Default @Column(nullable=false,columnDefinition="boolean not null default false")
    private boolean demoAccount=false;
	@Builder.Default
	@Column(name="email_verified",nullable=false,columnDefinition="boolean not null default false")
	private boolean emailVerified = false;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false, length = 100)
	private String name;

	@NotBlank
	@Email
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@NotBlank
	@JsonIgnore
	@Column(nullable = false, length = 255)
	private String password;

	@Column(name = "whatsapp_number", length = 32)
	private String whatsappNumber;

	@Column(length = 2048)
	private String profilePictureUrl;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Role role = Role.ROLE_PLAYER;

	@Builder.Default
	@Min(0)
	@Column(name = "matches_played", nullable = false, columnDefinition = "int not null default 0")
	private int matchesPlayed = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "matches_won", nullable = false, columnDefinition = "int not null default 0")
	private int matchesWon = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "total_kills", nullable = false, columnDefinition = "int not null default 0")
	private int totalKills = 0;

	@Builder.Default
	@Min(0)
	@Column(name = "reputation_score", nullable = false, columnDefinition = "int not null default 0")
	private int reputationScore = 0;

	@Builder.Default
	@DecimalMin("0.00")
	@Digits(integer = 3, fraction = 2)
	@Column(name = "win_rate_percent", nullable = false, precision = 5, scale = 2,
			columnDefinition = "decimal(5,2) not null default 0.00")
	private BigDecimal winRatePercent = new BigDecimal("0.00");

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
