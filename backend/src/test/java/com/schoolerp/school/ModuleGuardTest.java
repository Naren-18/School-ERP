package com.schoolerp.school;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.schoolerp.IntegrationTest;
import com.schoolerp.auth.Access;
import com.schoolerp.school.module.Module;
import com.schoolerp.school.module.RequiresModule;
import com.schoolerp.support.TenantFixtures.TestSchool;

@Import(ModuleGuardTest.FeeProbeController.class)
class ModuleGuardTest extends IntegrationTest {

	/** Stand-in for a real fees endpoint (the fees module arrives in phase 5). */
	@TestConfiguration
	@RestController
	@RequiresModule(Module.FEES)
	static class FeeProbeController {

		@GetMapping("/api/v1/test/fee-probe")
		@PreAuthorize(Access.ANY_STAFF)
		String probe() {
			return "ok";
		}

	}

	@Test
	void disabledModuleEndpointsAre404() throws Exception {
		TestSchool school = fixtures.school("Module School");
		getAs(school.principal(), "/api/v1/test/fee-probe").andExpect(status().isOk());

		putAs(school.principal(), "/api/v1/school/settings", """
				{"receiptPrefix":"MS","certificatePrefix":"MS","weekOffDays":["SUNDAY"],
				 "modules":{"attendance":true,"fees":false,"exams":true,"certificates":true},
				 "reportCard":{"showRank":false,"showAttendance":true}}""").andExpect(status().isOk());

		getAs(school.principal(), "/api/v1/test/fee-probe").andExpect(status().isNotFound());
		// Another school is unaffected.
		TestSchool other = fixtures.school("Other School");
		getAs(other.principal(), "/api/v1/test/fee-probe").andExpect(status().isOk());
	}

}
