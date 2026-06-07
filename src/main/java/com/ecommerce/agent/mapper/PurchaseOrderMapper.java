package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.PurchaseOrder;
import com.ecommerce.agent.domain.PurchaseOrderItem;

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

    @Insert("""
            INSERT INTO purchase_order (
                supplier_id,
                status,
                total_cost
            ) VALUES (
                #{supplierId},
                #{status},
                #{totalCost}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "poId")
    int insertPurchaseOrder(PurchaseOrder purchaseOrder);

    @Insert("""
            INSERT INTO purchase_order_item (
                po_id,
                product_id,
                quantity,
                unit_cost,
                subtotal
            ) VALUES (
                #{poId},
                #{productId},
                #{quantity},
                #{unitCost},
                #{subtotal}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "poItemId")
    int insertPurchaseOrderItem(PurchaseOrderItem purchaseOrderItem);
}
