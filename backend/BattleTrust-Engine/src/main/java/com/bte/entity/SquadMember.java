package com.bte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SquadMember {
	@com.fasterxml.jackson.annotation.JsonIgnore
	@Column(name="wallet_user_id")
	private Long walletUserId;
	@com.fasterxml.jackson.annotation.JsonIgnore
	@Column(name="original_guest_user_id")
	private Long originalGuestUserId;
	@com.fasterxml.jackson.annotation.JsonIgnore
	@Column(name="member_claim_ticket", length=36, unique=true)
	private String claimTicket;
	public SquadMember(String name, String inGameId) { this.name=name; this.inGameId=inGameId; }

	@NotBlank
	@Size(max = 100)
	@Column(name = "member_name", nullable = false, length = 100)
	private String name;

	@NotBlank
	@Size(max = 100)
	@Column(name = "in_game_id", nullable = false, length = 100)
	private String inGameId;
}
