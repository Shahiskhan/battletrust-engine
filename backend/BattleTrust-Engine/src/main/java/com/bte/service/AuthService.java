package com.bte.service;

import com.bte.dto.LoginRequest;
import com.bte.dto.RegisterRequest;
import com.bte.entity.User;
import com.bte.enums.Role;
import com.bte.exception.BadRequestException;
import com.bte.exception.DuplicateResourceException;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthSessionService authSessionService;
	private final String dummyPasswordHash;
	@org.springframework.beans.factory.annotation.Autowired private EmailVerificationService verification;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AuthSessionService authSessionService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authSessionService = authSessionService;
		byte[] randomPassword = new byte[32];
		SECURE_RANDOM.nextBytes(randomPassword);
		this.dummyPasswordHash = passwordEncoder.encode(
				Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword));
	}

	@Transactional
	public EmailVerificationService.Pending register(RegisterRequest request, String deviceInfo) {
		validateBcryptPasswordLength(request.password());
		String email = normalizeEmail(request.email());
		Role selectedRole=request.role()==null?Role.ROLE_PLAYER:switch(request.role()){case "ROLE_PLAYER" -> Role.ROLE_PLAYER;case "ROLE_ORGANIZER" -> Role.ROLE_ORGANIZER;default -> throw new BadRequestException("Choose ROLE_PLAYER or ROLE_ORGANIZER");};
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateResourceException("An account with this email already exists");
		}

		User user = User.builder()
				.name(request.name().trim())
				.email(email)
				.password(passwordEncoder.encode(request.password()))
				.whatsappNumber(request.whatsappNumber() == null ? null : request.whatsappNumber().trim())
				.role(selectedRole)
				.build();
		User savedUser = userRepository.save(user);
		verification.issue(savedUser);
		return new EmailVerificationService.Pending(email,true,"Check your inbox to verify your email before signing in");
	}

	@Transactional
	public IssuedAuthTokens login(LoginRequest request, String deviceInfo) {
		validateBcryptPasswordLength(request.password());
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmailForUpdate(email).orElse(null);
		if (user == null) {
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			throw new UnauthorizedException("Invalid email or password");
		}
		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new UnauthorizedException("Invalid email or password");
		}
		if(user.isBlocked())throw new UnauthorizedException("Account is blocked. Contact the system administrator");
        if(!user.isEmailVerified() || user.getRole()==Role.ROLE_GUEST)throw new UnauthorizedException("Verify your email before signing in");
		return authSessionService.createSession(user, deviceInfo);
	}

	public IssuedAuthTokens rotateRefreshToken(String refreshToken, String deviceInfo) {
		return authSessionService.rotate(refreshToken, deviceInfo);
	}

	public void logout(String refreshToken, String sessionId) {
		authSessionService.revoke(refreshToken, sessionId);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private void validateBcryptPasswordLength(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new BadRequestException("Password must not exceed 72 UTF-8 bytes");
		}
	}
}
