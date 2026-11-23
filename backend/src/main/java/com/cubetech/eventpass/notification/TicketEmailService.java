package com.cubetech.eventpass.notification;

import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.registration.QrCodeGenerator;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.registration.TicketMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends ticket emails over SMTP (e.g. a Mailtrap or Ethereal sandbox) after the registration
 * transaction commits. Delivery is asynchronous so a slow mail server never delays registration,
 * and the outcome is logged honestly: "sent", "failed" or "skipped (disabled)".
 */
@Service
public class TicketEmailService {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final RegistrationRepository registrations;
    private final TicketMapper ticketMapper;
    private final QrCodeGenerator qrCodeGenerator;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties properties;

    public TicketEmailService(RegistrationRepository registrations, TicketMapper ticketMapper,
                              QrCodeGenerator qrCodeGenerator, ObjectProvider<JavaMailSender> mailSender,
                              AppProperties properties) {
        this.registrations = registrations;
        this.ticketMapper = ticketMapper;
        this.qrCodeGenerator = qrCodeGenerator;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketEmailRequested(TicketEmailRequested request) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!properties.mail().enabled() || sender == null) {
            log.info("Email delivery disabled; skipped {} email for registration {}", request.kind(),
                    request.registrationId());
            return;
        }
        Registration registration = registrations.findById(request.registrationId()).orElse(null);
        if (registration == null) {
            return;
        }
        try {
            sender.send(buildMessage(sender, registration, request.kind()));
            log.info("Sent {} email for registration {}", request.kind(), request.registrationId());
        } catch (MailException | MessagingException ex) {
            log.warn("Failed to send {} email for registration {}: {}", request.kind(), request.registrationId(),
                    ex.getMessage());
        }
    }

    MimeMessage buildMessage(JavaMailSender sender, Registration registration, TicketEmailRequested.Kind kind)
            throws MessagingException {
        Event event = registration.getEvent();
        boolean hasTicket = kind != TicketEmailRequested.Kind.WAITLISTED;
        String ticketUrl = properties.publicBaseUrl().replaceAll("/+$", "") + "/tickets/" + registration.getAccessToken();

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.mail().from());
        helper.setTo(registration.getEmail());
        helper.setSubject(switch (kind) {
            case CONFIRMED -> "Your ticket for " + event.getTitle();
            case WAITLISTED -> "You're on the waitlist for " + event.getTitle();
            case PROMOTED -> "Good news! Your spot at " + event.getTitle() + " is confirmed";
        });
        helper.setText(plainText(registration, kind, ticketUrl), html(registration, kind, ticketUrl));
        if (hasTicket) {
            helper.addInline("ticket-qr", new ByteArrayResource(qrCodeGenerator.png(registration.getTicketCode())),
                    "image/png");
        }
        return message;
    }

    private String plainText(Registration registration, TicketEmailRequested.Kind kind, String ticketUrl) {
        Event event = registration.getEvent();
        StringBuilder text = new StringBuilder()
                .append("Hi ").append(registration.getFullName()).append(",\n\n")
                .append(intro(kind, event)).append("\n\n")
                .append("Event: ").append(event.getTitle()).append('\n')
                .append("Date: ").append(date(event)).append('\n')
                .append("Time: ").append(timeRange(event)).append(" (Cebu time)\n")
                .append("Venue: ").append(event.getVenue()).append('\n');
        if (kind == TicketEmailRequested.Kind.WAITLISTED) {
            text.append("Waitlist reference: ").append(registration.getTicketCode()).append('\n')
                    .append("Waitlist position: #").append(ticketMapper.waitlistPosition(registration)).append('\n');
        } else {
            text.append("Ticket code: ").append(registration.getTicketCode()).append('\n');
        }
        return text.append("\nView your ticket: ").append(ticketUrl).append("\n\n— EventPass").toString();
    }

    private String html(Registration registration, TicketEmailRequested.Kind kind, String ticketUrl) {
        Event event = registration.getEvent();
        boolean waitlisted = kind == TicketEmailRequested.Kind.WAITLISTED;
        String row = "<tr><td style=\"padding:4px 12px 4px 0;color:#64748b\">%s</td>"
                + "<td style=\"padding:4px 0;color:#0f172a;font-weight:600\">%s</td></tr>";
        StringBuilder html = new StringBuilder()
                .append("<div style=\"font-family:Segoe UI,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;")
                .append("color:#0f172a\"><h2 style=\"margin:0 0 4px\">EventPass</h2>")
                .append("<p>Hi ").append(esc(registration.getFullName())).append(",</p>")
                .append("<p>").append(esc(intro(kind, event))).append("</p>")
                .append("<table style=\"border-collapse:collapse;margin:16px 0\">")
                .append(row.formatted("Event", esc(event.getTitle())))
                .append(row.formatted("Date", esc(date(event))))
                .append(row.formatted("Time", esc(timeRange(event)) + " (Cebu time)"))
                .append(row.formatted("Venue", esc(event.getVenue())))
                .append(row.formatted(waitlisted ? "Waitlist reference" : "Ticket code",
                        "<span style=\"font-family:Consolas,monospace\">" + esc(registration.getTicketCode()) + "</span>"));
        if (waitlisted) {
            html.append(row.formatted("Position", "#" + ticketMapper.waitlistPosition(registration)));
        }
        html.append("</table>");
        if (!waitlisted) {
            html.append("<p>Show this QR code at the entrance:</p>")
                    .append("<img src=\"cid:ticket-qr\" alt=\"Ticket QR code\" width=\"220\" height=\"220\">");
        }
        return html.append("<p><a href=\"").append(esc(ticketUrl)).append("\" style=\"color:#4338ca\">View your ")
                .append(waitlisted ? "waitlist status" : "ticket").append(" online</a></p>")
                .append("<p style=\"color:#64748b;font-size:12px\">You received this email because you registered ")
                .append("for this event on EventPass.</p></div>")
                .toString();
    }

    private static String intro(TicketEmailRequested.Kind kind, Event event) {
        return switch (kind) {
            case CONFIRMED -> "You're registered for " + event.getTitle() + ". Your ticket is below.";
            case WAITLISTED -> event.getTitle() + " is currently full, so you've been added to the waitlist. "
                    + "Your registration is not confirmed yet; we'll email you if a spot opens.";
            case PROMOTED -> "A spot opened up and you've been moved off the waitlist. Your registration for "
                    + event.getTitle() + " is now confirmed.";
        };
    }

    private static String date(Event event) {
        return DATE.format(event.getStartsAt().atZone(TimeConfig.EVENT_ZONE));
    }

    private static String timeRange(Event event) {
        return TIME.format(event.getStartsAt().atZone(TimeConfig.EVENT_ZONE)) + " – "
                + TIME.format(event.getEndsAt().atZone(TimeConfig.EVENT_ZONE));
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
