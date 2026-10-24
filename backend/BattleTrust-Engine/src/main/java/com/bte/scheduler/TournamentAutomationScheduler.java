package com.bte.scheduler;

import com.bte.entity.MatchResult;
import com.bte.entity.Registration;
import com.bte.entity.Tournament;
import com.bte.enums.DisputeStatus;
import com.bte.enums.PaymentStatus;
import com.bte.enums.TournamentStatus;
import com.bte.enums.VerificationStatus;
import com.bte.repository.DisputeRepository;
import com.bte.repository.MatchResultRepository;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.service.mail.RoomCredentialsEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class TournamentAutomationScheduler {
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;

	private final TournamentRepository tournamentRepository;
	private final MatchResultRepository matchResultRepository;
	private final DisputeRepository disputeRepository;
	private final RegistrationRepository registrationRepository;
	private final ApplicationEventPublisher eventPublisher;
	@org.springframework.beans.factory.annotation.Autowired private com.bte.repository.PaymentVoucherRepository vouchers;

	public TournamentAutomationScheduler(
			TournamentRepository tournamentRepository,
			MatchResultRepository matchResultRepository,
			DisputeRepository disputeRepository,
			RegistrationRepository registrationRepository,
			ApplicationEventPublisher eventPublisher) {
		this.tournamentRepository = tournamentRepository;
		this.matchResultRepository = matchResultRepository;
		this.disputeRepository = disputeRepository;
		this.registrationRepository = registrationRepository;
		this.eventPublisher = eventPublisher;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay-ms:60000}")
	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public void transitionStartedTournamentsToLive() {
		LocalDateTime now = LocalDateTime.now();
		for (Tournament candidate : tournamentRepository.findByStatusAndStartTimeLessThanEqual(
				TournamentStatus.UPCOMING, now)) {
			Tournament tournament = tournamentRepository.findForUpdate(candidate.getId()).orElse(null);
			if (tournament != null
					&& tournament.getStatus() == TournamentStatus.UPCOMING
					&& !tournament.getStartTime().isAfter(now)) {
				tournament.setStatus(TournamentStatus.LIVE);
				registrationRepository.findByTournamentId(tournament.getId()).stream().filter(r -> r.getPaymentStatus()==PaymentStatus.PENDING).forEach(r -> r.setPaymentStatus(PaymentStatus.CANCELLED));
				vouchers.findByTournamentIdOrderByInventoryNumberAsc(tournament.getId()).stream().filter(v -> v.getStatus().equals("AVAILABLE") || v.getStatus().equals("PENDING")).forEach(v -> v.setStatus("EXPIRED"));
			}
		}
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay-ms:60000}")
	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public void closeExpiredDisputeWindows() {
		LocalDateTime now = LocalDateTime.now();
		for (MatchResult candidate :
				matchResultRepository.findByDisputeWindowClosedFalseAndDisputeDeadlineLessThanEqual(now)) {
			tournamentRepository.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();
            MatchResult result = matchResultRepository.findByIdForUpdate(candidate.getId()).orElse(null);if(result!=null)em.refresh(result,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
			if (result == null || result.isDisputeWindowClosed()
					|| result.getDisputeDeadline().isAfter(now)) {
				continue;
			}
			long pendingDisputes = disputeRepository.countByMatchResultIdAndStatus(
					result.getId(), DisputeStatus.PENDING);
			result.setDisputeWindowClosed(true);
			if (pendingDisputes == 0) {
				result.setVerificationStatus(VerificationStatus.GREEN_TICK_VERIFIED);
				if (result.getTournament().getPlannedRounds()==0 && result.getTournament().getStatus() == TournamentStatus.UNDER_REVIEW) {
					result.getTournament().setStatus(TournamentStatus.LIVE);
				}
			}
		}
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay-ms:60000}")
	@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public void dispatchRoomCredentials() {
		LocalDateTime now = LocalDateTime.now();
		for (Tournament candidate :
				tournamentRepository.findByStartTimeBetweenAndRoomIdIsNotNullAndRoomPasswordIsNotNullAndRoomCredentialsSentAtIsNull(
						now, now.plusMinutes(15))) {
			Tournament tournament = tournamentRepository.findForUpdate(candidate.getId()).orElse(null);
			if (tournament == null
					|| tournament.getRoomCredentialsSentAt() != null
					|| tournament.getRoomId() == null
					|| tournament.getRoomPassword() == null
					|| !tournament.getStartTime().isAfter(now)
					|| tournament.getStartTime().isAfter(now.plusMinutes(15))) {
				continue;
			}
			for (Registration registration : registrationRepository.findByTournamentIdAndPaymentStatus(
					tournament.getId(), PaymentStatus.PAID)) {
				String email = registration.getCaptainEmail() == null
						? registration.getEmail()
						: registration.getCaptainEmail();
				eventPublisher.publishEvent(new RoomCredentialsEvent(
						email,
						registration.getCaptainName() == null
								? registration.getPlayerName()
								: registration.getCaptainName(),
						registration.getTeamName(),
						tournament.getTitle(),
						tournament.getRoomId(),
						tournament.getRoomPassword()));
			}
			tournament.setRoomCredentialsSentAt(now);
		}
	}
}
