package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.AuthResponse;
import tn.esprit.trippocketbackend.dto.GoogleLoginRequest;
import tn.esprit.trippocketbackend.dto.LoginRequest;
import tn.esprit.trippocketbackend.dto.RefreshRequest;
import tn.esprit.trippocketbackend.dto.RegisterRequest;
import tn.esprit.trippocketbackend.entities.AuthProvider;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.UserRepository;
import tn.esprit.trippocketbackend.security.GoogleTokenVerifierService;
import tn.esprit.trippocketbackend.security.JwtUtil;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("encodedPass")
                .role(Role.TRAVELER)
                .enabled(true)
                .build();

        userDetails = new org.springframework.security.core.userdetails.User(
                sampleUser.getEmail(),
                sampleUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_TRAVELER"))
        );
    }

    @Test
    @DisplayName("login - should authenticate, create tokens, and return AuthResponse on success")
    void login_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "secret123", true);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(refreshTokenService.createRefreshToken(sampleUser, true)).thenReturn("rawRefreshToken123");
        when(jwtUtil.generateToken(userDetails)).thenReturn("jwtAccessToken123");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwtAccessToken123", response.accessToken());
        assertEquals("rawRefreshToken123", response.refreshToken());
        assertEquals("TRAVELER", response.role());
        assertEquals("test@example.com", response.username());
        assertEquals("John", response.firstName());
        assertEquals(1L, response.id());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login - should throw UNAUTHORIZED when account is disabled")
    void login_DisabledAccount() {
        LoginRequest request = new LoginRequest("test@example.com", "secret123", false);
        doThrow(new DisabledException("Account disabled")).when(authenticationManager).authenticate(any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("account is inactive"));
    }

    @Test
    @DisplayName("login - should throw UNAUTHORIZED when credentials are bad")
    void login_BadCredentials() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongPass", false);
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationManager).authenticate(any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid credentials"));
    }

    @Test
    @DisplayName("register - should throw CONFLICT if email already in use")
    void register_ConflictEmail() {
        RegisterRequest request = new RegisterRequest("Existing", "User", "existing@example.com", "pass123");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.register(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register - should encode password, create traveler user, and return AuthResponse")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("Alice", "Smith", "alice@example.com", "plainPass");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass")).thenReturn("encodedPlain");

        User savedUser = User.builder()
                .id(2L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("encodedPlain")
                .role(Role.TRAVELER)
                .enabled(true)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(refreshTokenService.createRefreshToken(savedUser, false)).thenReturn("newRefreshToken");
        when(jwtUtil.generateToken(userDetails)).thenReturn("newJwtToken");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("newJwtToken", response.accessToken());
        assertEquals("newRefreshToken", response.refreshToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("refresh - should throw UNAUTHORIZED if refresh token is missing or blank")
    void refresh_MissingToken() {
        RefreshRequest emptyRequest = new RefreshRequest("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.refresh(emptyRequest));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("refresh - should rotate refresh token and return new AuthResponse")
    void refresh_Success() {
        RefreshRequest request = new RefreshRequest("validRefreshToken");
        RefreshTokenService.RotationResult rotationResult =
                new RefreshTokenService.RotationResult(sampleUser, "brandNewRefreshToken");

        when(refreshTokenService.rotateRefreshToken("validRefreshToken")).thenReturn(rotationResult);
        when(userDetailsService.loadUserByUsername(sampleUser.getEmail())).thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails)).thenReturn("rotatedJwt");

        AuthResponse response = authService.refresh(request);

        assertNotNull(response);
        assertEquals("rotatedJwt", response.accessToken());
        assertEquals("brandNewRefreshToken", response.refreshToken());
    }

    @Test
    @DisplayName("loginWithGoogle - should login existing active user")
    void loginWithGoogle_ExistingUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("googleCredentialToken");
        GoogleTokenVerifierService.GoogleUserInfo googleUser = new GoogleTokenVerifierService.GoogleUserInfo(
                "google-sub-123", "test@example.com", true, "John", "Doe", "http://photo.url"
        );

        when(googleTokenVerifierService.verifyToken("googleCredentialToken")).thenReturn(googleUser);
        when(userRepository.findByProviderId("google-sub-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userDetailsService.loadUserByUsername(sampleUser.getEmail())).thenReturn(userDetails);
        when(refreshTokenService.createRefreshToken(sampleUser, true)).thenReturn("googleRefreshToken");
        when(jwtUtil.generateToken(userDetails)).thenReturn("googleJwt");

        AuthResponse response = authService.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("googleJwt", response.accessToken());
        assertEquals("googleRefreshToken", response.refreshToken());
    }

    @Test
    @DisplayName("loginWithGoogle - should register new user if not found")
    void loginWithGoogle_NewUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("newGoogleCredential");
        GoogleTokenVerifierService.GoogleUserInfo googleUser = new GoogleTokenVerifierService.GoogleUserInfo(
                "new-google-sub", "newgoogle@example.com", true, "New", "Explorer", "http://photo.url"
        );

        when(googleTokenVerifierService.verifyToken("newGoogleCredential")).thenReturn(googleUser);
        when(userRepository.findByProviderId("new-google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newgoogle@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("randomEncodedPass");

        User createdGoogleUser = User.builder()
                .id(5L)
                .firstName("New")
                .lastName("Explorer")
                .email("newgoogle@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("new-google-sub")
                .role(Role.TRAVELER)
                .enabled(true)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(createdGoogleUser);
        when(userDetailsService.loadUserByUsername("newgoogle@example.com")).thenReturn(userDetails);
        when(refreshTokenService.createRefreshToken(createdGoogleUser, true)).thenReturn("newGoogleRefresh");
        when(jwtUtil.generateToken(userDetails)).thenReturn("newGoogleJwt");

        AuthResponse response = authService.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("newGoogleJwt", response.accessToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("loginWithGoogle - should throw UNAUTHORIZED if existing user is disabled")
    void loginWithGoogle_DisabledUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("credential");
        GoogleTokenVerifierService.GoogleUserInfo googleUser = new GoogleTokenVerifierService.GoogleUserInfo(
                "sub-disabled", "disabled@example.com", true, "Disabled", "User", null
        );

        User disabledUser = User.builder()
                .id(10L)
                .email("disabled@example.com")
                .enabled(false)
                .build();

        when(googleTokenVerifierService.verifyToken("credential")).thenReturn(googleUser);
        when(userRepository.findByProviderId("sub-disabled")).thenReturn(Optional.of(disabledUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.loginWithGoogle(request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("account is inactive"));
    }

    @Test
    @DisplayName("logout - should revoke refresh token if provided")
    void logout_Success() {
        authService.logout("rawTokenToRevoke");
        verify(refreshTokenService).revokeToken("rawTokenToRevoke");
    }

    @Test
    @DisplayName("logoutAll - should revoke all user tokens if userId provided")
    void logoutAll_Success() {
        authService.logoutAll(1L);
        verify(refreshTokenService).revokeAllUserTokens(1L);
    }
}
