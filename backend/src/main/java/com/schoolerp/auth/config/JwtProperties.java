package com.schoolerp.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiry) {

	public JwtProperties {
		if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes");
		}
		if (expiry == null) {
			expiry = Duration.ofHours(12);
		}
	}

}
