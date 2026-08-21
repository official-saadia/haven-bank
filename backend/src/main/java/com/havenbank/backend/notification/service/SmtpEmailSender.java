package com.havenbank.backend.notification.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link EmailSender}. Mail is not optional here: registration requires a verification
 * link and sign-in requires a one-time passcode, so an instance that cannot send mail cannot
 * onboard or authenticate anybody.
 *
 * <p>Configuration is therefore checked at startup rather than at first send. A misconfigured
 * deployment fails immediately with a message naming the missing properties, instead of starting
 * cleanly and stranding the first customer who tries to register.
 */
@Slf4j
@Component
@Primary
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final String host;
    private final String username;
    private final String password;

    SmtpEmailSender(JavaMailSender mailSender,
                    EmailProperties properties,
                    @Value("${spring.mail.host:}") String host,
                    @Value("${spring.mail.username:}") String username,
                    @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.host = host;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    void verifyConfigured() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(host)) missing.add("spring.mail.host        (MAIL_HOST)");
        if (!StringUtils.hasText(username)) missing.add("spring.mail.username    (MAIL_USERNAME)");
        if (!StringUtils.hasText(password)) missing.add("spring.mail.password    (MAIL_PASSWORD)");
        if (!StringUtils.hasText(properties.getFrom())) missing.add("app.notification.email.from (MAIL_FROM)");

        if (!missing.isEmpty()) {
            throw new IllegalStateException("""
                    
                    Email is not configured, so the application will not start.
                    
                    Registration sends a verification link and sign-in sends a one-time passcode, so
                    without working mail nobody can create an account or log in.
                    
                    Missing:
                      %s
                    
                    For a Gmail account, set:
                      MAIL_HOST=smtp.gmail.com
                      MAIL_PORT=587
                      MAIL_USERNAME=you@gmail.com
                      MAIL_PASSWORD=<16-character App Password>
                      MAIL_FROM=you@gmail.com
                    
                    The App Password is generated at https://myaccount.google.com/apppasswords and
                    requires 2-Step Verification. It is not your Google account password.
                    """.formatted(String.join("\n  ", missing)));
        }
        log.info("Email dispatch ready: {} as {} <{}>", host, properties.getFromName(), properties.getFrom());
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(new InternetAddress(properties.getFrom(), properties.getFromName()));
            mailSender.send(message);
            log.info("[email] sent to={} subject={}", to, subject);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            // Dispatch is asynchronous by design (FR-7.4): a delivery failure is logged and
            // retried, never propagated into the committed business transaction.
            log.error("[email] delivery failed to={} subject={}", to, subject, e);
        }
    }
}
