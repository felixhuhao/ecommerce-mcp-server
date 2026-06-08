package com.ecommerce.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.ecommerce.agent.domain.CustomerOrder;

@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {

    @Update("""
            UPDATE orders
            SET status = #{toStatus},
                shipped_at = CASE
                    WHEN #{toStatus} = 'shipped' THEN NOW()
                    ELSE shipped_at
                END,
                completed_at = CASE
                    WHEN #{toStatus} = 'completed' THEN NOW()
                    ELSE completed_at
                END,
                cancelled_at = CASE
                    WHEN #{toStatus} = 'cancelled' THEN NOW()
                    ELSE cancelled_at
                END
            WHERE order_id = #{orderId}
            AND status = #{fromStatus}
            """)
    int updateStatusIfCurrent(
            @Param("orderId") Long orderId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus);
}
