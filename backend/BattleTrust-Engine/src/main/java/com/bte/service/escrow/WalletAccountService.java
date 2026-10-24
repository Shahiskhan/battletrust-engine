package com.bte.service.escrow;

import com.bte.entity.User;
import com.bte.entity.Wallet;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.UserRepository;
import com.bte.repository.WalletRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Service
public class WalletAccountService {

	private final WalletRepository walletRepository;
	private final UserRepository userRepository;

	public WalletAccountService(WalletRepository walletRepository, UserRepository userRepository) {
		this.walletRepository = walletRepository;
		this.userRepository = userRepository;
	}

	public Wallet getOrCreateLocked(Long userId) {
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Wallet owner was not found"));
		Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseGet(() ->
				walletRepository.save(Wallet.builder().user(user).build()));
		if (wallet.getAvailableBalance() == null) {
			wallet.setAvailableBalance(BigDecimal.ZERO);
		}
		if (wallet.getLockedBalance() == null) {
			wallet.setLockedBalance(BigDecimal.ZERO);
		}
		if (wallet.getTotalEarned() == null) {
			wallet.setTotalEarned(BigDecimal.ZERO);
		}
		return wallet;
	}

	public Map<Long, Wallet> getOrCreateLockedInOrder(Collection<Long> userIds) {
		Map<Long, Wallet> wallets = new HashMap<>();
		userIds.stream().distinct().sorted(Comparator.naturalOrder())
				.forEach(userId -> wallets.put(userId, getOrCreateLocked(userId)));
		return wallets;
	}
}
