package com.peergeek.community.posts;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.posts.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommunityService {

	PageResponse<CommunityPostDto> getPosts(Pageable pageable, Long currentUserId, boolean isAdmin);

	CommunityPostDto getPost(Long postId, Long currentUserId, boolean isAdmin);

	CommunityPostDto createPost(Long authorId, CreatePostRequest request);

	void deletePost(Long postId, Long currentUserId, boolean isAdmin);

	List<CommunityCommentDto> getComments(Long postId, Long currentUserId, boolean isAdmin);

	CommunityCommentDto createComment(Long postId, Long authorId, CreateCommentRequest request);

	void deleteComment(Long commentId, Long currentUserId, boolean isAdmin);

	List<MentionSuggestionDto> getMentionSuggestions(String query);
}

