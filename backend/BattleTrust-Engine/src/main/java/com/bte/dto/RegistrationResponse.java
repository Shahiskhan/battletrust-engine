package com.bte.dto;

import com.bte.enums.PaymentStatus;

import java.time.LocalDateTime;

public record RegistrationResponse(
		Long id,
		Long tournamentId,
		String teamName,
		String captainName,
		String captainEmail,
		String playerName,
		String inGameId,
		String email,
		String whatsappNumber,
		java.util.List<SquadMemberRequest> squadMembers,
		PaymentStatus paymentStatus,
		String transactionId,
		String ticketCode,
		LocalDateTime createdAt,
		Integer slotNumber,
		String slotStatus
) {
}
