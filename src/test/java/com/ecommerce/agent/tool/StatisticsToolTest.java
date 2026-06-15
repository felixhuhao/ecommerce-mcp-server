package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.StatisticsResult;

@SpringBootTest
class StatisticsToolTest {

    @Autowired
    private StatisticsTool statisticsTool;

    @Test
    void getStatisticsReturnsToolResult() {
        StatisticsResult result = statisticsTool.getStatistics(3);

        assertThat(result.inventory().productCount()).isPositive();
        assertThat(result.ordersByStatus()).isNotEmpty();
        assertThat(result.topProductsByRevenue()).hasSizeLessThanOrEqualTo(3);
        assertThat(result.topCustomersBySpend()).hasSizeLessThanOrEqualTo(3);
    }
}
