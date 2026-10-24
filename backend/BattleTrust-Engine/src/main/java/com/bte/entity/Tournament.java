package com.bte.entity;

import com.bte.enums.TournamentStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tournaments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament {
    @Builder.Default @Column(nullable=false,columnDefinition="int not null default 0") private int plannedRounds=0;
    @Builder.Default @Column(nullable=false,columnDefinition="boolean not null default true") private boolean registrationOpen=true;
    @Builder.Default @Column(nullable=false,columnDefinition="boolean not null default false")
    private boolean archived=false;
    @Builder.Default @Column(nullable=false,columnDefinition="varchar(16) not null default 'PUBLIC'")
    private String visibility="PUBLIC";
    @Builder.Default @Column(nullable=false,columnDefinition="boolean not null default true")
    private boolean roomChatEnabled=true;
    @Column(unique=true,length=36)
    private String roomInviteToken;
	@Builder.Default
	@Column(nullable=false, columnDefinition="boolean not null default false")
	private boolean automaticPayoutsEnabled = false;

	@Builder.Default
	@Column(nullable = false, columnDefinition = "int not null default 1")
	private Integer squadSize = 1;
	@Builder.Default
	@Column(nullable = false, precision = 12, scale = 2, columnDefinition = "decimal(12,2) not null default 0.00")
	private BigDecimal organizerStakeAmount = BigDecimal.ZERO;
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, columnDefinition = "varchar(40) not null default 'NONE'")
	private com.bte.enums.OrganizerStakeStatus organizerStakeStatus = com.bte.enums.OrganizerStakeStatus.NONE;
	private String cancellationReason;
	private String cancelledBy;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false, length = 150)
	private String title;

	@NotBlank
	@Column(nullable = false, length = 80)
	private String gameType;

	@Enumerated(EnumType.STRING)
	@Column(length = 32)
	private GameCategory gameCategory;

	@Column(length = 2048)
	private String tournamentLogoUrl;

	@Column(columnDefinition = "TEXT")
	private String rules;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "leader_id", nullable = false)
	private User leader;

	@NotNull
	@DecimalMin("0.00")
	@Digits(integer = 10, fraction = 2)
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal entryFeePkr;

	@NotNull
	@Min(1)
	@Column(nullable = false)
	private Integer maxPlayers;

	@NotNull
	@Column(nullable = false)
	private LocalDateTime startTime;

	@Column(length = 100)
	private String roomId;

	@Column(length = 255)
	private String roomPassword;

	private LocalDateTime roomCredentialsSentAt;

	@DecimalMin("0.00")
	@Column(precision = 5, scale = 2)
	private BigDecimal winnersSharePercent;

	@DecimalMin("0.00")
	@Column(precision = 5, scale = 2)
	private BigDecimal leaderSharePercent;

	@DecimalMin("0.00")
	@Column(precision = 5, scale = 2)
	private BigDecimal systemSharePercent;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TournamentStatus status = TournamentStatus.UPCOMING;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
