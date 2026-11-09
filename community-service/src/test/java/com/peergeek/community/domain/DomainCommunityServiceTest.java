package com.peergeek.community.domain;

import com.peergeek.community.domain.dto.DomainChatMessageDto;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainCommunityServiceTest {

    @Mock
    private DomainCommunityRepository domainCommunityRepository;
    @Mock
    private DomainCommunityMembershipRepository membershipRepository;
    @Mock
    private DomainChatMessageRepository messageRepository;
    @Mock
    private CommunityUserProfileRepository userProfileRepository;
    @Mock
    private GlobalCommunityBlockRepository blockRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private DomainCommunityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainCommunityServiceImpl(
                domainCommunityRepository,
                membershipRepository,
                messageRepository,
                userProfileRepository,
                blockRepository,
                outboxEventWriter,
                messagingTemplate
        );
    }

    @Test
    void testListDomainCommunities() {
        DomainCommunityEntity domain = new DomainCommunityEntity(1L, "Web Development", "web-development", "Desc");
        when(domainCommunityRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(domain));
        when(membershipRepository.countByDomainId(any())).thenReturn(5L);
        when(membershipRepository.existsByDomainIdAndUserId(any(), eq(10L))).thenReturn(true);

        List<DomainCommunityDto> list = service.listDomainCommunities(10L);

        assertEquals(1, list.size());
        assertEquals("web-development", list.get(0).slug());
        assertEquals(5L, list.get(0).memberCount());
        assertTrue(list.get(0).isMember());
    }

    @Test
    void testJoinCommunity() {
        DomainCommunityEntity domain = new DomainCommunityEntity(1L, "Web Development", "web-development", "Desc");
        domain.setId(1L);
        when(domainCommunityRepository.findBySlug("web-development")).thenReturn(Optional.of(domain));
        when(membershipRepository.existsByDomainIdAndUserId(any(), eq(10L))).thenReturn(false);
        when(membershipRepository.countByDomainId(any())).thenReturn(1L);

        DomainCommunityDto result = service.joinCommunity("web-development", 10L);

        assertTrue(result.isMember());
        verify(membershipRepository).save(any());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.member-joined.v1"), anyString(), any());
    }

    @Test
    void testSendMessageWhenNotMemberThrowsForbidden() {
        DomainCommunityEntity domain = new DomainCommunityEntity(1L, "Web Development", "web-development", "Desc");
        domain.setId(1L);
        when(domainCommunityRepository.findBySlug("web-development")).thenReturn(Optional.of(domain));
        when(blockRepository.isUserBlocked(10L)).thenReturn(false);
        when(membershipRepository.existsByDomainIdAndUserId(any(), eq(10L))).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                service.sendMessage("web-development", 10L, new SendDomainMessageRequest("Hello"))
        );
    }

    @Test
    void testSendMessageSuccess() {
        DomainCommunityEntity domain = new DomainCommunityEntity(1L, "Web Development", "web-development", "Desc");
        domain.setId(1L);
        when(domainCommunityRepository.findBySlug("web-development")).thenReturn(Optional.of(domain));
        when(blockRepository.isUserBlocked(10L)).thenReturn(false);
        when(membershipRepository.existsByDomainIdAndUserId(any(), eq(10L))).thenReturn(true);

        DomainChatMessageEntity savedMsg = new DomainChatMessageEntity(
                1L, 10L, "Dev User", "devuser", null, MessageType.CHAT, "Hello domain"
        );
        savedMsg.setId(100L);
        when(messageRepository.save(any())).thenReturn(savedMsg);

        DomainChatMessageDto dto = service.sendMessage("web-development", 10L, new SendDomainMessageRequest("Hello domain"));

        assertNotNull(dto);
        assertEquals("Hello domain", dto.content());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.message-created.v1"), anyString(), any());
    }
}

