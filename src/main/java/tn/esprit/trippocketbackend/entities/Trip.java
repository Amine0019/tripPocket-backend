package tn.esprit.trippocketbackend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Header ("Discover Japan" / "Tokyo, Kyoto & Osaka · 7 days · ★ 4.9 (126)" / "Best seller")
    private String title;

    private String subtitle;

    private String destination;

    private Integer durationDays;

    private Double rating;

    private Integer reviewCount;

    private String badge;

    // ── Long description
    @Column(columnDefinition = "TEXT")
    private String description;

    // ── Dates
    private LocalDate startDate;

    private LocalDate endDate;

    // ── Booking info ("From $1,290 / person", "Available May–October · Group of 12 · Balanced")
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private String currency;

    private String availability;

    private Integer groupSize;

    private String pace;

    // ── Highlights
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_highlights", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "highlight", length = 500)
    private List<String> highlights;

    // ── Itinerary (Day 1, Day 2, ...)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_itinerary", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "sort_order")
    private List<ItineraryDay> itinerary;

    // ── Cover image
    @JsonIgnore
    @Lob
    @Column(name = "cover_image", columnDefinition = "LONGBLOB")
    private byte[] coverImage;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Activity> activities = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<BudgetExpense> expenses = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Reservation> reservations = new ArrayList<>();
}