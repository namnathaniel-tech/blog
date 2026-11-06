package dev.blog.auth;

import dev.blog.config.BlogProperties;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the OTP email asynchronously after the code is committed, so response timing
 * doesn't reveal whether the requested address was the admin's (D-004).
 */
@Component
class AdminMailer {

    private static final Logger log = LoggerFactory.getLogger(AdminMailer.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final BlogProperties props;

    AdminMailer(JavaMailSender mailSender, BlogProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    void onOtpIssued(OtpIssuedEvent event) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.mailFrom());
        msg.setTo(props.adminEmail());
        msg.setSubject(event.code() + " is your " + props.siteName() + " login code");
        msg.setText("""
                Your login code is: %s

                It expires at %s and can be used once.

                If you didn't try to log in, you can ignore this email. Nobody can log in without this code.
                """.formatted(event.code(), TIME.format(event.expiresAt())));
        try {
            mailSender.send(msg);
            log.info("Sent admin login code email");
        } catch (MailException e) {
            log.error("Failed to send admin login code email", e);
        }
    }
}
