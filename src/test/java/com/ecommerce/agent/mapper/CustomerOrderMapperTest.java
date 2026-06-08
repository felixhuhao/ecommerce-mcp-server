package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.agent.domain.CustomerOrder;

@SpringBootTest
class CustomerOrderMapperTest {

    @Autowired
    private CustomerOrderMapper customerOrderMapper;

    @Test
    @Transactional
    void updateStatusIfCurrentUpdatesMatchingOrderOnce() {
        CustomerOrder paidOrder = customerOrderMapper.selectOne(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getStatus, "paid")
                .last("LIMIT 1"));

        int firstUpdateRows = customerOrderMapper.updateStatusIfCurrent(
                paidOrder.getOrderId(),
                "paid",
                "shipped");
        int secondUpdateRows = customerOrderMapper.updateStatusIfCurrent(
                paidOrder.getOrderId(),
                "paid",
                "shipped");
        CustomerOrder updated = customerOrderMapper.selectById(paidOrder.getOrderId());

        assertThat(firstUpdateRows).isEqualTo(1);
        assertThat(secondUpdateRows).isZero();
        assertThat(updated.getStatus()).isEqualTo("shipped");
        assertThat(updated.getShippedAt()).isNotNull();
    }
}
