package com.bte.entity;

import com.bte.enums.ChallengeRoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChallengeRoom {

	@NotBlank
	@Column(name = "room_id", nullable = false, length = 32)
	private String roomId;

	@NotBlank
	@Column(name = "room_password", nullable = false, length = 32)
	private String roomPassword;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "room_status", nullable = false, length = 24)
	private ChallengeRoomStatus status = ChallengeRoomStatus.PENDING;
}
