package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ecommerce.agent.domain.Inventory;

@Mapper
public interface InventoryMapper {

    @Select("""
            SELECT
                i.product_id,
                p.sku,
                p.name AS product_name,
                i.quantity,
                i.safety_stock,
                i.warehouse,
                i.updated_at
            FROM inventory i
            JOIN product p ON p.product_id = i.product_id
            WHERE i.product_id = #{productId}
            """)
    Inventory findByProductId(@Param("productId") Long productId);

    @Select("""
            SELECT
                i.product_id,
                p.sku,
                p.name AS product_name,
                i.quantity,
                i.safety_stock,
                i.warehouse,
                i.updated_at
            FROM inventory i
            JOIN product p ON p.product_id = i.product_id
            WHERE i.quantity < i.safety_stock
            ORDER BY (i.safety_stock - i.quantity) DESC
            LIMIT #{limit}
            """)
    List<Inventory> findLowStockItems(@Param("limit") Integer limit);

    @Select("""
            <script>
            SELECT
                i.product_id,
                p.sku,
                p.name AS product_name,
                i.quantity,
                i.safety_stock,
                i.warehouse,
                i.updated_at
            FROM inventory i
            JOIN product p ON p.product_id = i.product_id
            WHERE 1 = 1
            <if test="productId != null">
                AND i.product_id = #{productId}
            </if>
            <if test="warehouse != null and warehouse != ''">
                AND i.warehouse = #{warehouse}
            </if>
            ORDER BY i.product_id, i.warehouse
            LIMIT #{limit}
            </script>
            """)
    List<Inventory> queryInventory(
            @Param("productId") Long productId,
            @Param("warehouse") String warehouse,
            @Param("limit") Integer limit);

    @Update("""
            UPDATE inventory
            SET quantity = quantity + #{quantity}
            WHERE product_id = #{productId}
            """)
    int incrementQuantity(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);
}
