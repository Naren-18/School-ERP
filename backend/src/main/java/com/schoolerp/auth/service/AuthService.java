package com.schoolerp.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.auth.config.JwtProperties;
import com.schoolerp.auth.dto.LoginRequest;
import com.schoolerp.auth.dto.LoginResponse;
import com.schoolerp.auth.dto.MeResponse;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.common.error.ApiException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.tenant.TenantContext;

@Service
public class AuthService {

	static final String BAD_CREDENTIALS = "Invalid school code, username or password";

	private final SchoolRepository schools;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final JwtProperties jwtProperties;

	private final LoginRateLimiter rateLimiter;

	private final AuditService audit;

	private final TransactionTemplate tx;

	private final Clock clock;

	/** Compared against when the user does not exist, so timing does not reveal valid usernames. */
	private final String dummyHash;

	public AuthService(SchoolRepository schools, AppUserRepository users, PasswordEncoder passwordEncoder,
			JwtService jwtService, JwtProperties jwtProperties, LoginRateLimiter rateLimiter, AuditService audit,
			TransactionTemplate tx, Clock clock) {
		this.schools = schools;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.jwtProperties = jwtProperties;
		this.rateLimiter = rateLimiter;
		this.audit = audit;
		this.tx = tx;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("timing-equaliser");
	}

	public LoginResponse login(LoginRequest request) {
		String code = request.schoolCode().trim();
		String username = request.username().trim();
		if (rateLimiter.isBlocked(code, username)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Too many failed attempts. Please wait 15 minutes and try again.");
		}

		Optional<School> school = schools.findByCodeIgnoreCase(code);
		Optional<AppUser> user = school.flatMap(s -> users.findForLogin(s.getId(), username));
		boolean passwordOk = passwordEncoder.matches(request.password(),
				user.map(AppUser::getPasswordHash).orElse(dummyHash));

		if (user.isEmpty() || !passwordOk) {
			rateLimiter.recordFailure(code, username);
			school.ifPresent(s -> TenantContext.runAs(s.getId(), () -> audit.logAs(null, AuditAction.LOGIN_FAILED,
					"app_user", user.map(AppUser::getId).orElse(null), Map.of("username", username))));
			throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
		}
		if (!school.get().isActive() || !user.get().isActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN,
					"This account is disabled. Please contact your school administrator.");
		}

		rateLimiter.reset(code, username);
		AppUser appUser = user.get();
		TenantContext.runAs(appUser.getSchoolId(), () -> tx.executeWithoutResult(status -> {
			users.updateLastLogin(appUser.getId(), appUser.getSchoolId(), OffsetDateTime.now(clock));
			audit.logAs(appUser.getId(), AuditAction.LOGIN, "app_user", appUser.getId(), null);
		}));

		Instant expiresAt = clock.instant().plus(jwtProperties.expiry());
		return new LoginResponse(jwtService.issue(appUser), expiresAt);
	}

	public MeResponse me() {
		AuthUser current = CurrentUser.get();
		Long schoolId = TenantContext.require();
		AppUser user = users.findByIdAndSchoolId(current.userId(), schoolId)
			.orElseThrow(() -> new NotFoundException("User"));
		School school = schools.findById(schoolId).orElseThrow(() -> new NotFoundException("School"));
		return MeResponse.of(user, school);
	}

}
