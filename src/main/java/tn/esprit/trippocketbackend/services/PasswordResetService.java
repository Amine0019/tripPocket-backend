package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.ResetPasswordRequest;
import tn.esprit.trippocketbackend.entities.PasswordResetToken;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.PasswordResetTokenRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.password-reset.expiration-minutes:30}")
    private int expirationMinutes;

    @Value("${app.password-reset.frontend-url:http://localhost:4200/reset-password}")
    private String frontendResetUrl;

    /**
     * Handles forgot-password request.
     * Always returns successfully to prevent email enumeration.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Don't reveal that the user doesn't exist
            log.debug("Password reset requested for non-existent email");
            return;
        }

        User user = userOpt.get();

        // Invalidate any previous active tokens for this user
        tokenRepository.invalidateAllForUser(user.getId());

        // Generate cryptographically secure token
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        // Store only the hashed token
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES))
                .used(false)
                .createdAt(Instant.now())
                .build();

        tokenRepository.save(resetToken);

        // Build reset link with the raw (unhashed) token
        String resetLink = frontendResetUrl + "?token=" + rawToken;

        // Send email asynchronously
        emailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFirstName(),
                resetLink,
                expirationMinutes
        );

        log.info("Password reset token generated for user id={}", user.getId());
    }

    /**
     * Validates the token and resets the password.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Validate password confirmation
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }

        // Hash the incoming token to find in DB
        String tokenHash = hashToken(request.token());
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link."));

        // Check if token was already used
        if (resetToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has already been used.");
        }

        // Check expiration
        if (resetToken.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has expired. Please request a new one.");
        }

        // Update the user's password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Also invalidate any remaining active tokens for this user
        tokenRepository.invalidateAllForUser(user.getId());

        log.info("Password reset completed for user id={}", user.getId());
    }

    /**
     * Cleanup expired and used tokens. Can be called by a scheduled task.
     */
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredOrUsed(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/used password reset tokens", deleted);
        }
        return deleted;
    }

    // ── Private helpers ─────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
