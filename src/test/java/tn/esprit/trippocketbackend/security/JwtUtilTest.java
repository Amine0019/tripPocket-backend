package tn.esprit.trippocketbackend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET_32_BYTES = "12345678901234567890123456789012"; // 32 chars = 256 bits
    private static final long ACCESS_EXPIRATION_MS = 3600000L; // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 86400000L; // 24 hours

    private JwtUtil jwtUtil;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET_32_BYTES, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);

        userDetails = new User(
                "john.doe@example.com",
                "password123",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TRAVELER"))
        );
    }

    @Test
    @DisplayName("generateToken - should produce valid access token with subject and role")
    void generateToken_ProducesValidAccessToken() {
        String token = jwtUtil.generateToken(userDetails);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("john.doe@example.com", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.validateToken(token, userDetails));
        assertFalse(jwtUtil.isRefreshToken(token));
    }

    @Test
    @DisplayName("generateRefreshToken - should produce token with type=refresh")
    void generateRefreshToken_ProducesValidRefreshToken() {
        String token = jwtUtil.generateRefreshToken(userDetails);

        assertNotNull(token);
        assertEquals("john.doe@example.com", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.isRefreshToken(token));
    }

    @Test
    @DisplayName("validateToken - should return false for mismatched username")
    void validateToken_MismatchedUsername_ReturnsFalse() {
        String token = jwtUtil.generateToken(userDetails);

        UserDetails otherUser = new User(
                "other.user@example.com",
                "password123",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TRAVELER"))
        );

        assertFalse(jwtUtil.validateToken(token, otherUser));
    }

    @Test
    @DisplayName("validateToken - should return false for malformed or expired token")
    void validateToken_MalformedToken_ReturnsFalse() {
        assertFalse(jwtUtil.validateToken("not.a.valid.jwt.token", userDetails));
        assertFalse(jwtUtil.isRefreshToken("not.a.valid.jwt.token"));
    }

    @Test
    @DisplayName("validateToken - should return false for expired token")
    void validateToken_ExpiredToken_ReturnsFalse() throws InterruptedException {
        // JwtUtil with 1ms expiration
        JwtUtil shortJwtUtil = new JwtUtil(SECRET_32_BYTES, 1L, 1L);
        String token = shortJwtUtil.generateToken(userDetails);

        Thread.sleep(15); // Wait for expiration

        assertFalse(shortJwtUtil.validateToken(token, userDetails));
    }
}
