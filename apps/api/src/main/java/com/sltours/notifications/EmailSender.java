package com.sltours.notifications;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Transactional email (account verification and password reset). With {@code sltours.mail.provider=smtp} messages go to
 * the configured SMTP server; with {@code none} nothing is sent and callers tell the person to contact SL Tours instead.
 * Message bodies are never logged: they contain single-use sign-in links.
 */
@Component
public class EmailSender {
    public enum Delivery { SENT, NOT_CONFIGURED, FAILED }

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private final boolean enabled;
    private final JavaMailSenderImpl smtp;
    private final String from;

    public EmailSender(@Value("${sltours.mail.provider:none}") String provider, @Value("${sltours.mail.from:}") String from,
            @Value("${sltours.mail.smtp.host:}") String host, @Value("${sltours.mail.smtp.port:587}") int port,
            @Value("${sltours.mail.smtp.username:}") String username, @Value("${sltours.mail.smtp.password:}") String password,
            @Value("${sltours.mail.smtp.starttls:true}") boolean starttls, @Value("${sltours.mail.smtp.auth:true}") boolean auth) {
        this.enabled = "smtp".equalsIgnoreCase(provider);
        if (enabled && (!StringUtils.hasText(host) || !StringUtils.hasText(from)))
            throw new IllegalStateException("sltours.mail.provider=smtp needs sltours.mail.smtp.host and sltours.mail.from");
        this.from = from;
        this.smtp = new JavaMailSenderImpl();
        smtp.setHost(host); smtp.setPort(port); smtp.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (StringUtils.hasText(username)) { smtp.setUsername(username); smtp.setPassword(password); }
        Properties props = smtp.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(auth && StringUtils.hasText(username)));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (!enabled) log.warn("Email delivery is not configured (sltours.mail.provider=none): verification and password-reset emails cannot be sent");
    }

    public boolean isEnabled() { return enabled; }

    public Delivery send(String to, String subject, String text) {
        if (!enabled) return Delivery.NOT_CONFIGURED;
        try {
            var message = smtp.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            smtp.send(message);
            return Delivery.SENT;
        } catch (Exception e) {
            log.warn("Email '{}' could not be sent: {}", subject, e.getMessage());
            return Delivery.FAILED;
        }
    }
}
