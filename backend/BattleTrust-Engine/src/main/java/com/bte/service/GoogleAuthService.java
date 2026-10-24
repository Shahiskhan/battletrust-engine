package com.bte.service;

import com.bte.entity.User;
import com.bte.enums.Role;
import com.bte.exception.UnauthorizedException;
import com.bte.exception.ServiceUnavailableException;
import com.bte.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class GoogleAuthService {

	private static final String DEFAULT_AVATAR_URL = "/images/default-avatar.svg";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthSessionService authSessionService;
	private final String googleClientId;
	private volatile GoogleIdTokenVerifier verifier;

	public GoogleAuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AuthSessionService authSessionService,
			@Value("${app.google.client-id:}") String googleClientId) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authSessionService = authSessionService;
		this.googleClientId = googleClientId;
	}

	@Transactional
	public IssuedAuthTokens login(String idToken, String deviceInfo) {
		GoogleIdToken.Payload payload = verifyToken(idToken);
		String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmailForUpdate(email).orElseGet(() -> registerGoogleUser(payload, email));
		user.setEmailVerified(true); // Provider ID-token verification above requires email_verified=true.
		userRepository.saveAndFlush(user);
        return authSessionService.createSession(user, deviceInfo);
	}

	private GoogleIdToken.Payload verifyToken(String rawToken) {
		if (googleClientId.isBlank()) {
			throw new ServiceUnavailableException("Google Sign-In is not configured");
		}
		try {
			GoogleIdToken token = getVerifier().verify(rawToken);
			if (token == null
					|| token.getPayload().getEmail() == null
					|| token.getPayload().getEmail().isBlank()
					|| !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
				throw new UnauthorizedException("Google ID token is invalid or email is unverified");
			}
			return token.getPayload();
		} catch (GeneralSecurityException | IOException exception) {
			throw new UnauthorizedException("Google ID token verification failed");
		}
	}

	private GoogleIdTokenVerifier getVerifier() throws GeneralSecurityException, IOException {
		GoogleIdTokenVerifier currentVerifier = verifier;
		if (currentVerifier == null) {
			synchronized (this) {
				currentVerifier = verifier;
				if (currentVerifier == null) {
					currentVerifier = new GoogleIdTokenVerifier.Builder(
							GoogleNetHttpTransport.newTrustedTransport(),
							GsonFactory.getDefaultInstance())
							.setAudience(List.of(googleClientId))
							.build();
					verifier = currentVerifier;
				}
			}
		}
		return currentVerifier;
	}

	private User registerGoogleUser(GoogleIdToken.Payload payload, String email) {
		String name = payload.get("name") instanceof String googleName && !googleName.isBlank()
				? googleName.trim()
				: email.substring(0, email.indexOf('@'));
		String avatar = verifiedPictureUrl(payload.get("picture"));
		byte[] randomPassword = new byte[48];
		SECURE_RANDOM.nextBytes(randomPassword);
		String unusablePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword);

		return userRepository.save(User.builder()
				.name(name.substring(0, Math.min(name.length(), 100)))
				.email(email)
				.password(passwordEncoder.encode(unusablePassword))
				.profilePictureUrl(avatar)
				.role(Role.ROLE_PLAYER)
				.build());
	}

	private String verifiedPictureUrl(Object pictureClaim) {
		if (!(pictureClaim instanceof String picture) || picture.isBlank()) {
			return DEFAULT_AVATAR_URL;
		}
		try {
			URI uri = URI.create(picture);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
					? uri.toString()
					: DEFAULT_AVATAR_URL;
		} catch (IllegalArgumentException exception) {
			return DEFAULT_AVATAR_URL;
		}
	}
}
