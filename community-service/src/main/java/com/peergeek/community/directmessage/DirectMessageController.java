package com.peergeek.community.directmessage;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.directmessage.dto.DirectConversationDto;
import com.peergeek.community.directmessage.dto.DirectMessageDto;
import com.peergeek.community.directmessage.dto.SendDirectMessageRequest;
import com.peergeek.community.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/direct-messages")
public class DirectMessageController {

	private final DirectMessageService service;
	private final CurrentUserProvider current;

	public DirectMessageController(DirectMessageService service, CurrentUserProvider current) {
		this.service = service;
		this.current = current;
	}

	@GetMapping("/conversations")
	public List<DirectConversationDto> conversations() {
		return service.listConversations(current.requireCurrentUser().id());
	}

	@PostMapping("/conversations/{otherUserId}")
	public DirectConversationDto getOrCreate(@PathVariable("otherUserId") Long otherUserId) {
		return service.getOrCreateConversation(current.requireCurrentUser().id(), otherUserId);
	}

	@GetMapping("/conversations/{otherUserId}/messages")
	public PageResponse<DirectMessageDto> messages(
			@PathVariable("otherUserId") Long otherUserId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "50") int size) {
		return service.getMessagesByConversation(current.requireCurrentUser().id(), otherUserId, page, size);
	}

	@GetMapping("/by-user/{otherUserId}/messages")
	public PageResponse<DirectMessageDto> messagesByUser(
			@PathVariable("otherUserId") Long otherUserId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "50") int size) {
		return service.getMessages(current.requireCurrentUser().id(), otherUserId, page, size);
	}

	@PostMapping("/conversations/{otherUserId}/messages")
	public ResponseEntity<DirectMessageDto> send(
			@PathVariable("otherUserId") Long otherUserId,
			@Valid @RequestBody SendDirectMessageRequest request) {
		DirectMessageDto dto = service.sendToConversation(current.requireCurrentUser().id(), otherUserId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(dto);
	}

	@PostMapping("/by-user/{otherUserId}/messages")
	public ResponseEntity<DirectMessageDto> sendByUser(
			@PathVariable("otherUserId") Long otherUserId,
			@Valid @RequestBody SendDirectMessageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.send(current.requireCurrentUser().id(), otherUserId, request));
	}

	@PostMapping("/conversations/{conversationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void readLatest(@PathVariable("conversationId") Long conversationId) {
		service.markReadLatest(current.requireCurrentUser().id(), conversationId);
	}

	@PostMapping("/conversations/{otherUserId}/read/{messageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void read(
			@PathVariable("otherUserId") Long otherUserId,
			@PathVariable("messageId") Long messageId) {
		service.markRead(current.requireCurrentUser().id(), otherUserId, messageId);
	}
}

