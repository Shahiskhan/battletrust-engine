package com.bte.controller;

import com.bte.dto.RegistrationConfirmRequest;
import com.bte.dto.RegistrationInitRequest;
import com.bte.dto.RegistrationResponse;
import com.bte.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/registrations")
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping
	public ResponseEntity<RegistrationResponse> initialize(
			@Valid @RequestBody RegistrationInitRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(registrationService.initialize(request));
	}

	@PostMapping("/payment-reference")
	public ResponseEntity<RegistrationResponse> submitPaymentReference(
			@Valid @RequestBody RegistrationConfirmRequest request) {
		return ResponseEntity.ok(registrationService.submitPaymentReference(request));
	}
}
