package tn.esprit.trippocketbackend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("traveler@example.com")
                .password("encodedSecret")
                .enabled(true)
                .role(Role.TRAVELER)
                .build();
    }

    @Test
    @DisplayName("loadUserByUsername - should return UserDetails with correct authorities when user exists")
    void loadUserByUsername_UserExists() {
        when(userRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(sampleUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("traveler@example.com");

        assertNotNull(userDetails);
        assertEquals("traveler@example.com", userDetails.getUsername());
        assertEquals("encodedSecret", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TRAVELER")));
    }

    @Test
    @DisplayName("loadUserByUsername - should reflect disabled user state")
    void loadUserByUsername_DisabledUser() {
        sampleUser.setEnabled(false);
        when(userRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(sampleUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("traveler@example.com");

        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername - should throw UsernameNotFoundException when user does not exist")
    void loadUserByUsername_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("notfound@example.com"));
    }
}
