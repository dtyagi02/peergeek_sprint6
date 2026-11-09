package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;

class AdminDiscoveryReindexIntegrationTest extends DiscoveryTestSupport {

	@Test
	void adminCanTriggerReindexAndActionIsAudited() throws Exception {
		UserEntity admin = createAdmin("admin-reindex@example.test", "Discovery Admin");
		String token = tokenFor(admin);

		mockMvc.perform(post("/api/v1/admin/discovery/reindex")
						.header("Authorization", bearer(token)))
				.andExpect(status().isAccepted());

		Integer auditCount = jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'DISCOVERY_REINDEX_REQUESTED' and actor_user_id = ?",
				Integer.class, admin.getId());
		assertThat(auditCount).isPositive();
	}

	@Test
	void regularUserCannotTriggerReindex() throws Exception {
		UserEntity user = createInstructor("regular-user@example.test", "Regular User");
		String token = tokenFor(user);

		mockMvc.perform(post("/api/v1/admin/discovery/reindex")
						.header("Authorization", bearer(token)))
				.andExpect(status().isForbidden());
	}

	@Test
	void anonymousUserCannotTriggerReindex() throws Exception {
		mockMvc.perform(post("/api/v1/admin/discovery/reindex"))
				.andExpect(status().isUnauthorized());
	}
}
