package com.schoolerp.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** An error with a status and a message that is safe to show to the user. */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

}
