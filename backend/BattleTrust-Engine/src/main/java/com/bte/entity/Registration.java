package com.bte.entity;

import com.bte.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "registrations", uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_tournament_confirmed_slot", columnNames = {"tournament_id", "slot_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {
    @Column(unique=true,length=36)
    private String squadInviteToken;
    public int reservedPlayers(){return squadInviteToken==null?squadMembers.size():tournament.getSquadSize();}
	@Column(name = "slot_number")
	private Integer slotNumber;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Linked only by an authenticated join or possession of the private registration ticket.
	@Column(name = "owner_user_id")
	private Long ownerUserId;
	@Column(name = "guest_account_id")
	private Long guestAccountId;
	private java.time.Instant claimedAt;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@Column(length = 100)
	private String teamName;

	@Column(length = 100)
	private String captainName;

	@Email
	@Column(length = 254)
	private String captainEmail;

	@NotBlank
	@Column(nullable = false, length = 100)
	private String playerName;

	@NotBlank
	@Column(nullable = false, length = 100)
	private String inGameId;

	@NotBlank
	@Email
	@Column(nullable = false, length = 254)
	private String email;

	@NotBlank
	@Column(nullable = false, length = 32)
	private String whatsappNumber;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private PaymentStatus paymentStatus = PaymentStatus.PENDING;

	@Column(unique = true, length = 128)
	private String transactionId;

	@Column(unique = true, length = 36)
	private String ticketCode;

	@Builder.Default
	@Column(nullable = false)
	private boolean fundsCredited = false;

	@ElementCollection
	@Builder.Default
	@CollectionTable(name = "registration_squad_members",
			joinColumns = @JoinColumn(name = "registration_id"))
	@OrderColumn(name = "member_order")
	private List<SquadMember> squadMembers = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
