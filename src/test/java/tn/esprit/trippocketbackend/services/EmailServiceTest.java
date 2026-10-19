package tn.esprit.trippocketbackend.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@trippocket.com");
    }

    @Test
    @DisplayName("sendPasswordResetEmail - should create and send mime message")
    void sendPasswordResetEmail_Success() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetEmail(
                "traveler@example.com",
                "Alex",
                "http://localhost:4200/reset-password?token=xyz123",
                30
        );

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendPasswordResetEmail - should handle mail exception gracefully without throwing")
    void sendPasswordResetEmail_ExceptionHandledGracefully() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP connection refused"));

        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail(
                "traveler@example.com",
                "Alex",
                "http://localhost:4200/reset-password?token=xyz123",
                30
        ));
    }
}
