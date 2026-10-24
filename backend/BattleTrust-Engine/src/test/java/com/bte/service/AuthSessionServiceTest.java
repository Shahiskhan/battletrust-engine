package com.bte.service;

import com.bte.entity.AuthSession;
import com.bte.entity.RefreshToken;
import com.bte.entity.User;
import com.bte.enums.Role;
import com.bte.exception.UnauthorizedException;
import com.bte.repository.AuthSessionRepository;
import com.bte.repository.RefreshTokenRepository;
import com.bte.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

	private static final String SECRET =
			Base64.getEncoder().encodeToString("test-secret-with-at-least-thirty-two-bytes".getBytes());

	@Mock
	private AuthSessionRepository sessionRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private JwtTokenProvider jwtTokenProvider;
	private AuthSessionService authSessionService;
	private AuthSession session;
	private RefreshToken activeToken;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(SECRET, 900_000, "test-issuer");
		authSessionService = new AuthSessionService(
				sessionRepository, refreshTokenRepository, jwtTokenProvider, true);
		User user = User.builder().emailVerified(true)
				.id(7L)
				.name("Player")
				.email("player@example.com")
				.role(Role.ROLE_PLAYER)
				.build();
		session = AuthSession.builder()
				.id("session-id")
				.user(user)
				.expiresAt(Instant.now().plusSeconds(600))
				.build();
		activeToken = RefreshToken.builder()
				.id(1L)
				.session(session)
				.tokenHash("stored-hash")
				.expiresAt(session.getExpiresAt())
				.build();
	}

	@Test
	void rotatesRefreshTokenAndRevokesPreviousToken() {
		when(refreshTokenRepository.findByHashForUpdate(anyString())).thenReturn(Optional.of(activeToken));
		when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

		IssuedAuthTokens issued = authSessionService.rotate("A".repeat(43), "device");

		assertTrue(jwtTokenProvider.validateToken(issued.response().token()));
		assertNotEquals("A".repeat(43), issued.refreshToken());
		assertTrue(activeToken.isRevoked());
		assertEquals(64, activeToken.getReplacedByHash().length());
		assertNotEquals(issued.refreshToken(), activeToken.getReplacedByHash());
		ArgumentCaptor<RefreshToken> replacement = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(replacement.capture());
		assertFalse(replacement.getAllValues().getLast().isRevoked());
	}

	@Test
	void reuseRevokesTheEntireSession() {
		activeToken.setRevoked(true);
		when(refreshTokenRepository.findByHashForUpdate(anyString())).thenReturn(Optional.of(activeToken));
		when(sessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));

		assertThrows(UnauthorizedException.class,
				() -> authSessionService.rotate("A".repeat(43), "device"));

		assertTrue(session.isRevoked());
		verify(sessionRepository).save(session);
	}

	@Test
	void rejectsMalformedRefreshTokensBeforeDatabaseLookup() {
		assertThrows(UnauthorizedException.class,
				() -> authSessionService.rotate("invalid", "device"));

		org.mockito.Mockito.verifyNoInteractions(refreshTokenRepository);
	}
}

