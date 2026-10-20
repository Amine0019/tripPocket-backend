package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.ResetPasswordRequest;
import tn.esprit.trippocketbackend.entities.PasswordResetToken;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.PasswordResetTokenRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "expirationMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "frontendResetUrl", "http://localhost:4200/reset-password");

        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("oldHashedPassword")
                .enabled(true)
                .role(Role.TRAVELER)
                .build();
    }

    @Test
    @DisplayName("requestPasswordReset - should silently return if user is not found")
    void requestPasswordReset_UserNotFound_ShouldReturnSilently() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("unknown@example.com"));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("requestPasswordReset - should create token, save hash, and send email when user exists")
    void requestPasswordReset_UserExists_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));

        passwordResetService.requestPasswordReset("test@example.com");

        verify(tokenRepository).invalidateAllForUser(1L);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertNotNull(savedToken.getTokenHash());
        assertFalse(savedToken.isUsed());
        assertEquals(sampleUser, savedToken.getUser());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));

        verify(emailService).sendPasswordResetEmail(
                eq("test@example.com"),
                eq("John"),
                contains("http://localhost:4200/reset-password?token="),
                eq(30)
        );
    }

    @Test
    @DisplayName("resetPassword - should throw BAD_REQUEST when passwords do not match")
    void resetPassword_PasswordsMismatch() {
        ResetPasswordRequest request = new ResetPasswordRequest("token123", "NewPassword1!", "MismatchPassword2!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Passwords do not match"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword - should throw BAD_REQUEST when token is not found")
    void resetPassword_TokenNotFound() {
        ResetPasswordRequest request = new ResetPasswordRequest("invalidToken", "NewPassword1!", "NewPassword1!");

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid or expired reset link"));
    }

    @Test
    @DisplayName("resetPassword - should throw BAD_REQUEST when token is already used")
    void resetPassword_TokenAlreadyUsed() {
        PasswordResetToken token = PasswordResetToken.builder()
                .used(true)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .user(sampleUser)
                .build();

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest("validToken", "NewPassword1!", "NewPassword1!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already been used"));
    }

    @Test
    @DisplayName("resetPassword - should throw BAD_REQUEST when token is expired")
    void resetPassword_TokenExpired() {
        PasswordResetToken token = PasswordResetToken.builder()
                .used(false)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .user(sampleUser)
                .build();

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResetPasswordRequest request = new ResetPasswordRequest("validToken", "NewPassword1!", "NewPassword1!");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
    }

    @Test
    @DisplayName("resetPassword - should update password, invalidate tokens, and revoke sessions on success")
    void resetPassword_Success() throws Exception {
        String rawToken = "mySecretRawToken";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(expectedHash)
                .used(false)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .user(sampleUser)
                .build();

        when(tokenRepository.findByTokenHash(expectedHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewStrongPassword123!")).thenReturn("newEncodedHash");

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewStrongPassword123!", "NewStrongPassword123!");

        passwordResetService.resetPassword(request);

        assertEquals("newEncodedHash", sampleUser.getPassword());
        verify(userRepository).save(sampleUser);
        assertTrue(token.isUsed());
        verify(tokenRepository).save(token);
        verify(tokenRepository).invalidateAllForUser(sampleUser.getId());
        verify(refreshTokenService).revokeAllUserTokens(sampleUser.getId());
    }

    @Test
    @DisplayName("cleanupExpiredTokens - should invoke deleteExpiredOrUsed on repository")
    void cleanupExpiredTokens_Success() {
        when(tokenRepository.deleteExpiredOrUsed(any(Instant.class))).thenReturn(5);

        int count = passwordResetService.cleanupExpiredTokens();

        assertEquals(5, count);
        verify(tokenRepository).deleteExpiredOrUsed(any(Instant.class));
    }
}
