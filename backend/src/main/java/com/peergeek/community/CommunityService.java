package com.peergeek.community;

import com.peergeek.common.PageResponse;
import com.peergeek.community.dto.CommunityCommentDto;
import com.peergeek.community.dto.CommunityPostDto;
import com.peergeek.community.dto.CreateCommentRequest;
import com.peergeek.community.dto.CreatePostRequest;
import com.peergeek.community.dto.MentionSuggestionDto;
import java.util.List;
import org.springframework.data.domain.Pageable;

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
