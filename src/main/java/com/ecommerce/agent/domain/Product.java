package com.ecommerce.agent.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Product {
    private Long productId;
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private BigDecimal cost;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
