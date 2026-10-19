package tn.esprit.trippocketbackend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tn.esprit.trippocketbackend.security.JwtFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtFilter jwtFilter;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtFilter, userDetailsService);
    }

    @Test
    @DisplayName("passwordEncoder - should return BCryptPasswordEncoder that correctly hashes and matches passwords")
    void passwordEncoder_Functionality() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertNotNull(encoder);

        String raw = "mySecurePassword123";
        String encoded = encoder.encode(raw);

        assertNotNull(encoded);
        assertNotEquals(raw, encoded);
        assertTrue(encoder.matches(raw, encoded));
        assertFalse(encoder.matches("wrongPassword", encoded));
    }

    @Test
    @DisplayName("authenticationProvider - should create DaoAuthenticationProvider with userDetailsService")
    void authenticationProvider_Creation() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        AuthenticationProvider provider = securityConfig.authenticationProvider(encoder);

        assertNotNull(provider);
        assertInstanceOf(DaoAuthenticationProvider.class, provider);
    }

    @Test
    @DisplayName("authenticationManager - should delegate to AuthenticationConfiguration")
    void authenticationManager_Delegation() throws Exception {
        AuthenticationManager mockAuthManager = mock(AuthenticationManager.class);
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(mockAuthManager);

        AuthenticationManager manager = securityConfig.authenticationManager(authenticationConfiguration);

        assertNotNull(manager);
        assertEquals(mockAuthManager, manager);
        verify(authenticationConfiguration).getAuthenticationManager();
    }

    @Test
    @DisplayName("corsConfigurationSource - should configure allowed origins, methods, and credentials")
    void corsConfigurationSource_Configuration() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        assertTrue(config.getAllowedOrigins().contains("http://localhost:4200"));
        assertTrue(config.getAllowedMethods().contains("GET"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("PUT"));
        assertTrue(config.getAllowedMethods().contains("DELETE"));
        assertTrue(config.getAllowedHeaders().contains("Authorization"));
        assertTrue(config.getAllowedHeaders().contains("Content-Type"));
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
    }
}
