package com.bte.service;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import com.bte.exception.ServiceUnavailableException;
@Service
public class VerificationMailService {
    private final JavaMailSender sender;private final String from;private final String origin;
    public VerificationMailService(JavaMailSender sender,@Value("${spring.mail.username:}")String from,@Value("${app.auth.allowed-origin}")String origin){this.sender=sender;this.from=from;this.origin=origin;}
    public void send(String email,String token) {
        if(from.isBlank() || from.startsWith("your_"))throw new ServiceUnavailableException("Email verification requires configured SMTP credentials. Please try again after email service setup");
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);message.setSubject("Verify your BattleTrust email");
        message.setText("Verify your BattleTrust account using this private link (expires in 30 minutes):\n"+origin+"/verify-email?token="+token+"\n\nIf you did not create this account, ignore this email. Do not share this link.");
        try{sender.send(message);}catch(org.springframework.mail.MailException error){throw new ServiceUnavailableException("Verification email could not be sent. Please try again later");}
    }
}
