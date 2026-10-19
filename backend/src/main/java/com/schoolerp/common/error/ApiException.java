package com.schoolerp.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * An error with a status and a message that is safe to show to the user.
 * Optional properties are added to the ProblemDetail so the client can react
 * (e.g. offer a choice) without parsing the message.
 */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final Map<String, Object> properties;

	public ApiException(HttpStatus status, String message) {
		this(status, message, Map.of());
	}

	public ApiException(HttpStatus status, String message, Map<String, Object> properties) {
		super(message);
		this.status = status;
		this.properties = Map.copyOf(properties);
	}

}
