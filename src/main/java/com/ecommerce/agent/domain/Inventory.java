package com.ecommerce.agent.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Inventory {
    private Long productId;
    private String sku;
    private String productName;
    private Integer quantity;
    private Integer safetyStock;
    private String warehouse;
    private LocalDateTime updatedAt;
}
