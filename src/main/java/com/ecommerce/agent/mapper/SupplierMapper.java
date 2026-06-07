package com.ecommerce.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.ecommerce.agent.domain.Supplier;

@Mapper
public interface SupplierMapper {

    @Select("""
            SELECT
                supplier_id,
                name,
                contact_person,
                phone,
                address,
                email,
                rating,
                lead_time
            FROM supplier
            ORDER BY rating DESC, lead_time ASC, supplier_id
            LIMIT #{limit}
            """)
    List<Supplier> findTopSuppliers(@Param("limit") Integer limit);

    @Select("""
            SELECT
                supplier_id,
                name,
                contact_person,
                phone,
                address,
                email,
                rating,
                lead_time
            FROM supplier
            WHERE name LIKE CONCAT('%', #{keyword}, '%')
               OR contact_person LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY rating DESC, lead_time ASC, supplier_id
            LIMIT #{limit}
            """)
    List<Supplier> searchSuppliers(
            @Param("keyword") String keyword,
            @Param("limit") Integer limit);
}