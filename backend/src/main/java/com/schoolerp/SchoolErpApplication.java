package com.schoolerp;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchoolErpApplication {

	public static void main(String[] args) {
		// Windows reports the legacy "Asia/Calcutta" id, which newer Postgres rejects.
		// Tests get the same via surefire's argLine in pom.xml.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(SchoolErpApplication.class, args);
	}

}
