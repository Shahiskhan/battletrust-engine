package com.bte.security;

import com.bte.enums.Role;
import com.bte.entity.AuthSession;
import com.bte.repository.AuthSessionRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider tokenProvider;
	private final AuthSessionRepository authSessionRepository;

	public JwtAuthenticationFilter(
			JwtTokenProvider tokenProvider, AuthSessionRepository authSessionRepository) {
		this.tokenProvider = tokenProvider;
		this.authSessionRepository = authSessionRepository;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		return path.startsWith("/swagger-ui")
				|| path.startsWith("/v3/api-docs")
				|| path.startsWith("/swagger-resources")
				|| path.startsWith("/webjars/")
				|| path.startsWith("/openapi");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.startsWith("Bearer ")) {
			String token = authorization.substring(7).trim();
			if (!token.isEmpty() && tokenProvider.validateToken(token)) {
				try {
					String sessionId = tokenProvider.extractSessionId(token);
					AuthSession session = authSessionRepository.findActiveWithUser(
							sessionId, java.time.Instant.now())
							.orElse(null);
					if (session == null || session.getUser().isBlocked() || !session.getUser().isEmailVerified() || session.getUser().getRole()==Role.ROLE_GUEST) {
						SecurityContextHolder.clearContext();
						filterChain.doFilter(request, response);
						return;
					}
					String email = tokenProvider.extractEmail(token);
					Role role = tokenProvider.extractRole(token);
					if (!session.getUser().getEmail().equals(email) || session.getUser().getRole() != role) {
						SecurityContextHolder.clearContext();
						filterChain.doFilter(request, response);
						return;
					}
					var authentication = new UsernamePasswordAuthenticationToken(
							email, null, List.of(new SimpleGrantedAuthority(role.name())));
					authentication.setDetails(sessionId);
					SecurityContextHolder.getContext().setAuthentication(authentication);
				} catch (JwtException | IllegalArgumentException exception) {
					SecurityContextHolder.clearContext();
				}
			}
		}
		filterChain.doFilter(request, response);
	}
}
