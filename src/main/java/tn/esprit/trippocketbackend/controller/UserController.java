package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.UserSearchResponse;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.services.IUserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final IUserService userService;

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<User> createUser(@jakarta.validation.Valid @RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
    }

    @GetMapping
    @Operation(summary = "Get paginated users")
    public ResponseEntity<PageResponse<User>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getAllUsers(page, size));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by email")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String email) {
        return ResponseEntity.ok(userService.searchUsers(email));
    }

    @GetMapping("/filter")
    @Operation(summary = "Filter users by role (TRAVELER or ADMIN)")
    public ResponseEntity<List<UserSearchResponse>> getUsersByRole(
            @RequestParam Role role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @jakarta.validation.Valid @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
