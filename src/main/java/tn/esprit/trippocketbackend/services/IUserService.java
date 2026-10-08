package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.UserSearchResponse;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;

import java.util.List;

public interface IUserService {

    User createUser(User user);

    PageResponse<User> getAllUsers(int page, int size);

    List<User> getAllUsers();

    User getUserById(Long id);

    User updateUser(Long id, User user);

    void deleteUser(Long id);

    List<UserSearchResponse> searchUsers(String email);

    List<UserSearchResponse> getUsersByRole(Role role);
}
