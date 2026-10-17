package tn.esprit.trippocketbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.trippocketbackend.dto.AuthResponse;
import tn.esprit.trippocketbackend.dto.ForgotPasswordRequest;
import tn.esprit.trippocketbackend.dto.GoogleLoginRequest;
import tn.esprit.trippocketbackend.dto.LoginRequest;
import tn.esprit.trippocketbackend.dto.RefreshRequest;
import tn.esprit.trippocketbackend.dto.RegisterRequest;
import tn.esprit.trippocketbackend.dto.ResetPasswordRequest;
import tn.esprit.trippocketbackend.services.IAuthService;
import tn.esprit.trippocketbackend.services.PasswordResetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IAuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        sampleAuthResponse = new AuthResponse(
                "mockAccessToken",
                "mockRefreshToken",
                "TRAVELER",
                "user@example.com",
                "John",
                "Doe",
                1L
        );
    }

    @Test
    @DisplayName("POST /api/auth/login - should return 200 and set-cookie on success")
    void login_Success() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "validPass123!", true);
        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("mockAccessToken"))
                .andExpect(jsonPath("$.refreshToken").value("mockRefreshToken"))
                .andExpect(jsonPath("$.role").value("TRAVELER"))
                .andExpect(jsonPath("$.username").value("user@example.com"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 201 CREATED on success")
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("Alice", "Smith", "alice@example.com", "StrongPass123!");
        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("mockAccessToken"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - should refresh via request body")
    void refresh_ViaBody_Success() throws Exception {
        RefreshRequest request = new RefreshRequest("bodyRefreshToken");
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("mockAccessToken"));

        verify(authService).refresh(any(RefreshRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - should refresh via cookie if body is absent")
    void refresh_ViaCookie_Success() throws Exception {
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("trippocket_refresh_token", "cookieRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("mockAccessToken"));

        verify(authService).refresh(argThat(r -> "cookieRefreshToken".equals(r.refreshToken())));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - should return 401 UNAUTHORIZED when no token provided")
    void refresh_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).refresh(any());
    }

    @Test
    @DisplayName("POST /api/auth/logout - should revoke token and clear cookie")
    void logout_Success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("trippocket_refresh_token", "activeRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.message").value("Successfully logged out"));

        verify(authService).logout("activeRefreshToken");
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - should trigger password reset and return message")
    void forgotPassword_Success() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - should reset password and return 200")
    void resetPassword_Success() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("token123", "NewPassword1!", "NewPassword1!");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been successfully reset."));

        verify(passwordResetService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/google - should authenticate with Google credential")
    void loginWithGoogle_Success() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("googleCredential");
        when(authService.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("mockAccessToken"));

        verify(authService).loginWithGoogle(any(GoogleLoginRequest.class));
    }
}
