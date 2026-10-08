package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.UserSearchResponse;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already in use.");
        }

        if (user.getEnabled() == null) {
            user.setEnabled(true);
        }
        if (user.getRole() == null) {
            user.setRole(Role.TRAVELER);
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return userRepository.save(user);
    }

    @Override
    public PageResponse<User> getAllUsers(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").ascending());
        Page<User> userPage = userRepository.findAll(pageable);
        return PageResponse.from(userPage);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Override
    public User updateUser(Long id, User user) {
        User existingUser = getUserById(id);

        if (user.getEmail() != null
                && !Objects.equals(existingUser.getEmail(), user.getEmail())
                && userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already in use.");
        }

        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        existingUser.setEmail(user.getEmail());

        if (user.getRole() != null) {
            existingUser.setRole(user.getRole());
        }
        if (user.getEnabled() != null) {
            existingUser.setEnabled(user.getEnabled());
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return userRepository.save(existingUser);
    }

    @Override
    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }

    @Override
    public List<UserSearchResponse> searchUsers(String email) {
        String em = email != null ? email.trim() : "";
        return userRepository.searchByEmail(em)
                .stream()
                .map(u -> UserSearchResponse.builder()
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .enabled(u.getEnabled())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserSearchResponse> getUsersByRole(Role role) {
        return userRepository.findByRole(role)
                .stream()
                .map(u -> UserSearchResponse.builder()
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .enabled(u.getEnabled())
                        .build())
                .collect(Collectors.toList());
    }
}
