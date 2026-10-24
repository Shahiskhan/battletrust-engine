package com.bte.controller;

import com.bte.dto.ChatMessageResponse;
import com.bte.dto.ChatSendRequest;
import com.bte.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/send")
	public ResponseEntity<ChatMessageResponse> send(
			@Valid @RequestBody ChatSendRequest request,
			@org.springframework.web.bind.annotation.RequestHeader(value = "X-Registration-Ticket", required = false) String ticket,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(chatService.send(request, authentication, ticket));
	}

	@GetMapping("/tournament/{tournamentId}")
	public Page<ChatMessageResponse> history(
			@PathVariable @NotNull @Min(1) Long tournamentId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "30") @Min(1) int size,
			@org.springframework.web.bind.annotation.RequestHeader(value = "X-Registration-Ticket", required = false) String ticket,
			Authentication authentication) {
		return chatService.history(tournamentId, page, size, authentication, ticket);
	}
}
