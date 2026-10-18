package com.schoolerp.school.seed;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start production with development settings: the dev profile
 * active alongside prod, or a dev seed password present (a sign that a dev
 * .env leaked into the server environment, and that seeding could run).
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck implements InitializingBean {

	static final String DEV_SEED_PASSWORD = "DEV_SEED_PASSWORD";

	private final Environment environment;

	public ProductionSafetyCheck(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		if (environment.acceptsProfiles(Profiles.of("dev"))) {
			throw new IllegalStateException("The dev and prod profiles must not be active together");
		}
		if (environment.containsProperty(DEV_SEED_PASSWORD) || environment.containsProperty("app.dev-seed.password")) {
			throw new IllegalStateException(DEV_SEED_PASSWORD
					+ " is set, but the prod profile is active. Remove it from the production environment.");
		}
	}

}
