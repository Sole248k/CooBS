package com.group2.fse.customer_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "BENEFICIARY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_number", length = 34, nullable = false)
    private String accountNumber;

    @Column(name = "nickname", length = 60, nullable = false)
    private String nickname;

    @Builder.Default
    @Column(name = "bank_code", length = 20, nullable = false)
    private String bankCode = "COOBS";

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
