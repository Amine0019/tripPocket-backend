package tn.esprit.trippocketbackend.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryDay {

    private Integer dayNumber;

    private String title;

    @Column(length = 2000)
    private String description;
}