package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.Product;

@Mapper
public interface ProductMapper {

    @Select("""
            SELECT
                product_id,
                sku,
                name,
                category,
                price,
                cost,
                status,
                created_at,
                updated_at
            FROM product
            WHERE product_id = #{productId}
            """)
    Product findById(@Param("productId") Long productId);

    @Select("""
            SELECT
                product_id,
                sku,
                name,
                category,
                price,
                cost,
                status,
                created_at,
                updated_at
            FROM product
            WHERE status = 'active'
            ORDER BY product_id
            LIMIT #{limit}
            """)
    List<Product> findActiveProducts(@Param("limit") Integer limit);

    @Select("""
            SELECT
                product_id,
                sku,
                name,
                category,
                price,
                cost,
                status,
                created_at,
                updated_at
            FROM product
            WHERE status = 'active'
            AND (
                sku LIKE CONCAT('%', #{keyword}, '%')
                OR name LIKE CONCAT('%', #{keyword}, '%')
                OR category LIKE CONCAT('%', #{keyword}, '%')
            )
            ORDER BY product_id
            LIMIT #{limit}
            """)
    List<Product> searchActiveProducts(
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);
}
