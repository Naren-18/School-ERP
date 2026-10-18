package com.schoolerp.auth.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.schoolerp.auth.service.AuthUser;
import com.schoolerp.auth.service.JwtService;
import com.schoolerp.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the bearer token and sets the security context and {@link TenantContext}.
 * This is the only place a request's tenant is set. Both are cleared when the request ends.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try {
			String header = request.getHeader(HttpHeaders.AUTHORIZATION);
			if (header != null && header.startsWith(BEARER)) {
				jwtService.verify(header.substring(BEARER.length())).ifPresent(this::authenticate);
			}
			chain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}

	private void authenticate(AuthUser user) {
		List<SimpleGrantedAuthority> authorities = user.roles()
			.stream()
			.map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
			.toList();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
		SecurityContextHolder.setContext(context);
		TenantContext.set(user.schoolId());
	}

}
