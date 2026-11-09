package com.peergeek.community.websocket;

import com.peergeek.community.domain.DomainCommunityService;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.community.global.GlobalCommunityService;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CommunityWebSocketController {

	private final GlobalCommunityService globalCommunityService;
	private final DomainCommunityService domainCommunityService;

	public CommunityWebSocketController(
			GlobalCommunityService globalCommunityService,
			DomainCommunityService domainCommunityService) {
		this.globalCommunityService = globalCommunityService;
		this.domainCommunityService = domainCommunityService;
	}

	@MessageMapping("/community.global.send")
	public void sendGlobalMessage(@Valid @Payload CreateGlobalMessageRequest request, Principal principal) {
		Long userId = extractUserId(principal);
		globalCommunityService.postMessage(userId, request);
	}

	@MessageMapping("/community.domain.{slug}.send")
	public void sendDomainMessage(
			@DestinationVariable("slug") String slug,
			@Valid @Payload SendDomainMessageRequest request,
			Principal principal) {
		Long userId = extractUserId(principal);
		domainCommunityService.sendMessage(slug, userId, request);
	}

	private Long extractUserId(Principal principal) {
		if (principal instanceof UsernamePasswordAuthenticationToken authToken
				&& authToken.getPrincipal() instanceof CurrentUserPrincipal p) {
			return p.id();
		}
		throw new AccessDeniedException("Authenticated user required to send community messages");
	}
}

