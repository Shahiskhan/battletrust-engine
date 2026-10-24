package com.bte.config;

import com.bte.exception.ErrorResponse;
import com.bte.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Bean
	public org.springframework.security.core.userdetails.UserDetailsService userDetailsService(com.bte.repository.UserRepository users) {
		return email -> users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
				.map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
						.password(user.getPassword()).authorities(user.getRole().name()).build())
				.orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Account not found"));
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper)
			throws Exception {
		return http
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.frameOptions(frame -> frame.deny())
						.contentTypeOptions(Customizer.withDefaults())
						.xssProtection(xss -> xss.headerValue(
								XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31_536_000)))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST,"/api/v1/public/registrations").access(new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager("!hasAuthority('ROLE_ADMIN')"))
.requestMatchers("/api/v1/payments/simulate-pay/**").access(new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager("!hasAuthority('ROLE_ADMIN')"))
						.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,jakarta.servlet.DispatcherType.ERROR).permitAll()
						// Swagger UI, OpenAPI JSON/YAML, and their static assets are public.
						.requestMatchers(
						"/swagger-ui",
						"/swagger-ui/**",
						"/swagger-ui.html",
						"/v3/api-docs",
						"/v3/api-docs/**",
						"/swagger-resources",
						"/swagger-resources/**",
						"/webjars/**",
						"/openapi",
						"/openapi/**",
						"/openapi/v3/api-docs",
						"/openapi/v3/api-docs/**")
						.permitAll()

						// 2. Aapke baaki public endpoints
						.requestMatchers("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/chat/**",
								"/media/**", "/images/**")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/users/leaderboard").permitAll()

						// 3. Role-based Protected endpoints
						.requestMatchers("/api/v1/admin/**", "/actuator/**").hasAuthority("ROLE_ADMIN")
						.requestMatchers(HttpMethod.GET,"/api/v1/me").authenticated()
.requestMatchers("/api/v1/me/**","/api/v1/wallet/**","/api/v1/squads/**","/api/v1/challenges/**","/api/v1/disputes/file-claim").hasAnyAuthority("ROLE_PLAYER","ROLE_ORGANIZER","ROLE_LEADER")
.requestMatchers(HttpMethod.POST,"/api/v1/tournaments/*/join").hasAnyAuthority("ROLE_PLAYER","ROLE_ORGANIZER","ROLE_LEADER")
.requestMatchers(HttpMethod.POST,"/api/v1/tournaments","/api/v1/organizer/tournaments","/api/v1/leader/tournaments").hasAuthority("ROLE_ORGANIZER")
						.requestMatchers("/api/v1/tournaments/**", "/api/v1/room-credentials/**").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
.requestMatchers("/api/v1/organizer/**").hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
						.requestMatchers("/api/v1/leader/**")
						.hasAnyAuthority("ROLE_ORGANIZER", "ROLE_ADMIN")
						.requestMatchers("/api/v1/registrations/**")
						.hasAnyAuthority("ROLE_PLAYER", "ROLE_LEADER", "ROLE_ADMIN")

						// 4. Baaki sabhi requests ke liye authentication zaroori hai
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper,
								HttpStatus.UNAUTHORIZED,
								"Authentication is required", request.getRequestURI()))
						.accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper,
								HttpStatus.FORBIDDEN,
								"Access is denied", request.getRequestURI())))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${app.auth.allowed-origin}") String origin) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(origin));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Registration-Ticket", "X-Auth-Client", "X-Room-Invite"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	private void writeError(
			HttpServletResponse response,
			ObjectMapper objectMapper,
			HttpStatus status,
			String message,
			String path) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
				Instant.now(), status.value(), status.getReasonPhrase(), message, path));
	}
}
