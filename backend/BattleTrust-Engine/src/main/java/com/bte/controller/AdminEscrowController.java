package com.bte.controller;

import com.bte.dto.EscrowSettlementRequest;
import com.bte.dto.EscrowSettlementResponse;
import com.bte.dto.RegistrationPaymentVerificationRequest;
import com.bte.dto.RegistrationResponse;
import com.bte.service.escrow.EscrowSettlementService;
import com.bte.service.escrow.PaymentEscrowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminEscrowController {
    @org.springframework.beans.factory.annotation.Autowired private com.bte.repository.PayoutApprovalRepository approvals;
    @org.springframework.beans.factory.annotation.Autowired private com.bte.repository.TournamentRepository tournaments;
    @org.springframework.beans.factory.annotation.Autowired private AdminPlatformController adminReview;
    @org.springframework.beans.factory.annotation.Autowired private tools.jackson.databind.ObjectMapper mapper;

	private final PaymentEscrowService paymentEscrowService;
	private final EscrowSettlementService settlementService;

	public AdminEscrowController(
			PaymentEscrowService paymentEscrowService,
			EscrowSettlementService settlementService) {
		this.paymentEscrowService = paymentEscrowService;
		this.settlementService = settlementService;
	}

	@PostMapping("/registrations/{registrationId}/verify-payment")
	public ResponseEntity<RegistrationResponse> verifyPayment(
			@PathVariable @Min(1) Long registrationId,
			@Valid @RequestBody RegistrationPaymentVerificationRequest request) {
		return ResponseEntity.ok(paymentEscrowService.verifyRegistrationPayment(
				registrationId, request.transactionId()));
	}

	@PostMapping("/tournaments/{tournamentId}/settle")
    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
	public EscrowSettlementResponse settleTournament(
			@PathVariable @Min(1) Long tournamentId,
			@Valid @RequestBody EscrowSettlementRequest request,
			Authentication authentication) {
        tournaments.findByIdForUpdate(tournamentId).orElseThrow(()->new com.bte.exception.ResourceNotFoundException("Tournament not found"));
        var declaration=approvals.findByTournamentId(tournamentId).orElseThrow(()->new com.bte.exception.BadRequestException("Organizer winner declaration is required before release"));
        var declared=mapper.readValue(declaration.getPayload(),EscrowSettlementRequest.class);
        if(!com.bte.service.PayoutDeclarationRules.sameWinners(request,declared) || (request.overrideReason()!=null&&!request.overrideReason().isBlank()))throw new com.bte.exception.BadRequestException("Release the organizer's declared winners through Admin review");
        return adminReview.approve(declaration.getId(),authentication);
	}
}
