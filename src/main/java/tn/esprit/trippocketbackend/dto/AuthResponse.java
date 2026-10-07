package tn.esprit.trippocketbackend.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role,
        String username,
        String firstName,
        String lastName) {
}
