package com.schoolerp;

import org.springframework.boot.SpringApplication;

public class TestSchoolErpApplication {

	public static void main(String[] args) {
		SpringApplication.from(SchoolErpApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
