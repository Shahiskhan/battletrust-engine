package com.bte.service.escrow;

import com.bte.entity.LedgerTransaction;
import com.bte.enums.LedgerTransactionType;
import com.bte.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LedgerService {

	private final LedgerTransactionRepository ledgerTransactionRepository;

	public LedgerService(LedgerTransactionRepository ledgerTransactionRepository) {
		this.ledgerTransactionRepository = ledgerTransactionRepository;
	}

	public LedgerTransaction record(
			Long tournamentId,
			Long senderId,
			Long receiverId,
			BigDecimal amount,
			LedgerTransactionType type,
			String referenceId) {
		return ledgerTransactionRepository.save(LedgerTransaction.builder()
				.tournamentId(tournamentId)
				.senderId(senderId)
				.receiverId(receiverId)
				.amount(amount)
				.transactionType(type)
				.referenceId(referenceId)
				.build());
	}
}
