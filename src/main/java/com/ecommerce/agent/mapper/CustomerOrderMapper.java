package com.ecommerce.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.ecommerce.agent.domain.CustomerOrder;

@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {
}
