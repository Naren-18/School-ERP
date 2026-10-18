package com.schoolerp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.schoolerp.support.TenantFixtures;

/**
 * Base for integration tests: one shared Postgres container and Spring context,
 * MockMvc, and {@link TenantFixtures} for building isolated schools.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TenantFixtures.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected TenantFixtures fixtures;

}
