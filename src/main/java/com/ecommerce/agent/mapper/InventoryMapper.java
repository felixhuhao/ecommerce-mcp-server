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
}
