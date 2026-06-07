package com.ecommerce.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ecommerce.agent.dto.ProductResult;

@SpringBootTest
class ProductToolTest {

    @Autowired
    private ProductTool productTool;

    @Test
    void productQueryReturnsProductResults() {
        List<ProductResult> products = productTool.productQuery(null, 5);

        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeLessThanOrEqualTo(5);
        assertThat(products.getFirst().productId()).isNotNull();
        assertThat(products.getFirst().name()).isNotBlank();
    }

    @Test
    void productSearchReturnsMatchingProductResults() {
        List<ProductResult> products = productTool.productSearch("手机", 10);

        assertThat(products).isNotEmpty();
        assertThat(products)
                .allMatch(product -> product.name().contains("手机")
                        || product.category().contains("手机"));
    }

    @Test
    void productQueryReturnsMatchingProductResultsWhenKeywordProvided() {
        List<ProductResult> products = productTool.productQuery("手机", 10);

        assertThat(products).isNotEmpty();
        assertThat(products)
                .allMatch(product -> product.name().contains("手机")
                        || product.category().contains("手机"));
    }
}
