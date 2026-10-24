package com.bte.controller;

import com.bte.dto.DisputeClaimRequest;
import com.bte.dto.DisputeResponse;
import com.bte.service.DisputeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

	private final DisputeService disputeService;

	public DisputeController(DisputeService disputeService) {
		this.disputeService = disputeService;
	}

	@PostMapping(value = "/file-claim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DisputeResponse> fileClaim(
			@Valid @RequestPart("claim") DisputeClaimRequest request,
			@RequestPart("screenshot") MultipartFile screenshot,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(disputeService.fileClaim(authentication.getName(), request, screenshot));
	}
}
