package com.bte.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@NotBlank
	@Column(nullable = false, length = 100)
	private String senderName;

	@Column(length = 254)
	private String senderEmail;

	@Column(length = 100)
	private String teamName;

	@NotBlank
	@Column(nullable = false, length = 2000)
	private String messageText;

	@Column(nullable = false)
	private boolean isGuest;

	@NotBlank
	@Column(nullable = false, length = 2048)
	private String senderAvatarUrl;

	@Column(nullable = false, updatable = false)
	private LocalDateTime timestamp;

	@PrePersist
	void onCreate() {
		timestamp = LocalDateTime.now();
	}
}
