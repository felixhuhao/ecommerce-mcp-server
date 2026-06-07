package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.PurchaseOrder;

@Mapper
public interface PurchaseOrderMapper {

    @Select("""
            SELECT
                po_id,
                supplier_id,
                status,
                total_cost,
                created_at,
                received_at,
                cancelled_at
            FROM purchase_order
            ORDER BY created_at DESC, po_id DESC
            LIMIT #{limit}
            """)
    List<PurchaseOrder> findRecentPurchaseOrders(@Param("limit") Integer limit);
}
