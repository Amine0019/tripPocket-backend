package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.AuthResponse;
import tn.esprit.trippocketbackend.dto.ForgotPasswordRequest;
import tn.esprit.trippocketbackend.dto.GoogleLoginRequest;
import tn.esprit.trippocketbackend.dto.LoginRequest;
import tn.esprit.trippocketbackend.dto.RefreshRequest;
import tn.esprit.trippocketbackend.dto.RegisterRequest;
import tn.esprit.trippocketbackend.dto.ResetPasswordRequest;
import tn.esprit.trippocketbackend.services.IAuthService;
import tn.esprit.trippocketbackend.services.PasswordResetService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final IAuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        long maxAge = request.rememberMe() ? 30L * 24 * 3600 : 24L * 3600;
        ResponseCookie cookie = createRefreshTokenCookie(response.refreshToken(), maxAge);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.refreshToken(), 24L * 3600);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh an access token")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = "trippocket_refresh_token", required = false) String cookieToken) {
        String token = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : cookieToken;

        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        AuthResponse response = authService.refresh(new RefreshRequest(token));
        ResponseCookie cookie = createRefreshTokenCookie(response.refreshToken(), 30L * 24 * 3600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user and revoke refresh token")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = "trippocket_refresh_token", required = false) String cookieToken) {
        String token = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : cookieToken;

        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }

        ResponseCookie deleteCookie = createRefreshTokenCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "Successfully logged out"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset link")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using reset token")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset."));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google credential")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        ResponseCookie cookie = createRefreshTokenCookie(response.refreshToken(), 30L * 24 * 3600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from("trippocket_refresh_token", refreshToken != null ? refreshToken : "")
                .httpOnly(true)
                .secure(false) // HTTP compatible for development
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }
}
