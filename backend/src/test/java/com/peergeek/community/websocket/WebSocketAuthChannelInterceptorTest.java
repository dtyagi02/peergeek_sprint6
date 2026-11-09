package com.peergeek.community.websocket;

import java.security.Principal;
import java.util.Optional;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.directmessage.DirectMessageService;
import com.peergeek.security.CurrentUserPrincipal;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRole;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private GlobalCommunityBlockRepository blockRepository;

	@Mock
	private MessageChannel channel;

	@Mock
	private DirectMessageService directMessageService;

	private WebSocketAuthChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new WebSocketAuthChannelInterceptor(jwtService, userRepository, blockRepository);
	}

	@Test
	@DisplayName("CONNECT: successfully authenticates with valid Bearer token")
	void connectSuccessWithValidToken() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", "Bearer valid-jwt-token");
		accessor.setLeaveMutable(true);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		JwtService.TokenClaims claims = new JwtService.TokenClaims(42L, "USER");
		when(jwtService.parse("valid-jwt-token")).thenReturn(claims);

		UserEntity user = new UserEntity("user@peergeek.com", "hash", "Geek", "geek", true);
		user.setRole(UserRole.USER);
		org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));

		Message<?> result = interceptor.preSend(message, channel);
		assertThat(result).isNotNull();

		StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
		Principal userPrincipal = resultAccessor.getUser();
		assertThat(userPrincipal).isNotNull();
		assertThat(userPrincipal).isInstanceOf(UsernamePasswordAuthenticationToken.class);

		UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) userPrincipal;
		CurrentUserPrincipal principal = (CurrentUserPrincipal) authToken.getPrincipal();
		assertThat(principal.id()).isEqualTo(42L);
		assertThat(principal.role()).isEqualTo(UserRole.USER);
		assertThat(authToken.getAuthorities()).anyMatch(a -> "ROLE_USER".equals(a.getAuthority()));
	}

	@Test
	@DisplayName("CONNECT: throws BadCredentialsException when Authorization header is missing")
	void connectFailsWhenMissingAuthHeader() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessageContaining("Missing or invalid Authorization header");
	}

	@Test
	@DisplayName("CONNECT: throws BadCredentialsException when user does not exist or role mismatch")
	void connectFailsWhenUserNotFound() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", "Bearer invalid-token");
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(jwtService.parse("invalid-token")).thenThrow(new IllegalArgumentException("Expired or invalid signature"));

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessageContaining("Failed to authenticate STOMP session");
	}

	@Test
	@DisplayName("SUBSCRIBE: regular user can subscribe to public community topics")
	void regularUserCanSubscribeToPublicTopics() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/community.global");

		CurrentUserPrincipal principal = new CurrentUserPrincipal(10L, UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
		);
		accessor.setUser(auth);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, channel);
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("SUBSCRIBE: regular user is forbidden from subscribing to /topic/admin.community")
	void regularUserCannotSubscribeToAdminTopic() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/admin.community");

		CurrentUserPrincipal principal = new CurrentUserPrincipal(10L, UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
		);
		accessor.setUser(auth);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Administrative privileges required");
	}

	@Test
	@DisplayName("SUBSCRIBE: admin user is permitted to subscribe to /topic/admin.community")
	void adminUserCanSubscribeToAdminTopic() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/admin.community");

		CurrentUserPrincipal principal = new CurrentUserPrincipal(1L, UserRole.ADMIN);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
		);
		accessor.setUser(auth);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, channel);
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("SEND: blocked user is forbidden from sending messages via STOMP")
	void blockedUserCannotSendMessageViaStomp() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/community.global.send");

		CurrentUserPrincipal principal = new CurrentUserPrincipal(25L, UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
		);
		accessor.setUser(auth);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(blockRepository.isUserBlocked(25L)).thenReturn(true);

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("You are blocked from participating");
	}

	@Test
	@DisplayName("SEND: non-blocked user is permitted to send messages via STOMP")
	void activeUserCanSendMessageViaStomp() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/community.global.send");

		CurrentUserPrincipal principal = new CurrentUserPrincipal(25L, UserRole.USER);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
		);
		accessor.setUser(auth);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		when(blockRepository.isUserBlocked(25L)).thenReturn(false);

		Message<?> result = interceptor.preSend(message, channel);
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("SUBSCRIBE: direct-message participant can subscribe to private queue")
	void participantCanSubscribeToDirectMessageQueue() {
		WebSocketAuthChannelInterceptor dmInterceptor =
				new WebSocketAuthChannelInterceptor(jwtService, userRepository, blockRepository, directMessageService);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/user/queue/direct-messages/77");
		accessor.setUser(auth(25L));
		when(directMessageService.isParticipant(25L, 77L)).thenReturn(true);

		Message<?> result = dmInterceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("SUBSCRIBE: non-participant cannot subscribe to a private queue")
	void nonParticipantCannotSubscribeToDirectMessageQueue() {
		WebSocketAuthChannelInterceptor dmInterceptor =
				new WebSocketAuthChannelInterceptor(jwtService, userRepository, blockRepository, directMessageService);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/user/queue/direct-messages/77");
		accessor.setUser(auth(25L));
		when(directMessageService.isParticipant(25L, 77L)).thenReturn(false);

		assertThatThrownBy(() -> dmInterceptor.preSend(
				MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("not a participant");
	}

	@Test
	@DisplayName("SEND: non-participant cannot send to a direct conversation")
	void nonParticipantCannotSendDirectMessage() {
		WebSocketAuthChannelInterceptor dmInterceptor =
				new WebSocketAuthChannelInterceptor(jwtService, userRepository, blockRepository, directMessageService);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/direct.77.send");
		accessor.setUser(auth(25L));
		when(directMessageService.isParticipant(25L, 77L)).thenReturn(false);

		assertThatThrownBy(() -> dmInterceptor.preSend(
				MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("not a participant");
	}

	@Test
	@DisplayName("SEND: participant can send to a direct conversation")
	void participantCanSendDirectMessage() {
		WebSocketAuthChannelInterceptor dmInterceptor =
				new WebSocketAuthChannelInterceptor(jwtService, userRepository, blockRepository, directMessageService);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/direct.77.send");
		accessor.setUser(auth(25L));
		when(directMessageService.isParticipant(25L, 77L)).thenReturn(true);

		Message<?> result = dmInterceptor.preSend(
				MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
		assertThat(result).isNotNull();
	}

	private UsernamePasswordAuthenticationToken auth(Long userId) {
		return new UsernamePasswordAuthenticationToken(
				new CurrentUserPrincipal(userId, UserRole.USER), null,
				java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
	}
}
