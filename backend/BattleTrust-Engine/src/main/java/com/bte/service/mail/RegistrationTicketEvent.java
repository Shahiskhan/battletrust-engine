package com.bte.service.mail;

public record RegistrationTicketEvent(
		String email,
		String captainName,
		String teamName,
		String tournamentTitle,
		String ticketCode
) {
}
