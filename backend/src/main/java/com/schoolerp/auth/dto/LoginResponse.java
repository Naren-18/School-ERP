package com.schoolerp.auth.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt) {
}
