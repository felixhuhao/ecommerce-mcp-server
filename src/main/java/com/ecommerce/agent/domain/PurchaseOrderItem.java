package com.ecommerce.agent.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class PurchaseOrderItem {
    private Long poItemId;
    private Long poId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
}
