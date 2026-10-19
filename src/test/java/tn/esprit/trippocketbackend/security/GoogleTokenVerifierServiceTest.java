package tn.esprit.trippocketbackend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GoogleTokenVerifierServiceTest {

    private GoogleTokenVerifierService googleTokenVerifierService;

    @BeforeEach
    void setUp() {
        googleTokenVerifierService = new GoogleTokenVerifierService();
        ReflectionTestUtils.setField(googleTokenVerifierService, "googleClientId", "test-client-id.apps.googleusercontent.com");
    }

    @Test
    @DisplayName("verifyToken - should throw BAD_REQUEST when idTokenString is null or blank")
    void verifyToken_NullOrBlank_ThrowsBadRequest() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                googleTokenVerifierService.verifyToken(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                googleTokenVerifierService.verifyToken("   "));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    @DisplayName("verifyToken - should throw UNAUTHORIZED when token is malformed or invalid")
    void verifyToken_MalformedToken_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                googleTokenVerifierService.verifyToken("invalid.token.payload"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
