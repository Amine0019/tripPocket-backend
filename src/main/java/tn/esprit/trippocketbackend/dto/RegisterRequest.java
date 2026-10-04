package tn.esprit.trippocketbackend.dto;

public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String phone) {
}
