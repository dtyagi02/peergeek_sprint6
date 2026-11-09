package com.peergeek.referral;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.common.PageResponse;
import com.peergeek.referral.dto.ReferralConfigDto;
import com.peergeek.referral.dto.ReferralHistoryItemDto;
import com.peergeek.referral.dto.ReferralInviteRequest;
import com.peergeek.referral.dto.ReferralInviteResponse;
import com.peergeek.referral.dto.ReferralSummaryDto;
import com.peergeek.referral.dto.ReferralValidateResponse;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReferralControllerTest {

	@Mock
	private ReferralService referralService;

	@Mock
	private CurrentUserProvider currentUserProvider;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		ReferralController controller = new ReferralController(referralService, currentUserProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@DisplayName("POST /api/v1/referrals/invite: sends referral invitation")
	void sendInvitation_Endpoint() throws Exception {
		when(currentUserProvider.requireCurrentUser())
				.thenReturn(new CurrentUser(1L, UserRole.USER));
		when(referralService.sendInvitation(eq(1L), any(ReferralInviteRequest.class)))
				.thenReturn(new ReferralInviteResponse("Invitation sent successfully", "PG-12345678", "friend@example.com", Instant.now()));

		mockMvc.perform(post("/api/v1/referrals/invite")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ReferralInviteRequest("friend@example.com"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value("Invitation sent successfully"))
				.andExpect(jsonPath("$.referralCode").value("PG-12345678"))
				.andExpect(jsonPath("$.invitedEmail").value("friend@example.com"));
	}

	@Test
	@DisplayName("GET /api/v1/referrals/summary: returns referral summary for current user")
	void getSummary_Endpoint() throws Exception {
		when(currentUserProvider.requireCurrentUser())
				.thenReturn(new CurrentUser(1L, UserRole.USER));
		when(referralService.getSummary(1L))
				.thenReturn(new ReferralSummaryDto(5, 2, 3, new BigDecimal("300.00"), new BigDecimal("100.00"), new BigDecimal("50.00")));

		mockMvc.perform(get("/api/v1/referrals/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalInvitations").value(5))
				.andExpect(jsonPath("$.pendingReferrals").value(2))
				.andExpect(jsonPath("$.successfulReferrals").value(3))
				.andExpect(jsonPath("$.totalCoinsEarned").value(300.00))
				.andExpect(jsonPath("$.inviterRewardAmount").value(100.00))
				.andExpect(jsonPath("$.newUserRewardAmount").value(50.00));
	}

	@Test
	@DisplayName("GET /api/v1/referrals/history: returns paged history for current user")
	void getHistory_Endpoint() throws Exception {
		when(currentUserProvider.requireCurrentUser())
				.thenReturn(new CurrentUser(1L, UserRole.USER));
		ReferralHistoryItemDto item = new ReferralHistoryItemDto(
				10L, "invited@example.com", "PG-CODE1", ReferralStatus.PENDING,
				new BigDecimal("100.00"), new BigDecimal("50.00"),
				Instant.now(), null, null, Instant.now());
		when(referralService.getHistory(eq(1L), any()))
				.thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

		mockMvc.perform(get("/api/v1/referrals/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].invitedEmail").value("invited@example.com"))
				.andExpect(jsonPath("$.content[0].referralCode").value("PG-CODE1"))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"));
	}

	@Test
	@DisplayName("GET /api/v1/referrals/validate: validates referral code")
	void validateCode_Endpoint() throws Exception {
		when(referralService.validateReferralCode("PG-CODE1", "test@example.com"))
				.thenReturn(new ReferralValidateResponse(true, "Alice", new BigDecimal("50.00"), new BigDecimal("100.00"), "Valid"));

		mockMvc.perform(get("/api/v1/referrals/validate")
						.param("code", "PG-CODE1")
						.param("email", "test@example.com"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.inviterDisplayName").value("Alice"))
				.andExpect(jsonPath("$.newUserRewardAmount").value(50.00));
	}

	@Test
	@DisplayName("GET /api/v1/referrals/config: returns reward configuration")
	void getConfig_Endpoint() throws Exception {
		when(referralService.getConfig())
				.thenReturn(new ReferralConfigDto(new BigDecimal("100.00"), new BigDecimal("50.00"), 7));

		mockMvc.perform(get("/api/v1/referrals/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inviterReward").value(100.00))
				.andExpect(jsonPath("$.newUserReward").value(50.00))
				.andExpect(jsonPath("$.expiryDays").value(7));
	}
}
