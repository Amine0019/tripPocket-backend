package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.RefreshToken;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.RefreshTokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "shortExpirationMs", 86400000L); // 24h
        ReflectionTestUtils.setField(refreshTokenService, "longExpirationMs", 2592000000L); // 30d

        activeUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .role(Role.TRAVELER)
                .build();
    }

    @Test
    void createRefreshToken_shouldGenerateTokenAndPersistHashed() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(activeUser, true);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertEquals(activeUser, saved.getUser());
        assertTrue(saved.isRememberMe());
        assertFalse(saved.isRevoked());
        assertNotEquals(rawToken, saved.getTokenHash()); // Hash is not raw token
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
    }

    @Test
    void rotateRefreshToken_shouldRotateAndRevokeOldToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Create an initial token
        String rawToken = refreshTokenService.createRefreshToken(activeUser, false);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken existingToken = captor.getValue();

        when(refreshTokenRepository.findByTokenHash(existingToken.getTokenHash())).thenReturn(Optional.of(existingToken));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertNotNull(result);
        assertEquals(activeUser, result.user());
        assertNotNull(result.newRawToken());
        assertNotEquals(rawToken, result.newRawToken());
        assertTrue(existingToken.isRevoked()); // Old token revoked
    }

    @Test
    void rotateRefreshToken_onReusedRevokedToken_shouldDetectAttackAndRevokeAllSessions() {
        RefreshToken alreadyRevokedToken = RefreshToken.builder()
                .id(10L)
                .user(activeUser)
                .tokenHash("somehash")
                .revoked(true) // ALREADY REVOKED!
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevokedToken));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                refreshTokenService.rotateRefreshToken("dummyRawToken"));

        assertEquals(401, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("already used or revoked"));

        // Verify that ALL sessions for this user were revoked!
        verify(refreshTokenRepository).revokeAllForUser(activeUser.getId());
    }

    @Test
    void rotateRefreshToken_whenUserIsDisabled_shouldRefuseAndRevokeToken() {
        User disabledUser = User.builder()
                .id(2L)
                .email("disabled@example.com")
                .enabled(false) // Disabled
                .build();

        RefreshToken token = RefreshToken.builder()
                .id(20L)
                .user(disabledUser)
                .tokenHash("somehash")
                .revoked(false)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                refreshTokenService.rotateRefreshToken("dummyRawToken"));

        assertEquals(401, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("account is inactive"));
        assertTrue(token.isRevoked());
    }
}
