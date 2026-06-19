package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.StatisticsResult;

@SpringBootTest
class StatsServiceTest {

    @Autowired
    private StatsService statsService;

    @Test
    void getStatisticsReturnsAggregateSections() {
        StatisticsResult result = statsService.getStatistics(5);

        assertThat(result.inventory()).isNotNull();
        assertThat(result.ordersByStatus()).isNotEmpty();
        assertThat(result.productsByCategory()).isNotEmpty();
        assertThat(result.salesByCategory()).isNotEmpty();
        assertThat(result.salesDropWow()).isNotEmpty();
        assertThat(result.purchaseOrdersByStatus()).isNotEmpty();
        assertThat(result.topProductsByRevenue()).isNotEmpty();
        assertThat(result.topProductsByRevenue()).hasSizeLessThanOrEqualTo(5);
        assertThat(result.topCustomersBySpend()).isNotEmpty();
        assertThat(result.topCustomersBySpend()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void getStatisticsCapsTopProductLimit() {
        StatisticsResult result = statsService.getStatistics(500);

        assertThat(result.topProductsByRevenue()).hasSizeLessThanOrEqualTo(20);
        assertThat(result.topCustomersBySpend()).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void getStatisticsIncludesStableSalesDropWowAggregate() {
        StatisticsResult result = statsService.getStatistics(5);

        assertThat(result.salesDropWow())
                .anySatisfy(drop -> {
                    assertThat(drop.category()).isEqualTo("home");
                    assertThat(drop.currentSales()).isEqualByComparingTo("129.00");
                    assertThat(drop.previousSales()).isEqualByComparingTo("2580.00");
                    assertThat(drop.dropPct()).isEqualByComparingTo("0.9500");
                });
    }
}
