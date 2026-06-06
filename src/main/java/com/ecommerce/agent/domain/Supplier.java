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
public class Supplier {
    private Long supplierId;
    private String name;
    private String contactPerson;
    private String phone;
    private String address;
    private String email;
    private BigDecimal rating;
    private Integer leadTime;
}
