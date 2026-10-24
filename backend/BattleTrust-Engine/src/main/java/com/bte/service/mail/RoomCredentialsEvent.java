package com.bte.service.mail;

public record RoomCredentialsEvent(
		String email,
		String captainName,
		String teamName,
		String tournamentTitle,
		String roomId,
		String roomPassword
) {
}
