package com.bte.security;

import com.bte.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

	private static final String SECRET =
			Base64.getEncoder().encodeToString("test-secret-with-at-least-thirty-two-bytes".getBytes());

	@Test
	void generatedTokenContainsEmailAndRole() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000, "test-issuer");

		String token = provider.generateAccessToken("player@example.com", Role.ROLE_PLAYER, "test-session");

		assertTrue(provider.validateToken(token));
		assertEquals("player@example.com", provider.extractEmail(token));
		assertEquals(Role.ROLE_PLAYER, provider.extractRole(token));
		assertEquals("test-session", provider.extractSessionId(token));
	}

	@Test
	void rejectsMalformedToken() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000, "test-issuer");

		assertFalse(provider.validateToken("not-a-jwt"));
	}

	@Test
	void rejectsAccessTokenLifetimeAboveFifteenMinutes() {
		org.junit.jupiter.api.Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> new JwtTokenProvider(SECRET, 900_001, "test-issuer"));
	}
}
