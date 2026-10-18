package com.schoolerp.auth.service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.schoolerp.auth.entity.Role;

import lombok.RequiredArgsConstructor;

/**
 * Re-checks a token's user on every request, so deactivating a user or school,
 * or changing roles, takes effect immediately instead of when the token expires.
 * One indexed query; runs before any tenant is in context, hence plain SQL scoped
 * explicitly by school.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

	private static final String SQL = """
			SELECT u.full_name, (u.active AND s.active) AS ok,
			       ARRAY(SELECT r.role FROM user_role r WHERE r.user_id = u.id) AS roles
			FROM app_user u JOIN school s ON s.id = u.school_id
			WHERE u.id = ? AND u.school_id = ?
			""";

	private final JdbcTemplate jdbc;

	/** The user with current name and roles, or empty if they or their school are gone or inactive. */
	public Optional<AuthUser> refresh(AuthUser fromToken) {
		return jdbc.query(SQL, rs -> {
			if (!rs.next() || !rs.getBoolean("ok")) {
				return Optional.<AuthUser>empty();
			}
			Set<Role> roles = roles(rs.getArray("roles"));
			if (roles.isEmpty()) {
				return Optional.<AuthUser>empty();
			}
			return Optional.of(new AuthUser(fromToken.userId(), fromToken.schoolId(), rs.getString("full_name"), roles));
		}, fromToken.userId(), fromToken.schoolId());
	}

	private static Set<Role> roles(Array array) throws SQLException {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		for (Object name : (Object[]) array.getArray()) {
			roles.add(Role.valueOf(name.toString()));
		}
		return roles;
	}

}
