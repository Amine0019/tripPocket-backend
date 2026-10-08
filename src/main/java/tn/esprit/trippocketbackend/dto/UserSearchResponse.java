package tn.esprit.trippocketbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tn.esprit.trippocketbackend.entities.Role;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResponse {

    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private Boolean enabled;
}
