package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    boolean existsByEmail(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (:email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
            """)
    List<User> searchByEmail(@Param("email") String email);

    List<User> findByRole(Role role);
}
