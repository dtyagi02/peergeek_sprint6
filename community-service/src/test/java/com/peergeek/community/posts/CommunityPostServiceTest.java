package com.peergeek.community.posts;

import com.peergeek.community.exception.BadRequestException;
import com.peergeek.community.exception.NotFoundException;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.posts.dto.CommunityCommentDto;
import com.peergeek.community.posts.dto.CommunityPostDto;
import com.peergeek.community.posts.dto.CreateCommentRequest;
import com.peergeek.community.posts.dto.CreatePostRequest;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository postRepository;
    @Mock
    private CommunityCommentRepository commentRepository;
    @Mock
    private CommunityMentionRepository mentionRepository;
    @Mock
    private CommunityUserProfileRepository userProfileRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;

    private CommunityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunityServiceImpl(
                postRepository,
                commentRepository,
                mentionRepository,
                userProfileRepository,
                outboxEventWriter
        );
    }

    @Test
    void testCreatePostBlankThrowsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.createPost(1L, new CreatePostRequest("   "))
        );
    }

    @Test
    void testCreatePostSuccess() {
        CommunityPostEntity post = new CommunityPostEntity(1L, "User 1", "user_1", null, "Hello community post");
        post.setId(10L);
        when(postRepository.save(any())).thenReturn(post);

        CommunityPostDto dto = service.createPost(1L, new CreatePostRequest("Hello community post"));

        assertNotNull(dto);
        assertEquals("Hello community post", dto.content());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.message-created.v1"), anyString(), any());
    }

    @Test
    void testCreateCommentWhenPostNotFoundThrowsNotFound() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                service.createComment(999L, 1L, new CreateCommentRequest("Nice post", null))
        );
    }

    @Test
    void testCreateCommentSuccess() {
        CommunityPostEntity post = new CommunityPostEntity(1L, "User 1", "user_1", null, "Hello community post");
        post.setId(10L);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        CommunityCommentEntity comment = new CommunityCommentEntity(10L, 2L, "User 2", "user_2", null, null, "Great!");
        comment.setId(20L);
        when(commentRepository.save(any())).thenReturn(comment);

        CommunityCommentDto dto = service.createComment(10L, 2L, new CreateCommentRequest("Great!", null));

        assertNotNull(dto);
        assertEquals("Great!", dto.content());
        verify(outboxEventWriter).writeEvent(eq("peergeek.community.message-created.v1"), anyString(), any());
    }
}

