package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.UserSearchResponse;
import tn.esprit.trippocketbackend.entities.User;

import java.util.List;

public interface IUserService {

    User createUser(User user);

    List<User> getAllUsers();

    User getUserById(Long id);

    User updateUser(Long id, User user);

    void deleteUser(Long id);

    List<UserSearchResponse> searchUsers(String firstName, String lastName, String email);
}
