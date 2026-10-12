package com.lazzariniingenieria.clubmanagementapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "club")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "courts_enabled", nullable = false)
    private boolean courtsEnabled;

    @Column(name = "member_app_enabled", nullable = false)
    private boolean memberAppEnabled;

    @Column(name = "monthly_fee_amount", precision = 10, scale = 2)
    private BigDecimal monthlyFeeAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
