package com.bte.service;

import com.bte.dto.RoomCredentialsRequest;
import com.bte.entity.Tournament;
import com.bte.enums.TournamentStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomCredentialService {

	private final TournamentRepository tournamentRepository;

	public RoomCredentialService(TournamentRepository tournamentRepository) {
		this.tournamentRepository = tournamentRepository;
	}

	@Transactional
	public void setCredentials(Long tournamentId, String leaderEmail, RoomCredentialsRequest request) {
		Tournament tournament = tournamentRepository.findForUpdate(tournamentId)
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		if (!tournament.getLeader().getEmail().equalsIgnoreCase(leaderEmail)) {
			throw new com.bte.exception.UnauthorizedException(
					"Only the tournament leader can set room credentials");
		}
		if (tournament.getStatus() == TournamentStatus.COMPLETED
				|| tournament.getStatus() == TournamentStatus.CANCELLED
				|| tournament.getStartTime().isBefore(java.time.LocalDateTime.now())) {
			throw new BadRequestException("Room credentials can only be set before the tournament starts");
		}
		if (tournament.getRoomCredentialsSentAt() != null) {
			throw new BadRequestException("Room credentials have already been dispatched");
		}
		tournament.setRoomId(request.roomId().trim());
		tournament.setRoomPassword(request.roomPassword().trim());
	}
}
