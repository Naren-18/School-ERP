package com.schoolerp.common.error;

import org.springframework.http.HttpStatus;

/** 409: duplicates, records still in use, or a rule that the change would break. */
public class ConflictException extends ApiException {

	public ConflictException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

}
