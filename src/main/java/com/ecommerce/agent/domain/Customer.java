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
public class Customer {
    private Long userId;
    private String username;
    private String phone;
    private String email;
    private String address;
    private Integer level;
    private LocalDateTime registeredAt;
}
