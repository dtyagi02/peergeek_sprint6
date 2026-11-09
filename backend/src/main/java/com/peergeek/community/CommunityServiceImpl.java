package com.peergeek.community;

import com.peergeek.common.PageResponse;
import com.peergeek.community.dto.CommunityAuthorDto;
import com.peergeek.community.dto.CommunityCommentDto;
import com.peergeek.community.dto.CommunityPostDto;
import com.peergeek.community.dto.CreateCommentRequest;
import com.peergeek.community.dto.CreatePostRequest;
import com.peergeek.community.dto.MentionSuggestionDto;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityServiceImpl implements CommunityService {

	private static final Logger log = LoggerFactory.getLogger(CommunityServiceImpl.class);
	private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_]{3,30})\\b");

	private final CommunityPostRepository postRepository;
	private final CommunityCommentRepository commentRepository;
	private final CommunityMentionRepository mentionRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

	public CommunityServiceImpl(
			CommunityPostRepository postRepository,
			CommunityCommentRepository commentRepository,
			CommunityMentionRepository mentionRepository,
			UserRepository userRepository,
			NotificationService notificationService) {
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.mentionRepository = mentionRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<CommunityPostDto> getPosts(Pageable pageable, Long currentUserId, boolean isAdmin) {
		Page<CommunityPostEntity> page = postRepository.findAllByOrderByCreatedAtDesc(pageable);
		List<Long> authorIds = page.getContent().stream()
				.map(CommunityPostEntity::getAuthorId)
				.distinct()
				.toList();
		Map<Long, CommunityAuthorDto> authorMap = loadAuthorMap(authorIds);

		List<CommunityPostDto> items = page.getContent().stream().map(post -> {
			CommunityAuthorDto author = authorMap.getOrDefault(post.getAuthorId(), fallbackAuthor(post.getAuthorId()));
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
		CommunityAuthorDto author = getAuthor(post.getAuthorId());
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
		UserEntity authorEntity = userRepository.findById(authorId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		String content = request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Post content cannot be empty");
		}

		CommunityPostEntity post = new CommunityPostEntity(authorId, content);
		post = postRepository.save(post);

		// Resolve and save mentions
		processMentions(post.getId(), null, authorEntity, content);

		CommunityAuthorDto author = toAuthorDto(authorEntity);
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

		List<Long> authorIds = allComments.stream()
				.map(CommunityCommentEntity::getAuthorId)
				.distinct()
				.toList();
		Map<Long, CommunityAuthorDto> authorMap = loadAuthorMap(authorIds);

		// Separate top-level comments and replies
		List<CommunityCommentEntity> topLevelEntities = new ArrayList<>();
		Map<Long, List<CommunityCommentDto>> repliesMap = new HashMap<>();

		for (CommunityCommentEntity comment : allComments) {
			if (comment.getParentCommentId() == null) {
				topLevelEntities.add(comment);
			} else {
				repliesMap.computeIfAbsent(comment.getParentCommentId(), k -> new ArrayList<>());
				CommunityAuthorDto author = authorMap.getOrDefault(comment.getAuthorId(), fallbackAuthor(comment.getAuthorId()));
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

		// Build final top-level comments with replies attached
		return topLevelEntities.stream().map(comment -> {
			CommunityAuthorDto author = authorMap.getOrDefault(comment.getAuthorId(), fallbackAuthor(comment.getAuthorId()));
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
		UserEntity authorEntity = userRepository.findById(authorId)
				.orElseThrow(() -> new NotFoundException("User not found"));

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
			// If replying to a reply, attach to the top-level parent comment
			actualParentId = parentComment.getParentCommentId() != null
					? parentComment.getParentCommentId()
					: parentComment.getId();
		}

		CommunityCommentEntity comment = new CommunityCommentEntity(postId, authorId, actualParentId, content);
		comment = commentRepository.save(comment);

		// Resolve and save mentions
		processMentions(postId, comment.getId(), authorEntity, content);

		CommunityAuthorDto author = toAuthorDto(authorEntity);
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
	}

	@Override
	@Transactional(readOnly = true)
	public List<MentionSuggestionDto> getMentionSuggestions(String query) {
		String clean = query != null ? query.trim().replace("@", "") : "";
		Pageable pageable = PageRequest.of(0, 8);
		List<UserEntity> users = userRepository.findMentionSuggestions(clean.isEmpty() ? null : clean, pageable);

		return users.stream()
				.map(u -> new MentionSuggestionDto(
						u.getId(),
						u.getUsername(),
						u.getDisplayName(),
						u.getAvatarUrl()
				))
				.toList();
	}

	private void processMentions(Long postId, Long commentId, UserEntity author, String text) {
		Matcher matcher = MENTION_PATTERN.matcher(text);
		Set<String> mentionedUsernames = new HashSet<>();

		while (matcher.find()) {
			mentionedUsernames.add(matcher.group(1).toLowerCase());
		}

		for (String username : mentionedUsernames) {
			Optional<UserEntity> targetUserOpt = userRepository.findByUsername(username);
			if (targetUserOpt.isPresent()) {
				UserEntity targetUser = targetUserOpt.get();
				// Avoid saving duplicate mention relationship
				if (!mentionRepository.existsByPostIdAndCommentIdAndMentionedUserId(postId, commentId, targetUser.getId())) {
					mentionRepository.save(new CommunityMentionEntity(postId, commentId, targetUser.getId()));

					// Notify target user if not the author themselves
					if (!targetUser.getId().equals(author.getId())) {
						try {
							String snippet = text.length() > 80 ? text.substring(0, 77) + "..." : text;
							String title = "New mention in Community";
							String message = author.getDisplayName() + " (@" + author.getUsername() + ") mentioned you: \"" + snippet + "\"";
							String eventKey = "mention:post:" + postId + (commentId != null ? ":comment:" + commentId : "") + ":user:" + targetUser.getId();

							notificationService.createNotification(
									targetUser.getId(),
									NotificationType.COMMUNITY_MENTION,
									title,
									message,
									"COMMUNITY_POST",
									postId,
									"/community",
									eventKey
							);
						} catch (Exception e) {
							log.warn("Failed to create mention notification for user {}: {}", targetUser.getId(), e.getMessage());
						}
					}
				}
			}
		}
	}

	private Map<Long, CommunityAuthorDto> loadAuthorMap(List<Long> authorIds) {
		if (authorIds.isEmpty()) return Collections.emptyMap();
		List<UserEntity> users = userRepository.findAllById(authorIds);
		Map<Long, CommunityAuthorDto> map = new HashMap<>();
		for (UserEntity user : users) {
			map.put(user.getId(), toAuthorDto(user));
		}
		return map;
	}

	private CommunityAuthorDto getAuthor(Long authorId) {
		return userRepository.findById(authorId)
				.map(this::toAuthorDto)
				.orElseGet(() -> fallbackAuthor(authorId));
	}

	private CommunityAuthorDto toAuthorDto(UserEntity user) {
		return new CommunityAuthorDto(
				user.getId(),
				user.getDisplayName(),
				user.getUsername(),
				user.getAvatarUrl()
		);
	}

	private CommunityAuthorDto fallbackAuthor(Long authorId) {
		return new CommunityAuthorDto(
				authorId,
				"Community Member",
				"user_" + authorId,
				null
		);
	}
}
