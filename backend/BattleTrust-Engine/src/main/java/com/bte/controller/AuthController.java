package com.bte.controller;

import com.bte.dto.AuthResponse;
import com.bte.dto.GoogleLoginRequest;
import com.bte.dto.LoginRequest;
import com.bte.dto.MobileAuthResponse;
import com.bte.dto.RegisterRequest;
import com.bte.exception.UnauthorizedException;
import com.bte.service.AuthService;
import com.bte.service.AuthSessionService;
import com.bte.service.GoogleAuthService;
import com.bte.service.IssuedAuthTokens;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private static final String REFRESH_COOKIE_NAME = "bte_refresh";
	private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private final AuthService authService;
	private final AuthSessionService authSessionService;
	private final GoogleAuthService googleAuthService;
	private final String allowedWebOrigin;
	@org.springframework.beans.factory.annotation.Autowired private com.bte.service.EmailVerificationService verification;

	public AuthController(
			AuthService authService,
			AuthSessionService authSessionService,
			GoogleAuthService googleAuthService,
			@Value("${app.auth.allowed-origin:}") String allowedWebOrigin) {
		this.authService = authService;
		this.authSessionService = authSessionService;
		this.googleAuthService = googleAuthService;
		this.allowedWebOrigin = allowedWebOrigin;
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(
			@Valid @RequestBody RegisterRequest request,
			@RequestHeader(value = "X-Auth-Client", required = false) String clientType,
			@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		validateWebOrigin(clientType, origin);
		return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL,"no-store").body(authService.register(request,userAgent));
	}
    public record VerificationRequest(@jakarta.validation.constraints.NotBlank String token){}
    public record ResendRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email){}
    @PostMapping("/verify-email")
    public java.util.Map<String,String> verify(@Valid @RequestBody VerificationRequest request){verification.verify(request.token());return java.util.Map.of("message","Email verified. You can now sign in");}
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resend(@Valid @RequestBody ResendRequest request){verification.resend(request.email());return ResponseEntity.accepted().body(java.util.Map.of("message","If this account needs verification, a new link will be sent. Please wait at least a minute between requests"));}

	@PostMapping("/login")
	public ResponseEntity<?> login(
			@Valid @RequestBody LoginRequest request,
			@RequestHeader(value = "X-Auth-Client", required = false) String clientType,
			@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		validateWebOrigin(clientType, origin);
		return authenticationResponse(authService.login(request, userAgent), HttpStatus.OK, clientType);
	}

	@PostMapping("/google")
	public ResponseEntity<?> google(
			@Valid @RequestBody GoogleLoginRequest request,
			@RequestHeader(value = "X-Auth-Client", required = false) String clientType,
			@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		validateWebOrigin(clientType, origin);
		return authenticationResponse(
				googleAuthService.login(request.idToken(), userAgent), HttpStatus.OK, clientType);
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(
			@CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
			@RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader,
			@RequestHeader(value = "X-Auth-Client", required = false) String clientType,
			@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		boolean cookieProvided = refreshCookie != null && !refreshCookie.isBlank();
		if (cookieProvided) {
			validateCookieOrigin(origin);
		}
		String refreshToken = cookieProvided ? refreshCookie : refreshHeader;
		IssuedAuthTokens tokens = authService.rotateRefreshToken(refreshToken, userAgent);
		return authenticationResponse(tokens, HttpStatus.OK, clientType);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
			@RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader,
			@RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
			Authentication authentication) {
		boolean cookieProvided = refreshCookie != null && !refreshCookie.isBlank();
		if (cookieProvided) {
			validateCookieOrigin(origin);
		}
		String sessionId = authentication == null || authentication.getDetails() == null
				? null
				: authentication.getDetails().toString();
		authService.logout(cookieProvided ? refreshCookie : refreshHeader, sessionId);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	private ResponseEntity<?> authenticationResponse(
			IssuedAuthTokens tokens, HttpStatus status, String clientType) {
		if ("mobile".equalsIgnoreCase(clientType)) {
			return ResponseEntity.status(status)
					.header(HttpHeaders.CACHE_CONTROL, "no-store")
					.header(HttpHeaders.PRAGMA, "no-cache")
					.body(new MobileAuthResponse(tokens.response(), tokens.refreshToken()));
		}
		ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, tokens.refreshToken())
				.httpOnly(true)
				.secure(authSessionService.isRefreshCookieSecure())
				.sameSite("Strict")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(Duration.ofDays(7))
				.build();
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.PRAGMA, "no-cache")
				.body(tokens.response());
	}

	private ResponseCookie clearRefreshCookie() {
		return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(authSessionService.isRefreshCookieSecure())
				.sameSite("Strict")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
	}

	private void validateCookieOrigin(String origin) {
		if (allowedWebOrigin.isBlank() || origin == null || !allowedWebOrigin.equals(origin)) {
			throw new UnauthorizedException("Refresh cookie request origin is not allowed");
		}
	}

	private void validateWebOrigin(String clientType, String origin) {
		if (!"mobile".equalsIgnoreCase(clientType)) {
			validateCookieOrigin(origin);
		}
	}
}
