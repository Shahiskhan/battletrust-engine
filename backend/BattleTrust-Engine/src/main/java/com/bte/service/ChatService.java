package com.bte.service;

import com.bte.dto.ChatMessageResponse;
import com.bte.dto.ChatSendRequest;
import com.bte.entity.ChatMessage;
import com.bte.entity.Tournament;
import com.bte.entity.User;
import com.bte.exception.BadRequestException;
import com.bte.exception.ResourceNotFoundException;
import com.bte.repository.ChatMessageRepository;
import com.bte.repository.TournamentRepository;
import com.bte.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

	private static final String DEFAULT_AVATAR_URL = "/images/default-avatar.svg";
	private static final int MAX_PAGE_SIZE = 100;

	private final ChatMessageRepository chatMessageRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final TournamentAccessService access;

	public ChatService(
			ChatMessageRepository chatMessageRepository,
			TournamentRepository tournamentRepository,
			UserRepository userRepository, TournamentAccessService access) {
		this.chatMessageRepository = chatMessageRepository;
		this.tournamentRepository = tournamentRepository;
		this.userRepository = userRepository;
		this.access = access;
	}

	@Transactional
	public ChatMessageResponse send(ChatSendRequest request, Authentication authentication) {
		return send(request, authentication, null);
	}

	@Transactional
	public ChatMessageResponse send(ChatSendRequest request, Authentication authentication, String ticket) {
		Tournament tournament = tournamentRepository.findById(request.tournamentId())
				.orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		access.requireAccess(tournament, authentication, ticket);
        if(!tournament.isRoomChatEnabled() && !access.manages(tournament,authentication))throw new org.springframework.security.access.AccessDeniedException("Chat is muted by the organizer");
		boolean guest = isGuest(authentication);
		String senderName = request.senderName().trim();
		String senderEmail = normalizeOptionalEmail(request.senderEmail());
		String teamName = request.teamName() == null ? null : request.teamName().trim();
		String avatarUrl = DEFAULT_AVATAR_URL;
		if (guest) {
			var entry = access.requireEntry(tournament, authentication, ticket);
			senderName = entry.getCaptainName();
			senderEmail = null;
			teamName = entry.getTeamName();
		}

		if (!guest) {
			User user = userRepository.findByEmail(authentication.getName())
					.orElseThrow(() -> new ResourceNotFoundException("Sender account was not found"));
			senderName = user.getName();
			senderEmail = user.getEmail();
			avatarUrl = user.getProfilePictureUrl() == null || user.getProfilePictureUrl().isBlank()
					? DEFAULT_AVATAR_URL
					: user.getProfilePictureUrl();
		}

		ChatMessage message = ChatMessage.builder()
				.tournament(tournament)
				.senderName(senderName)
				.senderEmail(senderEmail)
				.teamName(teamName)
				.messageText(request.messageText().trim())
				.isGuest(guest)
				.senderAvatarUrl(avatarUrl)
				.build();
		return toResponse(chatMessageRepository.save(message));
	}

	@Transactional(readOnly = true)
	public Page<ChatMessageResponse> history(Long tournamentId, int page, int size, Authentication authentication, String ticket) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BadRequestException("Page must be non-negative and size must be between 1 and 100");
		}
		Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow(() -> new ResourceNotFoundException("Tournament was not found"));
		access.requireAccess(tournament, authentication, ticket);
		return chatMessageRepository
				.findByTournamentIdOrderByTimestampDesc(tournamentId, PageRequest.of(page, size))
				.map(this::toResponse);
	}

	private ChatMessageResponse toResponse(ChatMessage message) {
		return new ChatMessageResponse(
				message.getId(),
				message.getTournament().getId(),
				message.getSenderName(),
				message.getSenderEmail(),
				message.getTeamName(),
				message.getMessageText(),
				message.isGuest(),
				message.getSenderAvatarUrl(),
				message.getTimestamp());
	}

	private boolean isGuest(Authentication authentication) {
		return authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken;
	}

	private String normalizeOptionalEmail(String email) {
		return email == null || email.isBlank()
				? null
				: email.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
