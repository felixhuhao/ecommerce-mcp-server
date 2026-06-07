package com.ecommerce.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Product;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void searchActiveProductsUsesKeyword() {
        List<Product> products = productService.searchActiveProducts("手机", 10);

        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeLessThanOrEqualTo(10);
        assertThat(products)
                .allMatch(product -> product.getName().contains("手机")
                        || product.getCategory().contains("手机"));
    }

    @Test
    void searchActiveProductsFallsBackToActiveProductsWhenKeywordBlank() {
        List<Product> products = productService.searchActiveProducts("   ", 5);

        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void findActiveProductsCapsLargeLimit() {
        List<Product> products = productService.findActiveProducts(500);

        assertThat(products).hasSizeLessThanOrEqualTo(50);
    }
}
