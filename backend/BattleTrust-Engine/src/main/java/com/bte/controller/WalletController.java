package com.bte.controller;

import com.bte.dto.WalletResponse;
import com.bte.dto.WithdrawalRequestDto;
import com.bte.dto.WithdrawalResponse;
import com.bte.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	public WalletResponse getWallet(
			Authentication authentication,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "30") @Min(1) int size) {
		return walletService.getWallet(authentication.getName(), page, size);
	}

	@PostMapping("/withdraw")
	public ResponseEntity<WithdrawalResponse> requestWithdrawal(
			Authentication authentication,
			@Valid @RequestBody WithdrawalRequestDto request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(walletService.requestWithdrawal(authentication.getName(), request));
	}
}
