package com.peergeek.community.posts;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.exception.BadRequestException;
import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.exception.NotFoundException;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.posts.dto.*;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class CommunityServiceImpl implements CommunityService {

	private static final Logger log = LoggerFactory.getLogger(CommunityServiceImpl.class);
	private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_]{3,30})\\b");

	private final CommunityPostRepository postRepository;
	private final CommunityCommentRepository commentRepository;
	private final CommunityMentionRepository mentionRepository;
	private final CommunityUserProfileRepository userProfileRepository;
	private final OutboxEventWriter outboxEventWriter;

	public CommunityServiceImpl(
			CommunityPostRepository postRepository,
			CommunityCommentRepository commentRepository,
			CommunityMentionRepository mentionRepository,
			CommunityUserProfileRepository userProfileRepository,
			OutboxEventWriter outboxEventWriter) {
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.mentionRepository = mentionRepository;
		this.userProfileRepository = userProfileRepository;
		this.outboxEventWriter = outboxEventWriter;
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<CommunityPostDto> getPosts(Pageable pageable, Long currentUserId, boolean isAdmin) {
		Page<CommunityPostEntity> page = postRepository.findAllByOrderByCreatedAtDesc(pageable);

		List<CommunityPostDto> items = page.getContent().stream().map(post -> {
			CommunityAuthorDto author = toAuthorDto(post);
			long commentCount = commentRepository.countByPostId(post.getId());
			boolean isAuthor = currentUserId != null && currentUserId.equals(post.getAuthorId());
			boolean canDelete = isAuthor || isAdmin;
			return new CommunityPostDto(
					post.getId(),
					author,
					post.getContent(),
					post.getCreatedAt(),
					post.getUpdatedAt(),
					commentCount,
					isAuthor,
					canDelete
			);
		}).toList();

		return new PageResponse<>(
				items,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityPostDto getPost(Long postId, Long currentUserId, boolean isAdmin) {
		CommunityPostEntity post = postRepository.findById(postId)
				.orElseThrow(() -> new NotFoundException("Post not found"));
		CommunityAuthorDto author = toAuthorDto(post);
		long commentCount = commentRepository.countByPostId(post.getId());
		boolean isAuthor = currentUserId != null && currentUserId.equals(post.getAuthorId());
		boolean canDelete = isAuthor || isAdmin;

		return new CommunityPostDto(
				post.getId(),
				author,
				post.getContent(),
				post.getCreatedAt(),
				post.getUpdatedAt(),
				commentCount,
				isAuthor,
				canDelete
		);
	}

	@Override
	public CommunityPostDto createPost(Long authorId, CreatePostRequest request) {
		String content = request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Post content cannot be empty");
		}

		CommunityUserProfileEntity profile = userProfileRepository.findById(authorId).orElse(null);
		String authorName = profile != null ? profile.getDisplayName() : "User " + authorId;
		String authorUsername = profile != null ? profile.getUsername() : "user_" + authorId;
		String authorAvatar = profile != null ? profile.getAvatarUrl() : null;

		CommunityPostEntity post = new CommunityPostEntity(
				authorId,
				authorName,
				authorUsername,
				authorAvatar,
				content
		);
		post = postRepository.save(post);

		// Resolve and save mentions
		processMentions(post.getId(), null, authorId, authorName, authorUsername, content);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-created.v1",
				"CommunityPostCreated",
				Map.of(
						"channel", "post",
						"postId", post.getId(),
						"authorId", authorId,
						"authorName", authorName,
						"content", content,
						"createdAt", post.getCreatedAt().toString()
				)
		);

		CommunityAuthorDto author = new CommunityAuthorDto(authorId, authorName, authorUsername, authorAvatar);
		return new CommunityPostDto(
				post.getId(),
				author,
				post.getContent(),
				post.getCreatedAt(),
				post.getUpdatedAt(),
				0L,
				true,
				true
		);
	}

	@Override
	public void deletePost(Long postId, Long currentUserId, boolean isAdmin) {
		CommunityPostEntity post = postRepository.findById(postId)
				.orElseThrow(() -> new NotFoundException("Post not found"));

		boolean isAuthor = currentUserId != null && currentUserId.equals(post.getAuthorId());
		if (!isAuthor && !isAdmin) {
			throw new ForbiddenException("You do not have permission to delete this post");
		}

		postRepository.delete(post);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-deleted.v1",
				"CommunityPostDeleted",
				Map.of(
						"channel", "post",
						"postId", postId,
						"deletedBy", currentUserId,
						"isAdmin", isAdmin,
						"deletedAt", Instant.now().toString()
				)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityCommentDto> getComments(Long postId, Long currentUserId, boolean isAdmin) {
		if (!postRepository.existsById(postId)) {
			throw new NotFoundException("Post not found");
		}

		List<CommunityCommentEntity> allComments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
		if (allComments.isEmpty()) {
			return Collections.emptyList();
		}

		List<CommunityCommentEntity> topLevelEntities = new ArrayList<>();
		Map<Long, List<CommunityCommentDto>> repliesMap = new HashMap<>();

		for (CommunityCommentEntity comment : allComments) {
			if (comment.getParentCommentId() == null) {
				topLevelEntities.add(comment);
			} else {
				repliesMap.computeIfAbsent(comment.getParentCommentId(), k -> new ArrayList<>());
				CommunityAuthorDto author = toAuthorDto(comment);
				boolean isAuthor = currentUserId != null && currentUserId.equals(comment.getAuthorId());
				boolean canDelete = isAuthor || isAdmin;

				repliesMap.get(comment.getParentCommentId()).add(new CommunityCommentDto(
						comment.getId(),
						comment.getPostId(),
						comment.getParentCommentId(),
						author,
						comment.getContent(),
						comment.getCreatedAt(),
						comment.getUpdatedAt(),
						isAuthor,
						canDelete,
						Collections.emptyList()
				));
			}
		}

		return topLevelEntities.stream().map(comment -> {
			CommunityAuthorDto author = toAuthorDto(comment);
			boolean isAuthor = currentUserId != null && currentUserId.equals(comment.getAuthorId());
			boolean canDelete = isAuthor || isAdmin;
			List<CommunityCommentDto> replies = repliesMap.getOrDefault(comment.getId(), Collections.emptyList());

			return new CommunityCommentDto(
					comment.getId(),
					comment.getPostId(),
					null,
					author,
					comment.getContent(),
					comment.getCreatedAt(),
					comment.getUpdatedAt(),
					isAuthor,
					canDelete,
					replies
			);
		}).toList();
	}

	@Override
	public CommunityCommentDto createComment(Long postId, Long authorId, CreateCommentRequest request) {
		CommunityPostEntity post = postRepository.findById(postId)
				.orElseThrow(() -> new NotFoundException("Post not found"));

		String content = request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Comment content cannot be empty");
		}

		Long actualParentId = null;
		if (request.parentCommentId() != null) {
			CommunityCommentEntity parentComment = commentRepository.findById(request.parentCommentId())
					.orElseThrow(() -> new NotFoundException("Parent comment not found"));

			if (!parentComment.getPostId().equals(postId)) {
				throw new BadRequestException("Parent comment does not belong to this post");
			}

			// Enforce strictly 1-level reply nesting
			actualParentId = parentComment.getParentCommentId() != null
					? parentComment.getParentCommentId()
					: parentComment.getId();
		}

		CommunityUserProfileEntity profile = userProfileRepository.findById(authorId).orElse(null);
		String authorName = profile != null ? profile.getDisplayName() : "User " + authorId;
		String authorUsername = profile != null ? profile.getUsername() : "user_" + authorId;
		String authorAvatar = profile != null ? profile.getAvatarUrl() : null;

		CommunityCommentEntity comment = new CommunityCommentEntity(
				postId,
				authorId,
				authorName,
				authorUsername,
				authorAvatar,
				actualParentId,
				content
		);
		comment = commentRepository.save(comment);

		// Resolve and save mentions
		processMentions(postId, comment.getId(), authorId, authorName, authorUsername, content);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-created.v1",
				"CommunityCommentCreated",
				Map.of(
						"channel", "comment",
						"postId", postId,
						"commentId", comment.getId(),
						"authorId", authorId,
						"authorName", authorName,
						"content", content,
						"createdAt", comment.getCreatedAt().toString()
				)
		);

		CommunityAuthorDto author = new CommunityAuthorDto(authorId, authorName, authorUsername, authorAvatar);
		return new CommunityCommentDto(
				comment.getId(),
				comment.getPostId(),
				comment.getParentCommentId(),
				author,
				comment.getContent(),
				comment.getCreatedAt(),
				comment.getUpdatedAt(),
				true,
				true,
				Collections.emptyList()
		);
	}

	@Override
	public void deleteComment(Long commentId, Long currentUserId, boolean isAdmin) {
		CommunityCommentEntity comment = commentRepository.findById(commentId)
				.orElseThrow(() -> new NotFoundException("Comment not found"));

		boolean isAuthor = currentUserId != null && currentUserId.equals(comment.getAuthorId());
		if (!isAuthor && !isAdmin) {
			throw new ForbiddenException("You do not have permission to delete this comment");
		}

		commentRepository.delete(comment);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-deleted.v1",
				"CommunityCommentDeleted",
				Map.of(
						"channel", "comment",
						"commentId", commentId,
						"deletedBy", currentUserId,
						"isAdmin", isAdmin,
						"deletedAt", Instant.now().toString()
				)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MentionSuggestionDto> getMentionSuggestions(String query) {
		String clean = query != null ? query.trim().replace("@", "") : "";
		Pageable pageable = PageRequest.of(0, 8);
		List<CommunityUserProfileEntity> users = userProfileRepository.findMentionSuggestions(clean.isEmpty() ? null : clean, pageable);

		return users.stream()
				.map(u -> new MentionSuggestionDto(
						u.getUserId(),
						u.getUsername(),
						u.getDisplayName(),
						u.getAvatarUrl()
				))
				.toList();
	}

	private void processMentions(Long postId, Long commentId, Long authorId, String authorName, String authorUsername, String text) {
		Matcher matcher = MENTION_PATTERN.matcher(text);
		Set<String> mentionedUsernames = new HashSet<>();

		while (matcher.find()) {
			mentionedUsernames.add(matcher.group(1).toLowerCase());
		}

		for (String username : mentionedUsernames) {
			userProfileRepository.findByUsername(username).ifPresent(targetUser -> {
				if (!mentionRepository.existsByPostIdAndCommentIdAndMentionedUserId(postId, commentId, targetUser.getUserId())) {
					mentionRepository.save(new CommunityMentionEntity(postId, commentId, targetUser.getUserId()));

					if (!targetUser.getUserId().equals(authorId)) {
						String snippet = text.length() > 80 ? text.substring(0, 77) + "..." : text;
						String title = "New mention in Community";
						String message = authorName + " (@" + authorUsername + ") mentioned you: \"" + snippet + "\"";
						String eventKey = "mention:post:" + postId + (commentId != null ? ":comment:" + commentId : "") + ":user:" + targetUser.getUserId();

						outboxEventWriter.writeEvent(
								"peergeek.community.mention-created.v1",
								"CommunityMentionCreated",
								Map.of(
										"targetUserId", targetUser.getUserId(),
										"title", title,
										"message", message,
										"resourceType", "COMMUNITY_POST",
										"resourceId", postId,
										"actionUrl", "/community",
										"eventKey", eventKey
								)
						);
					}
				}
			});
		}
	}

	private CommunityAuthorDto toAuthorDto(CommunityPostEntity post) {
		String name = post.getAuthorName() != null ? post.getAuthorName() : "user_" + post.getAuthorId();
		String username = post.getAuthorUsername() != null ? post.getAuthorUsername() : "user_" + post.getAuthorId();
		return new CommunityAuthorDto(post.getAuthorId(), name, username, post.getAuthorAvatarUrl());
	}

	private CommunityAuthorDto toAuthorDto(CommunityCommentEntity comment) {
		String name = comment.getAuthorName() != null ? comment.getAuthorName() : "user_" + comment.getAuthorId();
		String username = comment.getAuthorUsername() != null ? comment.getAuthorUsername() : "user_" + comment.getAuthorId();
		return new CommunityAuthorDto(comment.getAuthorId(), name, username, comment.getAuthorAvatarUrl());
	}
}

