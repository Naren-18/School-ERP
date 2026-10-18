package com.schoolerp;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Logins are handled by AuthService + JWT; no default in-memory user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SchoolErpApplication {

	public static void main(String[] args) {
		// Windows reports the legacy "Asia/Calcutta" id, which newer Postgres rejects.
		// Tests get the same via surefire's argLine in pom.xml.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(SchoolErpApplication.class, args);
	}

}
