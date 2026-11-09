package com.peergeek.community.websocket;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.directmessage.DirectMessageService;
import com.peergeek.security.CurrentUserPrincipal;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final GlobalCommunityBlockRepository blockRepository;
	private final DirectMessageService directMessageService;

	@Autowired
	public WebSocketAuthChannelInterceptor(
			JwtService jwtService,
			UserRepository userRepository,
			GlobalCommunityBlockRepository blockRepository,
			@Lazy DirectMessageService directMessageService) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.blockRepository = blockRepository;
		this.directMessageService = directMessageService;
	}

	public WebSocketAuthChannelInterceptor(
			JwtService jwtService,
			UserRepository userRepository,
			GlobalCommunityBlockRepository blockRepository) {
		this(jwtService, userRepository, blockRepository, null);
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}

		StompCommand command = accessor.getCommand();
		if (StompCommand.CONNECT.equals(command)) {
			return authenticateConnection(message, accessor);
		} else if (StompCommand.SUBSCRIBE.equals(command)) {
			authorizeSubscription(accessor);
		} else if (StompCommand.SEND.equals(command)) {
			authorizeSend(accessor);
		}

		return message;
	}

	private Message<?> authenticateConnection(Message<?> message, StompHeaderAccessor accessor) {
		String authHeader = accessor.getFirstNativeHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			// Also check lowercase 'authorization'
			authHeader = accessor.getFirstNativeHeader("authorization");
		}

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new BadCredentialsException("Missing or invalid Authorization header in STOMP connect");
		}

		String token = authHeader.substring(7);
		try {
			JwtService.TokenClaims claims = jwtService.parse(token);
			Optional<UserEntity> userOpt = userRepository.findById(claims.userId())
					.filter(UserEntity::isEnabled)
					.filter(UserEntity::isEmailVerified);

			if (userOpt.isEmpty()) {
				throw new BadCredentialsException("Token user not found, disabled, or unverified");
			}

			UserEntity user = userOpt.get();
			if (!user.getRole().name().equals(claims.role())) {
				throw new BadCredentialsException("Token role mismatch");
			}

			CurrentUserPrincipal principal = new CurrentUserPrincipal(user.getId(), user.getRole());
			var authentication = new UsernamePasswordAuthenticationToken(
					principal,
					null,
					List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
			);

			if (accessor.isMutable()) {
				accessor.setUser(authentication);
				return message;
			}

			return org.springframework.messaging.support.MessageBuilder.fromMessage(message)
					.setHeader(org.springframework.messaging.simp.SimpMessageHeaderAccessor.USER_HEADER, authentication)
					.build();
		} catch (BadCredentialsException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BadCredentialsException("Failed to authenticate STOMP session: " + ex.getMessage(), ex);
		}
	}

	private void authorizeSubscription(StompHeaderAccessor accessor) {
		Principal principal = accessor.getUser();
		if (principal == null) {
			throw new AccessDeniedException("Authentication required to subscribe to community topics");
		}

		String destination = accessor.getDestination();
		if (destination == null) {
			return;
		}
		if (destination.startsWith("/user/queue/direct-messages/")) {
			String id = destination.substring("/user/queue/direct-messages/".length());
			try {
				Long conversationId = Long.valueOf(id);
				Long userId = authenticatedUserId(principal);
				if (directMessageService == null || !directMessageService.isParticipant(userId, conversationId)) {
					throw new AccessDeniedException("You are not a participant in this conversation");
				}
			} catch (NumberFormatException ex) {
				throw new AccessDeniedException("Invalid direct conversation");
			}
		}

		// Restricted admin topics require ROLE_ADMIN
		if (destination.startsWith("/topic/admin.") || destination.startsWith("/topic/admin/")) {
			if (principal instanceof UsernamePasswordAuthenticationToken authToken) {
				boolean isAdmin = authToken.getAuthorities().stream()
						.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
				if (!isAdmin) {
					throw new AccessDeniedException("Administrative privileges required to subscribe to " + destination);
				}

			} else {
				throw new AccessDeniedException("Administrative privileges required to subscribe to " + destination);
			}
		}

	}

	private void authorizeSend(StompHeaderAccessor accessor) {
		Principal principal = accessor.getUser();
		if (principal == null) {
			throw new AccessDeniedException("Authentication required to send community messages");
		}

		Long userId = null;
		if (principal instanceof UsernamePasswordAuthenticationToken authToken
				&& authToken.getPrincipal() instanceof CurrentUserPrincipal p) {
			userId = p.id();
		}

		if (userId != null) {
			String destination = accessor.getDestination();
			if (destination != null && destination.startsWith("/app/direct.") && destination.endsWith(".send")) {
				String id = destination.substring("/app/direct.".length(), destination.length() - ".send".length());
				try {
					if (directMessageService == null || !directMessageService.isParticipant(userId, Long.valueOf(id))) {
						throw new AccessDeniedException("You are not a participant in this conversation");
					}
				} catch (NumberFormatException ex) {
					throw new AccessDeniedException("Invalid direct conversation");
				}
			}
			if (destination != null && (destination.contains("community.global") || destination.contains("community/global")
					|| destination.contains("community.domain") || destination.contains("community/domain"))) {
				if (blockRepository.isUserBlocked(userId)) {
					throw new AccessDeniedException("You are blocked from participating in the PeerGeek community");
				}
			}

		}
	}

	private Long authenticatedUserId(Principal principal) {
		if (principal instanceof UsernamePasswordAuthenticationToken auth
				&& auth.getPrincipal() instanceof CurrentUserPrincipal p) return p.id();
		throw new AccessDeniedException("Authentication required");
	}
}
