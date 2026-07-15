package com.wedjan.api.auth;

import com.wedjan.api.config.WedjanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Transactional email (Mailpit locally, Resend in production via SMTP).
 * Sends are async and failure-tolerant: a mail outage must never fail an
 * auth request — failures are logged for ops.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, WedjanProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Async
    public void sendSignupOtp(String email, String code) {
        send(email, "Your wedjan verification code",
                "Welcome to wedjan!\n\nYour verification code is: " + code
                        + "\n\nIt expires in 15 minutes. If you didn't create an account, ignore this email.");
    }

    @Async
    public void sendPasswordResetOtp(String email, String code) {
        send(email, "Reset your wedjan password",
                "Your wedjan password reset code is: " + code
                        + "\n\nIt expires in 15 minutes. If you didn't request this, ignore this email.");
    }

    @Async
    public void sendAccountExistsNotice(String email) {
        send(email, "You already have a wedjan account",
                "Someone (hopefully you) tried to sign up with this email, but an account already exists."
                        + "\n\nYou can log in, or reset your password if you've forgotten it."
                        + "\n\nIf this wasn't you, no action is needed.");
    }

    @Async
    public void sendVendorVerified(String email, String businessName, String publicUrl) {
        send(email, "Your wedjan listing is live",
                "Congratulations, " + businessName + " is verified and live on wedjan.\n\n"
                        + "View your public listing: " + publicUrl);
    }

    @Async
    public void sendShowcaseTagRequest(String email, String showcaseTitle, String ownerName,
            String dashboardUrl) {
        send(email, "Confirm your credit on " + showcaseTitle,
                ownerName + " tagged your business in the real event “" + showcaseTitle + "”.\n\n"
                        + "Accept the credit before your name appears publicly: " + dashboardUrl
                        + "\n\nYou can decline if this association is not accurate.");
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {}", subject, to, e);
        }
    }
}
