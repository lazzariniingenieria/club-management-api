package com.lazzariniingenieria.clubmanagementapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_club_dni", columnNames = {"club_id", "dni"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "family_group_id")
    private Long familyGroupId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "dni", nullable = false, length = 20)
    private String dni;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    // ACTIVE/INACTIVE enum, not the boolean `active` UserAccount uses: this mirrors the technical spec's member
    // schema as-is, which models status as a string enum to leave room for future values beyond a simple toggle.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
