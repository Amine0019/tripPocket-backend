package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.RefreshToken;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.refresh-expiration-short-ms:86400000}")
    private long shortExpirationMs; // 24 hours

    @Value("${app.jwt.refresh-expiration-long-ms:2592000000}")
    private long longExpirationMs; // 30 days

    public record RotationResult(User user, String newRawToken) {}

    /**
     * Creates a new cryptographically secure RefreshToken entity.
     * Returns the unhashed raw token to be sent to the client.
     */
    @Transactional
    public String createRefreshToken(User user, boolean rememberMe) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        long durationMs = rememberMe ? longExpirationMs : shortExpirationMs;
        Instant expiresAt = Instant.now().plus(durationMs, ChronoUnit.MILLIS);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(expiresAt)
                .rememberMe(rememberMe)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user id={}, rememberMe={}, expiresAt={}",
                user.getId(), rememberMe, expiresAt);

        return rawToken;
    }

    /**
     * Validates an incoming refresh token, detects reuse attacks, revokes it,
     * and rotates to a brand-new token.
     */
    @Transactional
    public RotationResult rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing.");
        }

        String tokenHash = hashToken(rawToken.trim());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token."));

        User user = token.getUser();

        // 1. Detect Reuse Attack (if already revoked, compromise detected!)
        if (token.isRevoked()) {
            log.warn("SECURITY ALERT: Refresh token reuse detected for user id={}! Revoking all sessions.", user.getId());
            refreshTokenRepository.revokeAllForUser(user.getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token was already used or revoked.");
        }

        // 2. Check expiration
        if (token.isExpired()) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired. Please sign in again.");
        }

        // 3. Check user enabled
        if (user.getEnabled() != null && !user.getEnabled()) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account is inactive. Please contact the administrator.");
        }

        // 4. Revoke the old token (Single-Use Token Rotation)
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // 5. Issue new token with the same rememberMe policy
        String newRawToken = createRefreshToken(user, token.isRememberMe());

        log.info("Successfully rotated refresh token for user id={}", user.getId());
        return new RotationResult(user, newRawToken);
    }

    /**
     * Revokes a specific refresh token (used on logout).
     */
    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user id={}", token.getUser().getId());
        });
    }

    /**
     * Revokes all active refresh tokens for a user (used on logout-all or password reset).
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        if (userId != null) {
            int revokedCount = refreshTokenRepository.revokeAllForUser(userId);
            log.info("Revoked all ({}) active refresh tokens for user id={}", revokedCount, userId);
        }
    }

    // ── Cryptographic Helpers ────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
