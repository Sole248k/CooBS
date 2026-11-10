package com.group2.fse.order_service.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long productId;
    private String sku;
    private String name;
    private String category;
    private String unit;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String badge;
    private String farmSource;
    private Integer stockQuantity;
}
