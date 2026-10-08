package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
            WHERE (:firstName = '' OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%')))
              AND (:lastName  = '' OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :lastName,  '%')))
              AND (:email     = '' OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :email,     '%')))
            """)
    List<User> searchByMultiCriteria(@Param("firstName") String firstName,
                                     @Param("lastName")  String lastName,
                                     @Param("email")     String email);
}
