-- ecommerce_db Schema
-- Generated: 2026-06-06 00:08:38

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS ecommerce_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce_db;

-- 商品表
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `product_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `category` VARCHAR(50) NOT NULL COMMENT '品类: electronics/clothing/home/food/sports',
  `price` DECIMAL(10,2) NOT NULL COMMENT '售价',
  `cost` DECIMAL(10,2) NOT NULL COMMENT '成本价',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/inactive/discontinued',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '地址',
  `level` TINYINT NOT NULL DEFAULT 1 COMMENT '等级: 1普通 2银牌 3金牌 4钻石',
  `registered_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`user_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 供应商表
DROP TABLE IF EXISTS `supplier`;
CREATE TABLE `supplier` (
  `supplier_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `name` VARCHAR(100) NOT NULL COMMENT '供应商名称',
  `contact_person` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '地址',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `rating` DECIMAL(2,1) NOT NULL DEFAULT 4.0 COMMENT '评分 1.0-5.0',
  `lead_time` INT NOT NULL DEFAULT 3 COMMENT '交货天数',
  PRIMARY KEY (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- 订单表
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `total_amount` DECIMAL(12,2) NOT NULL COMMENT '订单总额',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/paid/shipped/completed/cancelled',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
  `shipped_at` DATETIME DEFAULT NULL COMMENT '发货时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  PRIMARY KEY (`order_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL COMMENT '数量',
  `unit_price` DECIMAL(10,2) NOT NULL COMMENT '成交单价',
  `subtotal` DECIMAL(12,2) NOT NULL COMMENT '小计金额',
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 库存表
DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory` (
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL DEFAULT 0 COMMENT '当前库存',
  `safety_stock` INT NOT NULL DEFAULT 50 COMMENT '安全库存线',
  `warehouse` VARCHAR(20) NOT NULL DEFAULT 'A区' COMMENT '仓库位置',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 评价表
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `review_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `rating` TINYINT NOT NULL COMMENT '评分 1-5',
  `content` TEXT COMMENT '评价内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`review_id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

DROP TABLE IF EXISTS `purchase_order_item`;
DROP TABLE IF EXISTS `purchase_order`;

-- 采购订单表
CREATE TABLE `purchase_order` (
  `po_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'placed' COMMENT '状态: placed/received/cancelled',
  `total_cost` DECIMAL(12,2) NOT NULL COMMENT '采购总成本',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `received_at` DATETIME DEFAULT NULL COMMENT '收货时间',
  `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  PRIMARY KEY (`po_id`),
  KEY `idx_supplier` (`supplier_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单表';

-- 采购订单明细表
CREATE TABLE `purchase_order_item` (
  `po_item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '采购明细ID',
  `po_id` BIGINT NOT NULL COMMENT '采购订单ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL COMMENT '采购数量',
  `unit_cost` DECIMAL(10,2) NOT NULL COMMENT '采购单价',
  `subtotal` DECIMAL(12,2) NOT NULL COMMENT '采购小计',
  PRIMARY KEY (`po_item_id`),
  KEY `idx_po` (`po_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

-- HITL审批记录表
DROP TABLE IF EXISTS `approval_record`;
CREATE TABLE `approval_record` (
  `approval_id` VARCHAR(36) NOT NULL COMMENT '审批ID (UUID)',
  `operation_hash` VARCHAR(64) NOT NULL COMMENT '规范化授权载荷SHA-256哈希',
  `tool_name` VARCHAR(40) NOT NULL COMMENT '授权的写工具名称',
  `operation_type` VARCHAR(20) NOT NULL COMMENT '操作类型: create/modify/receive',
  `operation_payload` JSON NOT NULL COMMENT '规范化授权载荷: 参数+服务端前置条件',
  `operation_detail` JSON NOT NULL COMMENT '服务端渲染的人类审批详情',
  `user_id` BIGINT NOT NULL COMMENT '绑定的用户ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '绑定的会话ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected/expired/consumed/invalidated/failed',
  `rejection_reason` TEXT DEFAULT NULL COMMENT '拒绝原因',
  `execution_result` JSON DEFAULT NULL COMMENT '后端执行结果或失败/失效原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `rejected_at` DATETIME DEFAULT NULL COMMENT '拒绝时间',
  `consumed_at` DATETIME DEFAULT NULL COMMENT '消费时间',
  `executed_at` DATETIME DEFAULT NULL COMMENT '执行完成时间',
  PRIMARY KEY (`approval_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_session` (`user_id`, `session_id`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HITL审批记录表';
