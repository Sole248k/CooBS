package com.group2.fse.order_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "COOP_SHOP_ORDER")
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 64)
    private String referenceNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    @Column(name = "hold_id")
    private Long holdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    @Builder.Default
    private OrderStage stage = OrderStage.HELD;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "pickup_branch", nullable = false, length = 150)
    @Builder.Default
    private String pickupBranch = "PureSilver Central Mall Depot";

    @Column(name = "pickup_pass_token", nullable = false, length = 128)
    private String pickupPassToken;

    @Column(name = "pickup_time_slot", nullable = false, length = 100)
    @Builder.Default
    private String pickupTimeSlot = "4:00 PM - 6:00 PM";

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ShopOrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
