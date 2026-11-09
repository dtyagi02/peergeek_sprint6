package com.peergeek.community;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.common.UserMode;
import com.peergeek.community.common.UserRole;
import com.peergeek.community.domain.DomainCommunityService;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.global.GlobalCommunityService;
import com.peergeek.community.global.dto.CommunityUserBlockStatusDto;
import com.peergeek.community.global.dto.GlobalCommunityMessageDto;
import com.peergeek.community.posts.CommunityService;
import com.peergeek.community.posts.dto.CommunityAuthorDto;
import com.peergeek.community.posts.dto.CommunityPostDto;
import com.peergeek.community.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DomainCommunityService domainCommunityService;

    @MockitoBean
    private GlobalCommunityService globalCommunityService;

    @MockitoBean
    private CommunityService communityService;

    @Test
    void testGetDomainCommunitiesPublic() throws Exception {
        DomainCommunityDto dto = new DomainCommunityDto(1L, 1L, "Web Development", "web-development", "Desc", 10L, false);
        when(domainCommunityService.listDomainCommunities(isNull())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/domains/communities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("web-development"))
                .andExpect(jsonPath("$[0].memberCount").value(10));
    }

    @Test
    void testGetGlobalCommunityStatusWithAuth() throws Exception {
        String token = jwtService.generateToken(1L, UserRole.LEARNER);
        when(globalCommunityService.getUserBlockStatus(1L))
                .thenReturn(new CommunityUserBlockStatusDto(false, null, null));

        mockMvc.perform(get("/api/v1/community/global/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void testGetGlobalCommunityMessagesWithAuth() throws Exception {
        String token = jwtService.generateToken(1L, UserRole.LEARNER);
        GlobalCommunityMessageDto msg = new GlobalCommunityMessageDto(
                100L, 1L, "Dev User", "devuser", UserRole.LEARNER, UserMode.LEARNER, "Hello world", Instant.now(), true, true
        );
        when(globalCommunityService.getMessages(any(), any(), anyInt(), eq(1L), eq(false)))
                .thenReturn(List.of(msg));

        mockMvc.perform(get("/api/v1/community/global/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello world"));
    }

    @Test
    void testGetPostsPublic() throws Exception {
        CommunityAuthorDto author = new CommunityAuthorDto(1L, "Dev User", "devuser", null);
        CommunityPostDto post = new CommunityPostDto(1L, author, "Welcome", Instant.now(), Instant.now(), 0L, false, false);
        when(communityService.getPosts(any(), isNull(), eq(false)))
                .thenReturn(new PageResponse<>(List.of(post), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Welcome"));
    }
}

