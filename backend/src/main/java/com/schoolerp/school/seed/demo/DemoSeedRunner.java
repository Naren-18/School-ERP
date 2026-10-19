package com.schoolerp.school.seed.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo schools and exits, only when both the {@code demo} profile is
 * active and {@code --seed-demo} is passed, so neither can trigger it alone.
 * Usable on the hosted demo server too (profiles {@code prod,demo}).
 */
@Component
@Profile("demo")
public class DemoSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

	static final String FLAG = "seed-demo";

	private final DemoDataService demoData;

	private final ApplicationContext context;

	private final String password;

	private final int studentsPerSection;

	private final int attendanceDays;

	public DemoSeedRunner(DemoDataService demoData, ApplicationContext context,
			@Value("${app.demo.password:}") String password,
			@Value("${app.demo.students-per-section:15}") int studentsPerSection,
			@Value("${app.demo.attendance-days:60}") int attendanceDays) {
		this.demoData = demoData;
		this.context = context;
		this.password = password;
		this.studentsPerSection = studentsPerSection;
		this.attendanceDays = attendanceDays;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.containsOption(FLAG)) {
			log.info("Demo profile is active; pass --{} to create the demo schools", FLAG);
			return;
		}
		int exitCode = 0;
		try {
			DemoDataService.Result result = demoData
				.seed(new DemoDataService.Options(password, studentsPerSection, attendanceDays));
			log.info("Demo seed finished. Created: {}. Already there (skipped): {}", result.created(), result.skipped());
		}
		catch (RuntimeException ex) {
			log.error("Demo seed failed", ex);
			exitCode = 1;
		}
		int code = exitCode;
		System.exit(SpringApplication.exit(context, () -> code));
	}

}
