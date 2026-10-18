package com.schoolerp.common.error;

import org.springframework.http.HttpStatus;

/** 404. Also used when a record belongs to another school, so existence never leaks. */
public class NotFoundException extends ApiException {

	public NotFoundException(String what) {
		super(HttpStatus.NOT_FOUND, what + " not found");
	}

}
