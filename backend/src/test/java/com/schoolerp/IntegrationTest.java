package com.schoolerp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.schoolerp.support.AcademicFixtures;
import com.schoolerp.support.TenantFixtures;
import com.schoolerp.support.TenantFixtures.TestUser;

/**
 * Base for integration tests: one shared Postgres container and Spring context,
 * MockMvc, {@link TenantFixtures} for building isolated schools, and request helpers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TenantFixtures.class, AcademicFixtures.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected TenantFixtures fixtures;

	@Autowired
	protected AcademicFixtures academic;

	protected ResultActions getAs(TestUser user, String url, Object... vars) throws Exception {
		return mvc.perform(get(url, vars).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	protected ResultActions postAs(TestUser user, String url, String json, Object... vars) throws Exception {
		return mvc.perform(post(url, vars).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json == null ? "" : json));
	}

	protected ResultActions putAs(TestUser user, String url, String json, Object... vars) throws Exception {
		return mvc.perform(put(url, vars).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	protected ResultActions deleteAs(TestUser user, String url, Object... vars) throws Exception {
		return mvc.perform(delete(url, vars).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	/** Reads a numeric id (or any long) from a JSON response. */
	protected static long idOf(ResultActions result, String path) throws Exception {
		Number value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
		return value.longValue();
	}

	protected static long idOf(ResultActions result) throws Exception {
		return idOf(result, "$.id");
	}

}
