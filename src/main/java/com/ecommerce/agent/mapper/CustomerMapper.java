package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.Customer;

@Mapper
public interface CustomerMapper {

    @Select("""
            SELECT
                user_id,
                username,
                phone,
                email,
                address,
                level,
                registered_at
            FROM `user`
            ORDER BY registered_at DESC, user_id DESC
            LIMIT #{limit}
            """)
    List<Customer> findRecentCustomers(@Param("limit") Integer limit);

    @Select("""
            SELECT
                user_id,
                username,
                phone,
                email,
                address,
                level,
                registered_at
            FROM `user`
            WHERE user_id = #{userId}
            """)
    Customer findByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT
                user_id,
                username,
                phone,
                email,
                address,
                level,
                registered_at
            FROM `user`
            WHERE username LIKE CONCAT('%', #{keyword}, '%')
               OR email LIKE CONCAT('%', #{keyword}, '%')
               OR phone LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY registered_at DESC, user_id DESC
            LIMIT #{limit}
            """)
    List<Customer> searchCustomers(
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);

    @Select("""
            SELECT
                user_id,
                username,
                phone,
                email,
                address,
                level,
                registered_at
            FROM `user`
            WHERE level = #{level}
            ORDER BY registered_at DESC, user_id DESC
            LIMIT #{limit}
            """)
    List<Customer> findByLevel(
            @Param("level") Integer level,
            @Param("limit") Integer limit);
}
