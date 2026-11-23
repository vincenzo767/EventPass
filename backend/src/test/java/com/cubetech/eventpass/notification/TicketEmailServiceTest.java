package com.cubetech.eventpass.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubetech.eventpass.config.AppProperties;
import com.cubetech.eventpass.config.TimeConfig;
import com.cubetech.eventpass.event.Event;
import com.cubetech.eventpass.event.EventStatus;
import com.cubetech.eventpass.registration.QrCodeGenerator;
import com.cubetech.eventpass.registration.Registration;
import com.cubetech.eventpass.registration.RegistrationRepository;
import com.cubetech.eventpass.registration.RegistrationStatus;
import com.cubetech.eventpass.registration.TicketMapper;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class TicketEmailServiceTest {

    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private TicketEmailService service(boolean enabled) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        AppProperties props = new AppProperties("https://eventpass.example.com/",
                new AppProperties.Mail(enabled, "EventPass <no-reply@example.com>"), null, null);
        return new TicketEmailService(registrations, new TicketMapper(registrations), new QrCodeGenerator(), provider,
                props);
    }

    private Registration registration(RegistrationStatus status) {
        Instant start = ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, TimeConfig.EVENT_ZONE).toInstant();
        Event event = new Event("Cebu Dev Meetup 2026", "desc", "Ayala Center Cebu, Function Room 2", start,
                start.plusSeconds(12 * 3600), 30, EventStatus.PUBLISHED, "CDM", 1L, start);
        return new Registration(event, "Juan <b>dela Cruz</b>", "juan@example.com", "09171234567",
                "CDM-7K3P9-Q2XHM", "token123", status, start);
    }

    @Test
    void confirmationEmailContainsTicketDetailsLinkAndInlineQr() throws Exception {
        MimeMessage message = service(true).buildMessage(new JavaMailSenderImpl(),
                registration(RegistrationStatus.CONFIRMED), TicketEmailRequested.Kind.CONFIRMED);

        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("juan@example.com");
        assertThat(message.getSubject()).isEqualTo("Your ticket for Cebu Dev Meetup 2026");
        String raw = rawContent(message);
        assertThat(raw).contains("CDM-7K3P9-Q2XHM")
                .contains("https://eventpass.example.com/tickets/token123")
                .contains("9:00 AM")
                .contains("cid:ticket-qr")
                .contains("Content-ID: <ticket-qr>")
                .contains("Juan &lt;b&gt;dela Cruz&lt;/b&gt;"); // user input is HTML-escaped
        assertThat(hasImagePart(message)).isTrue();
    }

    @Test
    void promotionEmailHasConfirmedSubject() throws Exception {
        MimeMessage message = service(true).buildMessage(new JavaMailSenderImpl(),
                registration(RegistrationStatus.CONFIRMED), TicketEmailRequested.Kind.PROMOTED);
        assertThat(message.getSubject()).startsWith("Good news! Your spot at Cebu Dev Meetup 2026 is confirmed");
        assertThat(rawContent(message)).contains("cid:ticket-qr");
    }

    @Test
    void disabledMailIsSkippedWithoutSending() {
        service(false).onTicketEmailRequested(new TicketEmailRequested(1L, TicketEmailRequested.Kind.CONFIRMED));
        verify(sender, never()).send(any(MimeMessage.class));
        verify(registrations, never()).findById(any());
    }

    @Test
    void enabledMailIsSent() {
        Registration registration = registration(RegistrationStatus.CONFIRMED);
        when(registrations.findById(1L)).thenReturn(Optional.of(registration));
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        service(true).onTicketEmailRequested(new TicketEmailRequested(1L, TicketEmailRequested.Kind.CONFIRMED));

        verify(sender).send(any(MimeMessage.class));
    }

    private static String rawContent(MimeMessage message) throws Exception {
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasImagePart(Part part) throws Exception {
        if (part.isMimeType("image/png")) {
            return true;
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                if (hasImagePart(multipart.getBodyPart(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
