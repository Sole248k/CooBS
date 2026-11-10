package com.group2.fse.customer_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CHANNEL_PROFILE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProfile {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "membership_number", length = 20, nullable = false, unique = true)
    private String membershipNumber;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Builder.Default
    @Column(name = "preferred_language", length = 10, nullable = false)
    private String preferredLanguage = "en";

    @Column(name = "kyc_tier", length = 20, nullable = false)
    private String kycTier;

    @Builder.Default
    @Column(name = "kyc_status", length = 20, nullable = false)
    private String kycStatus = "VERIFIED";

    @Column(name = "risk_score", precision = 4, scale = 2, nullable = false)
    private BigDecimal riskScore;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", length = 150)
    private String email;

    @Builder.Default
    @Column(name = "daily_limit", precision = 18, scale = 4)
    private BigDecimal dailyLimit = new BigDecimal("100000.0000");

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
