package com.bte.service;

import com.bte.dto.RegistrationConfirmRequest;
import com.bte.dto.RegistrationInitRequest;
import com.bte.dto.RegistrationResponse;
import com.bte.dto.SquadMemberRequest;
import com.bte.entity.Registration;
import com.bte.entity.SquadMember;
import com.bte.entity.Tournament;
import com.bte.enums.PaymentStatus;
import com.bte.enums.TournamentStatus;
import com.bte.exception.BadRequestException;
import com.bte.exception.DuplicateResourceException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.RegistrationRepository;
import com.bte.repository.TournamentRepository;
import com.bte.service.mail.RegistrationTicketEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegistrationService {

	private static final int SQUAD_SIZE = 4;

	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final ApplicationEventPublisher eventPublisher;
	@org.springframework.beans.factory.annotation.Autowired
	private com.bte.repository.UserRepository users;
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.security.crypto.password.PasswordEncoder encoder;
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<VoucherService> vouchers;
	@org.springframework.beans.factory.annotation.Autowired
	private RegistrationFundingService funding;
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<SquadWalletService> squadWallets;

	public RegistrationService(
			RegistrationRepository registrationRepository,
			TournamentRepository tournamentRepository,
			ApplicationEventPublisher eventPublisher) {
		this.registrationRepository = registrationRepository;
		this.tournamentRepository = tournamentRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public RegistrationResponse initialize(RegistrationInitRequest request) {
		Tournament tournament = tournamentRepository.findByIdForUpdate(request.tournamentId())
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		if(!tournament.isRegistrationOpen())throw new BadRequestException("Registration is closed by the organizer");
        if(tournament.isArchived())throw new ResourceNotFoundException("Tournament not found");
        if("PRIVATE".equals(tournament.getVisibility()) && (tournament.getRoomInviteToken()==null || !java.util.Objects.equals(tournament.getRoomInviteToken(),request.roomInviteToken())))throw new UnauthorizedException("Room invite required");
        if (tournament.getStatus() != TournamentStatus.UPCOMING) {
			throw new BadRequestException("Registration is only available for upcoming tournaments");
		}
		if (!tournament.getStartTime().isAfter(java.time.LocalDateTime.now())) {
			throw new BadRequestException("Registration is locked after the start time");
		}
		vouchers.getObject().expireReservations(tournament);
		if (request.squadMembers().size() != 1 && request.squadMembers().size() != tournament.getSquadSize()) throw new BadRequestException("This room requires exactly " + tournament.getSquadSize() + " players per squad");
		if (request.squadMembers().stream().map(m -> m.inGameId().trim().toLowerCase(java.util.Locale.ROOT)).distinct().count() != request.squadMembers().size())
			throw new BadRequestException("Squad player UIDs must be unique");
		int registeredPlayers = registrationRepository.findByTournamentId(tournament.getId()).stream()
				.filter(r -> r.getPaymentStatus() == PaymentStatus.PENDING || r.getPaymentStatus() == PaymentStatus.PAID)
				.mapToInt(Registration::reservedPlayers).sum();
		if (registeredPlayers + tournament.getSquadSize() > tournament.getMaxPlayers()) {
			throw new BadRequestException("Tournament has no remaining squad slots");
		}

		List<SquadMember> members = request.squadMembers().stream()
				.map(member -> new SquadMember(member.name().trim(), member.inGameId().trim()))
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		var guest = users.save(com.bte.entity.User.builder().name(request.captainName().trim())
				.email(UUID.randomUUID() + "@guest.battletrust.invalid")
				.password(encoder.encode(UUID.randomUUID().toString()))
				.role(com.bte.enums.Role.ROLE_GUEST).build());
		Registration registration = Registration.builder()
				.ownerUserId(guest.getId()).guestAccountId(guest.getId())
				.tournament(tournament)
				.teamName(request.teamName().trim())
				.captainName(request.captainName().trim())
				.captainEmail(normalizeEmail(request.captainEmail()))
				.whatsappNumber(request.whatsappNumber().trim())
				.playerName(request.captainName().trim())
				.inGameId(members.getFirst().getInGameId())
				.email(normalizeEmail(request.captainEmail()))
				.squadMembers(members)
				.squadInviteToken(request.squadMembers().size()==1 && tournament.getSquadSize()>1?UUID.randomUUID().toString():null)
.ticketCode(UUID.randomUUID().toString())
				.paymentStatus(tournament.getEntryFeePkr().signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PENDING)
				.build();
		Registration savedRegistration = registrationRepository.save(registration);
		squadWallets.getObject().initialize(savedRegistration);
		if (savedRegistration.getPaymentStatus() == PaymentStatus.PAID) funding.assignSlot(savedRegistration);
		vouchers.getObject().create(savedRegistration);
		eventPublisher.publishEvent(new RegistrationTicketEvent(
				savedRegistration.getCaptainEmail(),
				savedRegistration.getCaptainName(),
				savedRegistration.getTeamName(),
				tournament.getTitle(),
				savedRegistration.getTicketCode()));
		return toResponse(savedRegistration);
	}

	@Transactional
	public RegistrationResponse submitPaymentReference(RegistrationConfirmRequest request) {
		Registration registration = registrationRepository.findByIdForUpdate(request.registrationId())
				.orElseThrow(() -> new ResourceNotFoundException("Registration was not found"));
		if (!Objects.equals(registration.getTicketCode(), request.ticketCode())) {
			throw new UnauthorizedException("Invalid registration ticket");
		}
		if (registration.getPaymentStatus() != PaymentStatus.PENDING) {
			throw new BadRequestException("Only pending entries accept payment references");
		}
		String transactionId = request.transactionId().trim();
		if (registration.getTransactionId() != null
				&& registration.getTransactionId().equals(transactionId)) {
			return toResponse(registration);
		}
		if (registrationRepository.existsByTransactionId(transactionId)) {
			throw new DuplicateResourceException("This transaction reference has already been submitted");
		}
		registration.setTransactionId(transactionId);
		// A client-supplied reference is recorded for verification; it is not proof of payment.
		return toResponse(registrationRepository.save(registration));
	}

	public RegistrationResponse toResponse(Registration registration) {
		List<SquadMemberRequest> members = registration.getSquadMembers().stream()
				.map(member -> new SquadMemberRequest(member.getName(), member.getInGameId()))
				.toList();
		return new RegistrationResponse(
				registration.getId(),
				registration.getTournament().getId(),
				registration.getTeamName(),
				registration.getCaptainName(),
				registration.getCaptainEmail(),
				registration.getPlayerName(),
				registration.getInGameId(),
				registration.getEmail(),
				registration.getWhatsappNumber(),
				members,
				registration.getPaymentStatus(),
				registration.getTransactionId(),
				registration.getTicketCode(),
				registration.getCreatedAt(), registration.getSlotNumber(), registration.getPaymentStatus() == PaymentStatus.PAID ? "CONFIRMED" : registration.getPaymentStatus().name());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
