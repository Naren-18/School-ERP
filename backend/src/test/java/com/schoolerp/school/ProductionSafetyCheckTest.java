package com.schoolerp.school;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

import com.schoolerp.school.seed.DevSeeder;
import com.schoolerp.school.seed.ProductionSafetyCheck;

/** Starts a minimal context with only the safety check and the seeder's profile rule. */
class ProductionSafetyCheckTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(ProductionSafetyCheck.class);

	@Test
	void prodStartsWhenClean() {
		runner.withPropertyValues("spring.profiles.active=prod").run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@Test
	void prodRefusesADevSeedPassword() {
		runner.withPropertyValues("spring.profiles.active=prod", "DEV_SEED_PASSWORD=Demo@12345")
			.run(ctx -> assertThat(ctx).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("DEV_SEED_PASSWORD is set"));
	}

	@Test
	void prodRefusesTheDevProfile() {
		runner.withPropertyValues("spring.profiles.active=prod,dev")
			.run(ctx -> assertThat(ctx).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("must not be active together"));
	}

	@Test
	void seederIsNeverActiveUnderProd() {
		Profile profile = DevSeeder.class.getAnnotation(Profile.class);
		assertThat(profile.value()).containsExactly("dev & !prod");
	}

}
