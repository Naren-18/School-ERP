package com.schoolerp.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.schoolerp.auth.config.JwtProperties;
import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;

/** Issues and validates HS256 access tokens. Claims: sub, schoolId, roles, name. */
@Service
public class JwtService {

	private static final String ISSUER = "school-erp";

	private final JwtEncoder encoder;

	private final JwtDecoder decoder;

	private final JwtProperties properties;

	private final Clock clock;

	public JwtService(JwtProperties properties, Clock clock) {
		SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		this.properties = properties;
		this.clock = clock;
	}

	public String issue(AppUser user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.expiry()))
			.claim("schoolId", user.getSchoolId())
			.claim("roles", user.getRoles().stream().map(Role::name).sorted().toList())
			.claim("name", user.getFullName())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** Returns the principal for a valid token, or empty if it is invalid or expired. */
	public Optional<AuthUser> verify(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			if (!ISSUER.equals(jwt.getClaimAsString("iss"))) {
				return Optional.empty();
			}
			Long userId = Long.valueOf(jwt.getSubject());
			Number schoolId = jwt.getClaim("schoolId");
			List<String> roleNames = jwt.getClaimAsStringList("roles");
			if (schoolId == null || roleNames == null) {
				return Optional.empty();
			}
			Set<Role> roles = EnumSet.noneOf(Role.class);
			roleNames.forEach(name -> roles.add(Role.valueOf(name)));
			return Optional.of(new AuthUser(userId, schoolId.longValue(), jwt.getClaimAsString("name"), roles));
		}
		catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
