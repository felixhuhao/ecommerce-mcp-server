package com.ecommerce.agent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ecommerce.agent.domain.ApprovalRecord;

@Mapper
public interface ApprovalRecordMapper {

    @Select("""
            SELECT
                approval_id,
                operation_hash,
                tool_name,
                operation_type,
                operation_payload,
                operation_detail,
                user_id,
                session_id,
                status,
                created_at,
                expires_at,
                consumed_at
            FROM approval_record
            WHERE approval_id = #{approvalId}
            """)
    ApprovalRecord findById(@Param("approvalId") String approvalId);

    @Insert("""
            INSERT INTO approval_record (
                approval_id,
                operation_hash,
                tool_name,
                operation_type,
                operation_payload,
                operation_detail,
                user_id,
                session_id,
                status,
                expires_at,
                consumed_at
            )
            VALUES (
                #{approvalId},
                #{operationHash},
                #{toolName},
                #{operationType},
                #{operationPayload},
                #{operationDetail},
                #{userId},
                #{sessionId},
                #{status},
                #{expiresAt},
                #{consumedAt}
            )
            """)
    int insert(ApprovalRecord approvalRecord);

    @Update("""
            UPDATE approval_record
            SET status = 'approved'
            WHERE approval_id = #{approvalId}
            AND user_id = #{userId}
            AND session_id = #{sessionId}
            AND status = 'pending'
            AND expires_at > NOW()
            """)
    int approvePending(
            @Param("approvalId") String approvalId,
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId);

    @Update("""
            UPDATE approval_record
            SET status = 'rejected'
            WHERE approval_id = #{approvalId}
            AND user_id = #{userId}
            AND session_id = #{sessionId}
            AND status = 'pending'
            AND expires_at > NOW()
            """)
    int rejectPending(
            @Param("approvalId") String approvalId,
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId);

    @Update("""
            UPDATE approval_record
            SET status = 'consumed',
                consumed_at = NOW()
            WHERE approval_id = #{approvalId}
            AND operation_hash = #{operationHash}
            AND tool_name = #{toolName}
            AND user_id = #{userId}
            AND session_id = #{sessionId}
            AND status = 'approved'
            AND expires_at > NOW()
            AND consumed_at IS NULL
            """)
    int consumeApproved(
            @Param("approvalId") String approvalId,
            @Param("operationHash") String operationHash,
            @Param("toolName") String toolName,
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId);
}
