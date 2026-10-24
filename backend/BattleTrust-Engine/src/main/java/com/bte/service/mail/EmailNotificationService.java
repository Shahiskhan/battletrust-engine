package com.bte.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public EmailNotificationService(
			JavaMailSender mailSender,
			@org.springframework.beans.factory.annotation.Value("${spring.mail.username}") String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	@Async("mailTaskExecutor")
	public void sendRegistrationTicket(RegistrationTicketEvent event) {
		String subject = "Tournament registration confirmed: " + event.tournamentTitle();
		String html = """
				<html><body>
				<h2>Registration received</h2>
				<p>Hello %s,</p>
				<p>Your team <strong>%s</strong> has been registered for <strong>%s</strong>.</p>
				<p>Your registration ticket is <strong>%s</strong>.</p>
				<p>Keep this ticket private. Payment remains subject to verification.</p>
				</body></html>
				""".formatted(escape(event.captainName()), escape(event.teamName()),
				escape(event.tournamentTitle()), escape(event.ticketCode()));
		sendHtml(event.email(), subject, html);
	}

	@Async("mailTaskExecutor")
	public void sendRoomCredentials(RoomCredentialsEvent event) {
		String subject = "Tournament room credentials: " + event.tournamentTitle();
		String html = """
				<html><body>
				<h2>Match room details</h2>
				<p>Hello %s,</p>
				<p>Team <strong>%s</strong> — <strong>%s</strong> begins soon.</p>
				<p>Room ID: <strong>%s</strong><br/>Password: <strong>%s</strong></p>
				<p>Do not share these credentials publicly.</p>
				</body></html>
				""".formatted(escape(event.captainName()), escape(event.teamName()),
				escape(event.tournamentTitle()), escape(event.roomId()), escape(event.roomPassword()));
		sendHtml(event.email(), subject, html);
	}

	private void sendHtml(String to, String subject, String html) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromAddress);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(html, true);
			mailSender.send(message);
		} catch (MailException | MessagingException exception) {
			LOGGER.error("Failed to send tournament email to {}", to, exception);
		}
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
