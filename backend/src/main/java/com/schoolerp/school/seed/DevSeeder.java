package com.schoolerp.school.seed;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;
import com.schoolerp.auth.repository.AppUserRepository;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.tenant.TenantContext;

/**
 * Dev-only seed: two fictional schools with a {@code principal} login each.
 * Idempotent: a school whose code already exists is skipped.
 */
@Component
// Never under prod, even if someone activates dev alongside it (ProductionSafetyCheck also refuses that).
@Profile("dev & !prod")
public class DevSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

	private final SchoolRepository schools;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate tx;

	private final String password;

	public DevSeeder(SchoolRepository schools, AppUserRepository users, PasswordEncoder passwordEncoder,
			TransactionTemplate tx, @Value("${app.dev-seed.password}") String password) {
		this.schools = schools;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tx = tx;
		this.password = password;
	}

	@Override
	public void run(String... args) {
		seed("ssv", "Sri Saraswathi Vidyalayam", School.Board.STATE, "TELUGU", "#b45309", "SS",
				"K. Lakshmi Prasanna");
		seed("gps", "Greenfield Public School", School.Board.CBSE, "ENGLISH", "#15803d", "GP", "R. Srinivas Rao");
	}

	private void seed(String code, String name, School.Board board, String medium, String color, String initials,
			String principalName) {
		if (schools.findByCodeIgnoreCase(code).isPresent()) {
			return;
		}
		tx.executeWithoutResult(status -> {
			School school = new School();
			school.setCode(code);
			school.setName(name);
			school.setBoard(board);
			school.setMedium(medium);
			school.setPrimaryColor(color);
			school.setLogoUrl(logo(initials, color));
			schools.save(school);

			TenantContext.runAs(school.getId(), () -> {
				AppUser principal = new AppUser();
				principal.setUsername("principal");
				principal.setFullName(principalName);
				principal.setDesignation("Principal");
				principal.setPasswordHash(passwordEncoder.encode(password));
				principal.setRoles(EnumSet.of(Role.PRINCIPAL));
				users.save(principal);
			});
		});
		log.info("Dev seed: created school '{}' (code {}) with user 'principal'", name, code);
	}

	/** Placeholder logo until logo upload exists (phase 2): initials on the school colour. */
	private static String logo(String initials, String color) {
		String svg = """
				<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96">\
				<circle cx="48" cy="48" r="46" fill="%s"/>\
				<text x="48" y="60" font-family="Arial,sans-serif" font-size="36" font-weight="700" \
				fill="#fff" text-anchor="middle">%s</text></svg>""".formatted(color, initials);
		return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
	}

}
