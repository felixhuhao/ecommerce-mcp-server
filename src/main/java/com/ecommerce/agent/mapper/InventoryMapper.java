package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.Inventory;

@Mapper
public interface InventoryMapper {

    @Select("""
            SELECT
                product_id,
                quantity,
                safety_stock,
                warehouse,
                updated_at
            FROM inventory
            WHERE quantity < safety_stock
            ORDER BY (safety_stock - quantity) DESC
            LIMIT #{limit}
            """)
    List<Inventory> findLowStockItems(@Param("limit") Integer limit);

    @Select("""
            <script>
            SELECT
                product_id,
                quantity,
                safety_stock,
                warehouse,
                updated_at
            FROM inventory
            WHERE 1 = 1
            <if test="productId != null">
                AND product_id = #{productId}
            </if>
            <if test="warehouse != null and warehouse != ''">
                AND warehouse = #{warehouse}
            </if>
            ORDER BY product_id, warehouse
            LIMIT #{limit}
            </script>
            """)
    List<Inventory> queryInventory(
            @Param("productId") Long productId,
            @Param("warehouse") String warehouse,
            @Param("limit") Integer limit);
}
