package com.bte.controller;

import com.bte.dto.DisputeResolveRequest;
import com.bte.service.DisputeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/disputes")
public class AdminDisputeController {

	private final DisputeService disputeService;

	public AdminDisputeController(DisputeService disputeService) {
		this.disputeService = disputeService;
	}

	@PostMapping("/{disputeId}/resolve")
	public DisputeService.DisputeResolution resolve(
			@PathVariable @Min(1) Long disputeId,
			@Valid @RequestBody DisputeResolveRequest request,
			Authentication authentication) {
		return disputeService.resolve(disputeId, request, authentication.getName());
	}
}
