package com.bte.service.mail;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true", matchIfMissing = true)
public class EmailEventListener {

	private final EmailNotificationService emailNotificationService;

	public EmailEventListener(EmailNotificationService emailNotificationService) {
		this.emailNotificationService = emailNotificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRegistrationTicket(RegistrationTicketEvent event) {
		emailNotificationService.sendRegistrationTicket(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRoomCredentials(RoomCredentialsEvent event) {
		emailNotificationService.sendRoomCredentials(event);
	}
}
