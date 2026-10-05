package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "CUSTOMER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Builder.Default
    @Column(name = "kyc_status", length = 20, nullable = false)
    private String kycStatus = "VERIFIED";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Kyc kyc;

    public String getDisplayName() {
        if (kyc != null && kyc.getFirstName() != null) {
            String mid = (kyc.getMiddleInitial() != null && !kyc.getMiddleInitial().isBlank()) 
                    ? kyc.getMiddleInitial() + " " : "";
            return (kyc.getFirstName() + " " + mid + (kyc.getLastName() != null ? kyc.getLastName() : "")).trim();
        }
        return "Customer #" + customerId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (kycStatus == null) {
            kycStatus = "VERIFIED";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return customerId != null && Objects.equals(customerId, customer.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }
}
