package com.peergeek.community.global;

import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.global.dto.AdminCommunityBlockedUserDto;
import com.peergeek.community.global.dto.BlockCommunityUserRequest;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.global.dto.GlobalCommunityMessageDto;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalCommunityServiceTest {

    @Mock
    private GlobalCommunityMessageRepository messageRepository;
    @Mock
    private GlobalCommunityBlockRepository blockRepository;
    @Mock
    private CommunityUserProfileRepository userProfileRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GlobalCommunityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GlobalCommunityServiceImpl(
                messageRepository,
                blockRepository,
                userProfileRepository,
                outboxEventWriter,
                messagingTemplate
        );
    }

    @Test
    void testPostMessageWhenBlockedThrowsForbidden() {
        when(blockRepository.isUserBlocked(5L)).thenReturn(true);

        assertThrows(ForbiddenException.class, () ->
                service.postMessage(5L, new CreateGlobalMessageRequest("Hello all"))
        );
    }

    @Test
    void testPostMessageSuccess() {
        when(blockRepository.isUserBlocked(5L)).thenReturn(false);

        GlobalCommunityMessageEntity savedMsg = new GlobalCommunityMessageEntity(
                5L, "Test User", "testuser", null, null, null, "Hello all"
        );
        savedMsg.setId(100L);
        when(messageRepository.save(any())).thenReturn(savedMsg);

        GlobalCommunityMessageDto dto = service.postMessage(5L, new CreateGlobalMessageRequest("Hello all"));

        assertNotNull(dto);
        assertEquals("Hello all", dto.content());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.message-created.v1"), anyString(), any());
    }

    @Test
    void testBlockUser() {
        when(blockRepository.isUserBlocked(20L)).thenReturn(false);

        GlobalCommunityBlockEntity block = new GlobalCommunityBlockEntity(
                20L, "Bad Actor", "bad@test.com", "badactor", null, 1L, "Admin User", "Spamming"
        );
        when(blockRepository.save(any())).thenReturn(block);

        AdminCommunityBlockedUserDto dto = service.blockUser(20L, 1L, new BlockCommunityUserRequest("Spamming"));

        assertNotNull(dto);
        assertEquals(20L, dto.userId());
        assertEquals("Spamming", dto.reason());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.user-blocked.v1"), anyString(), any());
    }

    @Test
    void testUnblockUser() {
        GlobalCommunityBlockEntity activeBlock = new GlobalCommunityBlockEntity(
                20L, "Bad Actor", "bad@test.com", "badactor", null, 1L, "Admin User", "Spamming"
        );
        when(blockRepository.findActiveBlockByUserId(20L)).thenReturn(Optional.of(activeBlock));

        service.unblockUser(20L, 1L, new BlockCommunityUserRequest("Appeal granted"));

        assertNotNull(activeBlock.getUnblockedAt());
        assertEquals(1L, activeBlock.getUnblockedBy());
        assertEquals("Appeal granted", activeBlock.getUnblockReason());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.user-unblocked.v1"), anyString(), any());
    }
}

