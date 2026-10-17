package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.UserSearchResponse;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("traveler@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("rawPassword123")
                .enabled(true)
                .role(Role.TRAVELER)
                .build();
    }

    @Test
    @DisplayName("createUser - should encode password, set defaults, and persist user")
    void createUser_Success() {
        User newUser = User.builder()
                .email("new@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .password("plain123")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain123")).thenReturn("encoded123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(newUser);

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.getEnabled());
        assertEquals(Role.TRAVELER, result.getRole());
        assertEquals("encoded123", result.getPassword());
        verify(userRepository).save(newUser);
    }

    @Test
    @DisplayName("createUser - should throw CONFLICT when email already exists")
    void createUser_ConflictEmail() {
        when(userRepository.existsByEmail(sampleUser.getEmail())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userService.createUser(sampleUser));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllUsers(page, size) - should return paginated response")
    void getAllUsers_Paginated() {
        Page<User> page = new PageImpl<>(List.of(sampleUser));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<User> result = userService.getAllUsers(0, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(sampleUser.getEmail(), result.getContent().get(0).getEmail());
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllUsers() - should return list of all users")
    void getAllUsers_List() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<User> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("John", result.get(0).getFirstName());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getUserById - should return user when found")
    void getUserById_Found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        User result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getUserById - should throw NOT_FOUND when user does not exist")
    void getUserById_NotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userService.getUserById(99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateUser - should update fields and encode password if provided")
    void updateUser_Success() {
        User existing = User.builder()
                .id(1L)
                .email("old@example.com")
                .firstName("OldFirst")
                .lastName("OldLast")
                .role(Role.TRAVELER)
                .enabled(true)
                .build();

        User updateData = User.builder()
                .email("new@example.com")
                .firstName("NewFirst")
                .lastName("NewLast")
                .password("newSecret")
                .role(Role.ADMIN)
                .enabled(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newSecret")).thenReturn("encodedSecret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = userService.updateUser(1L, updateData);

        assertEquals("NewFirst", updated.getFirstName());
        assertEquals("NewLast", updated.getLastName());
        assertEquals("new@example.com", updated.getEmail());
        assertEquals("encodedSecret", updated.getPassword());
        assertEquals(Role.ADMIN, updated.getRole());
        assertEquals(Boolean.FALSE, updated.getEnabled());
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("updateUser - should throw CONFLICT if updated email belongs to someone else")
    void updateUser_ConflictEmail() {
        User existing = User.builder()
                .id(1L)
                .email("user1@example.com")
                .build();

        User updateData = User.builder()
                .email("user2@example.com")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("user2@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userService.updateUser(1L, updateData));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteUser - should delete user when found")
    void deleteUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        userService.deleteUser(1L);

        verify(userRepository).delete(sampleUser);
    }

    @Test
    @DisplayName("searchUsers - should map results to UserSearchResponse")
    void searchUsers_Success() {
        when(userRepository.searchByEmail("traveler")).thenReturn(List.of(sampleUser));

        List<UserSearchResponse> results = userService.searchUsers("traveler");

        assertEquals(1, results.size());
        assertEquals(sampleUser.getEmail(), results.get(0).getEmail());
        assertEquals(sampleUser.getFirstName(), results.get(0).getFirstName());
    }

    @Test
    @DisplayName("getUsersByRole - should filter by role and map to UserSearchResponse")
    void getUsersByRole_Success() {
        when(userRepository.findByRole(Role.TRAVELER)).thenReturn(List.of(sampleUser));

        List<UserSearchResponse> results = userService.getUsersByRole(Role.TRAVELER);

        assertEquals(1, results.size());
        assertEquals(Role.TRAVELER, results.get(0).getRole());
    }
}
