package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.AuthResponse;
import tn.esprit.trippocketbackend.dto.GoogleLoginRequest;
import tn.esprit.trippocketbackend.dto.LoginRequest;
import tn.esprit.trippocketbackend.dto.RefreshRequest;
import tn.esprit.trippocketbackend.dto.RegisterRequest;

public interface IAuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refresh(RefreshRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);
}
