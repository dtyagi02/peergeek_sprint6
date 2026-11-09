package com.peergeek.community.websocket;

import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.community.security.CurrentUser;
import com.peergeek.community.security.CurrentUserPrincipal;
import com.peergeek.community.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtService jwtService;
	private final GlobalCommunityBlockRepository blockRepository;

	public WebSocketAuthChannelInterceptor(
			JwtService jwtService,
			GlobalCommunityBlockRepository blockRepository) {
		this.jwtService = jwtService;
		this.blockRepository = blockRepository;
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
			authHeader = accessor.getFirstNativeHeader("authorization");
		}

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new BadCredentialsException("Missing or invalid Authorization header in STOMP connect");
		}

		String token = authHeader.substring(7);
		try {
			CurrentUser user = jwtService.parseToken(token);
			CurrentUserPrincipal principal = new CurrentUserPrincipal(user.id(), user.role());
			var authentication = new UsernamePasswordAuthenticationToken(
					principal,
					null,
					List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
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
			if (destination != null && (destination.contains("community.global") || destination.contains("community/global")
					|| destination.contains("community.domain") || destination.contains("community/domain"))) {
				if (blockRepository.isUserBlocked(userId)) {
					throw new AccessDeniedException("You are blocked from participating in the PeerGeek community");
				}
			}
		}
	}
}

