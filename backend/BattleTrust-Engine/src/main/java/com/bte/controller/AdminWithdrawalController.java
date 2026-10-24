package com.bte.controller;

import com.bte.dto.WithdrawalDecisionRequest;
import com.bte.dto.WithdrawalResponse;
import com.bte.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
public class AdminWithdrawalController {

	private final WalletService walletService;

	public AdminWithdrawalController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	public Page<WithdrawalResponse> pendingWithdrawals(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "30") @Min(1) int size) {
		return walletService.listPendingWithdrawals(page, size);
	}

	@PostMapping("/{withdrawalId}/decision")
	public WithdrawalResponse decide(
			@PathVariable @Min(1) Long withdrawalId,
			@Valid @RequestBody WithdrawalDecisionRequest request,
			Authentication authentication) {
		return walletService.decideWithdrawal(
				withdrawalId,
				request.approve(),
				request.decisionNote(),
				authentication.getName());
	}
}
