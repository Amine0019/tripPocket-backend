package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (org.springframework.security.authentication.DisabledException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account is inactive. Please contact the administrator.");
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return createAuthResponse(userDetails, user);
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already in use.");
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(Role.TRAVELER)
                .build();

        User savedUser = userRepository.save(user);
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        return createAuthResponse(userDetails, savedUser);
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {
        try {
            if (!jwtUtil.isRefreshToken(request.refreshToken())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }

            String username = jwtUtil.extractUsername(request.refreshToken());
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtUtil.validateToken(request.refreshToken(), userDetails)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }

            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
            return createAuthResponse(userDetails, user);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser =
                googleTokenVerifierService.verifyToken(request.credential());

        // 1. Check if user already exists by Google providerId or email
        User user = userRepository.findByProviderId(googleUser.subject())
                .orElseGet(() -> userRepository.findByEmail(googleUser.email()).orElse(null));

        if (user != null) {
            // Refuse login if user is inactive
            if (user.getEnabled() != null && !user.getEnabled()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account is inactive. Please contact the administrator.");
            }

            // Link Google account to existing user without touching password
            boolean needsUpdate = false;
            if (user.getProviderId() == null || user.getProviderId().isBlank()) {
                user.setProviderId(googleUser.subject());
                if (user.getProvider() == null || user.getProvider() == AuthProvider.LOCAL) {
                    user.setProvider(AuthProvider.GOOGLE);
                }
                needsUpdate = true;
            }

            // Fill names if they were missing
            if ((user.getFirstName() == null || user.getFirstName().isBlank()) && !googleUser.firstName().isBlank()) {
                user.setFirstName(googleUser.firstName());
                needsUpdate = true;
            }
            if ((user.getLastName() == null || user.getLastName().isBlank()) && !googleUser.lastName().isBlank()) {
                user.setLastName(googleUser.lastName());
                needsUpdate = true;
            }

            if (needsUpdate) {
                user = userRepository.save(user);
            }
        } else {
            // Register new Traveler user from Google profile
            user = User.builder()
                    .firstName(!googleUser.firstName().isBlank() ? googleUser.firstName() : "Traveler")
                    .lastName(!googleUser.lastName().isBlank() ? googleUser.lastName() : "")
                    .email(googleUser.email())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .enabled(true)
                    .role(Role.TRAVELER)
                    .provider(AuthProvider.GOOGLE)
                    .providerId(googleUser.subject())
                    .build();

            user = userRepository.save(user);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return createAuthResponse(userDetails, user);
    }

    private AuthResponse createAuthResponse(UserDetails userDetails, User user) {
        return new AuthResponse(
                jwtUtil.generateToken(userDetails),
                jwtUtil.generateRefreshToken(userDetails),
                user.getRole().name(),
                userDetails.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getId());
    }
}
