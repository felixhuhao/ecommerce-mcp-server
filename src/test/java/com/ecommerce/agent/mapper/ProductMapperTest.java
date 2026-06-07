package com.ecommerce.agent.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.domain.Product;

@SpringBootTest
class ProductMapperTest {

    @Autowired
    private ProductMapper productMapper;

    @Test
    void findActiveProductsReturnsProducts() {
        List<Product> products = productMapper.findActiveProducts(5);

        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeLessThanOrEqualTo(5);
        assertThat(products.getFirst().getProductId()).isNotNull();
        assertThat(products.getFirst().getName()).isNotBlank();
    }

    @Test
    void searchActiveProductsReturnsMatchingProducts() {
        List<Product> products = productMapper.searchActiveProducts("手机", 10);

        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeLessThanOrEqualTo(10);
        assertThat(products)
                .allMatch(product -> product.getName().contains("手机")
                        || product.getCategory().contains("手机"));
    }
}
