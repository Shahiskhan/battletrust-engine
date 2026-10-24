package com.bte.service;

import com.bte.dto.AuthResponse;
import com.bte.dto.UserDto;
import com.bte.entity.AuthSession;
import com.bte.entity.RefreshToken;
import com.bte.entity.User;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.AuthSessionRepository;
import com.bte.repository.RefreshTokenRepository;
import com.bte.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthSessionService {
    @org.springframework.beans.factory.annotation.Autowired private com.bte.repository.UserRepository accountUsers;
    @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;

	private static final long REFRESH_TOKEN_LIFETIME_DAYS = 7;
	private static final int REFRESH_TOKEN_BYTES = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final AuthSessionRepository authSessionRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtTokenProvider tokenProvider;
	private final boolean refreshCookieSecure;

	public AuthSessionService(
			AuthSessionRepository authSessionRepository,
			RefreshTokenRepository refreshTokenRepository,
			JwtTokenProvider tokenProvider,
			@Value("${app.auth.cookie-secure:true}") boolean refreshCookieSecure) {
		this.authSessionRepository = authSessionRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.tokenProvider = tokenProvider;
		this.refreshCookieSecure = refreshCookieSecure;
	}

	@Transactional
	public IssuedAuthTokens createSession(User user, String deviceInfo) {
        if(accountUsers!=null){user=accountUsers.findByIdForUpdate(user.getId()).orElseThrow(()->new UnauthorizedException("Account not found"));em.refresh(user,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);}
		requireVerified(user);
		Instant now = Instant.now();
		Instant expiry = now.plus(REFRESH_TOKEN_LIFETIME_DAYS, ChronoUnit.DAYS);
		String sessionId = UUID.randomUUID().toString();
		AuthSession session = authSessionRepository.save(AuthSession.builder()
				.id(sessionId)
				.user(user)
				.deviceInfo(limitDeviceInfo(deviceInfo))
				.expiresAt(expiry)
				.build());
		String refreshToken = newRefreshToken();
		refreshTokenRepository.save(RefreshToken.builder()
				.session(session)
				.tokenHash(hashToken(refreshToken))
				.expiresAt(expiry)
				.build());
		return issueAccessToken(user, sessionId, refreshToken);
	}

	@Transactional(noRollbackFor = UnauthorizedException.class)
	public IssuedAuthTokens rotate(String rawToken, String deviceInfo) {
		if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
			throw new UnauthorizedException("Refresh token is required");
		}
		RefreshToken token = refreshTokenRepository.findByHashForUpdate(hashToken(rawToken))
				.orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));
		AuthSession session = authSessionRepository.findByIdForUpdate(token.getSession().getId())
				.orElseThrow(() -> new UnauthorizedException("Refresh session is invalid"));
		requireVerified(session.getUser());
		Instant now = Instant.now();
		if (token.isRevoked()) {
			revokeSession(session, now);
			throw new UnauthorizedException("Refresh token reuse detected; session revoked");
		}
		if (session.isRevoked() || !session.getExpiresAt().isAfter(now) || !token.getExpiresAt().isAfter(now)) {
			revokeSession(session, now);
			throw new UnauthorizedException("Refresh token has expired or been revoked");
		}

		String replacement = newRefreshToken();
		String replacementHash = hashToken(replacement);
		token.setRevoked(true);
		token.setRevokedAt(now);
		token.setReplacedByHash(replacementHash);
		refreshTokenRepository.save(token);
		refreshTokenRepository.save(RefreshToken.builder()
				.session(session)
				.tokenHash(replacementHash)
				.expiresAt(session.getExpiresAt())
				.build());
		if (deviceInfo != null && !deviceInfo.isBlank()) {
			session.setDeviceInfo(limitDeviceInfo(deviceInfo));
		}
		return issueAccessToken(session.getUser(), session.getId(), replacement);
	}

	@Transactional
	public void revoke(String rawToken, String sessionId) {
		Instant now = Instant.now();
		if (rawToken != null && !rawToken.isBlank()) {
			refreshTokenRepository.findByHashForUpdate(hashToken(rawToken))
					.ifPresent(token -> {
						AuthSession session = authSessionRepository.findByIdForUpdate(token.getSession().getId())
								.orElse(null);
						if (session != null) {
							revokeSession(session, now);
						}
					});
		}
		if (sessionId != null && !sessionId.isBlank()) {
			authSessionRepository.findByIdForUpdate(sessionId)
					.ifPresent(session -> revokeSession(session, now));
		}
	}

	public boolean isRefreshCookieSecure() {
		return refreshCookieSecure;
	}

	private IssuedAuthTokens issueAccessToken(User user, String sessionId, String refreshToken) {
		requireVerified(user);
		String accessToken = tokenProvider.generateAccessToken(user.getEmail(), user.getRole(), sessionId);
		AuthResponse response = new AuthResponse(
				accessToken,
				"Bearer",
				tokenProvider.getAccessTokenExpirationMs() / 1000,
				user.getRole(),
				new UserDto(
						user.getId(),
						user.getName(),
						user.getEmail(),
						user.getRole(),
						user.getProfilePictureUrl()));
		return new IssuedAuthTokens(response, refreshToken);
	}
	private void requireVerified(User user){if(user.isBlocked())throw new UnauthorizedException("Account is blocked");if(!user.isEmailVerified() || user.getRole()==com.bte.enums.Role.ROLE_GUEST)throw new UnauthorizedException("Email verification is required");}

	private void revokeSession(AuthSession session, Instant now) {
		if (!session.isRevoked()) {
			session.setRevoked(true);
			session.setRevokedAt(now);
			authSessionRepository.save(session);
		}
	}

	private String newRefreshToken() {
		byte[] random = new byte[REFRESH_TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(random);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

	private String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String limitDeviceInfo(String deviceInfo) {
		if (deviceInfo == null) {
			return null;
		}
		String normalized = deviceInfo.replaceAll("[\\p{Cntrl}]", " ").trim();
		return normalized.substring(0, Math.min(normalized.length(), 255));
	}
}
