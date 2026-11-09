package com.peergeek.directmessage;

import com.peergeek.common.PageResponse;
import com.peergeek.directmessage.dto.*;
import com.peergeek.security.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/direct-messages")
public class DirectMessageController {
	private final DirectMessageService service; private final CurrentUserProvider current;
	public DirectMessageController(DirectMessageService service, CurrentUserProvider current){this.service=service;this.current=current;}
	@GetMapping("/conversations") public List<DirectConversationDto> conversations(){return service.listConversations(current.requireCurrentUser().id());}
	@PostMapping("/conversations/{otherUserId}")
	public DirectConversationDto getOrCreate(@PathVariable Long otherUserId) {
		return service.getOrCreateConversation(current.requireCurrentUser().id(), otherUserId);
	}
	@GetMapping("/conversations/{otherUserId}/messages")
	public PageResponse<DirectMessageDto> messages(@PathVariable Long otherUserId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
		return service.getMessagesByConversation(current.requireCurrentUser().id(),otherUserId,page,size);
	}
	@GetMapping("/by-user/{otherUserId}/messages")
	public PageResponse<DirectMessageDto> messagesByUser(@PathVariable Long otherUserId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
		return service.getMessages(current.requireCurrentUser().id(),otherUserId,page,size);
	}
	@PostMapping("/conversations/{otherUserId}/messages")
	public ResponseEntity<DirectMessageDto> send(@PathVariable Long otherUserId,@Valid @RequestBody SendDirectMessageRequest request){
		DirectMessageDto dto=service.sendToConversation(current.requireCurrentUser().id(),otherUserId,request);
		return ResponseEntity.status(HttpStatus.CREATED).body(dto);
	}
	@PostMapping("/by-user/{otherUserId}/messages")
	public ResponseEntity<DirectMessageDto> sendByUser(@PathVariable Long otherUserId,@Valid @RequestBody SendDirectMessageRequest request){
		return ResponseEntity.status(HttpStatus.CREATED).body(service.send(current.requireCurrentUser().id(),otherUserId,request));
	}
	@PostMapping("/conversations/{conversationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT) public void readLatest(@PathVariable Long conversationId){
		service.markReadLatest(current.requireCurrentUser().id(),conversationId);
	}
	@PostMapping("/conversations/{otherUserId}/read/{messageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable Long otherUserId,@PathVariable Long messageId){
		service.markRead(current.requireCurrentUser().id(),otherUserId,messageId);
	}
}
