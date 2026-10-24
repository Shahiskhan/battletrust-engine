package com.bte.security;

import com.bte.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";
	private static final String SESSION_CLAIM = "sid";

	private final SecretKey signingKey;
	private final long accessTokenExpirationMs;
	private final String issuer;

	public JwtTokenProvider(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-expiration-ms:900000}") long accessTokenExpirationMs,
			@Value("${app.jwt.issuer:battletrust-engine}") String issuer) {
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		if (keyBytes.length < 32) {
			throw new IllegalArgumentException("JWT secret must decode to at least 32 bytes");
		}
		if (accessTokenExpirationMs <= 0 || accessTokenExpirationMs > 900_000) {
			throw new IllegalArgumentException("Access token lifetime must be between 1ms and 15 minutes");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("JWT issuer must not be blank");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		this.accessTokenExpirationMs = accessTokenExpirationMs;
		this.issuer = issuer;
	}

	public String generateAccessToken(String email, Role role, String sessionId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.issuer(issuer)
				.subject(email)
				.id(UUID.randomUUID().toString())
				.claim(ROLE_CLAIM, role.name())
				.claim(SESSION_CLAIM, sessionId)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
				.signWith(signingKey)
				.compact();
	}

	public String extractEmail(String token) {
		return extractClaims(token).getSubject();
	}

	public Role extractRole(String token) {
		return Role.valueOf(extractRequiredClaim(token, ROLE_CLAIM));
	}

	public String extractSessionId(String token) {
		return extractRequiredClaim(token, SESSION_CLAIM);
	}

	public boolean validateToken(String token) {
		try {
			Claims claims = extractClaims(token);
			String role = claims.get(ROLE_CLAIM, String.class);
			String sessionId = claims.get(SESSION_CLAIM, String.class);
			if (claims.getSubject() == null || claims.getSubject().isBlank()
					|| claims.getId() == null || claims.getId().isBlank()
					|| sessionId == null || sessionId.isBlank()
					|| role == null || role.isBlank()
					|| !issuer.equals(claims.getIssuer())) {
				return false;
			}
			Role.valueOf(role);
			return true;
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public long getAccessTokenExpirationMs() {
		return accessTokenExpirationMs;
	}

	private String extractRequiredClaim(String token, String claim) {
		String value = extractClaims(token).get(claim, String.class);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("JWT claim is missing: " + claim);
		}
		return value;
	}

	private Claims extractClaims(String token) {
		return Jwts.parser()
				.requireIssuer(issuer)
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
