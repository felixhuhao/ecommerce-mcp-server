"""Generate e-commerce mock data and SQL files."""
import random
from datetime import datetime, timedelta
from pathlib import Path

random.seed(42)

# === Products ===
categories = {
    'electronics': [
        ('智能手机', 2999, 1800), ('无线蓝牙耳机', 299, 120), ('快充充电器', 79, 25),
        ('手机壳', 39, 8), ('Type-C数据线', 29, 6), ('平板电脑', 2599, 1500),
        ('智能手表', 899, 400), ('蓝牙音箱', 199, 70), ('移动电源', 129, 45),
        ('机械键盘', 349, 130),
    ],
    'clothing': [
        ('纯棉T恤', 89, 28), ('牛仔裤', 199, 60), ('运动鞋', 399, 120),
        ('连帽卫衣', 169, 50), ('冲锋衣', 459, 150), ('休闲短裤', 99, 30),
        ('商务衬衫', 159, 55), ('连衣裙', 239, 70), ('羊毛毛衣', 349, 110),
        ('棒球帽', 59, 15),
    ],
    'home': [
        ('LED台灯', 129, 40), ('收纳盒套装', 49, 12), ('记忆棉抱枕', 79, 22),
        ('感应垃圾桶', 159, 50), ('旋转拖把', 89, 28), ('洗衣液5L', 59, 18),
        ('抽纸巾24包', 49, 20), ('不粘锅', 169, 55), ('厨师刀', 99, 30),
        ('餐具四件套', 199, 65),
    ],
    'food': [
        ('混合坚果500g', 59, 22), ('零食大礼包', 89, 30), ('速溶咖啡30条', 69, 20),
        ('纯牛奶24盒', 59, 30), ('方便面12连包', 39, 15), ('进口巧克力', 49, 18),
        ('铁观音茶叶250g', 79, 25), ('夹心饼干', 29, 10), ('芒果干100g', 25, 8),
        ('天然蜂蜜500ml', 69, 28),
    ],
    'sports': [
        ('瑜伽垫', 89, 28), ('跑步鞋', 499, 160), ('可调节哑铃', 259, 90),
        ('钢丝跳绳', 39, 10), ('运动手套', 49, 14), ('大容量水壶', 59, 18),
        ('运动腰包', 39, 10), ('护膝', 69, 22), ('泡沫轴', 79, 25),
        ('弹力带套装', 49, 12),
    ],
}

pid = 1
products = []
for cat, items in categories.items():
    for name, price, cost in items:
        status = random.choices(['active', 'active', 'active', 'inactive'], k=1)[0]
        products.append((pid, name, cat, price, cost, status))
        pid += 1

# === Suppliers ===
suppliers_raw = [
    ('深圳华强电子科技有限公司', '张伟', '13800001001', '华强北路1号', 'supplier_a@hq.com'),
    ('广州白马服装批发商', '李娜', '13800002002', '站南路20号', 'sale@baima.com'),
    ('义乌小商品批发中心', '王磊', '13800003003', '国际商贸城A区', 'trade@yiwu.com'),
    ('杭州西湖食品加工厂', '陈静', '13800004004', '西湖区文三路50号', 'info@xihufood.com'),
    ('宁波跨境贸易公司', '刘洋', '13800005005', '北仑区港口路8号', 'trade@nb-cross.com'),
    ('上海美家居日用品', '赵敏', '13800006006', '闵行区莘庄工业区', 'sale@meijiaju.com'),
    ('泉州运动器材制造', '黄强', '13800007007', '晋江市体育用品产业园', 'export@qzsport.com'),
    ('成都蜀味零食工坊', '周婷', '13800008008', '郫都区川菜产业园', 'sale@shuwei.com'),
    ('北京数码科技贸易', '吴刚', '13800009009', '中关村科技园B座', 'biz@bj-digital.com'),
    ('苏州丝绸纺织品', '孙丽', '13800010010', '吴中区丝绸市场', 'sale@suzhou-silk.com'),
    ('青岛海风运动户外', '马超', '13800011011', '崂山区体育中心', 'outdoor@haifeng.com'),
    ('武汉光电子配件厂', '郑华', '13800012012', '东湖高新区光谷大道', 'parts@wh-optical.com'),
]

suppliers = []
for i, (name, contact, phone, addr, email) in enumerate(suppliers_raw, 1):
    rating = round(random.uniform(3.5, 5.0), 1)
    lead = random.choice([1, 2, 3, 3, 5, 5, 7])
    suppliers.append((i, name, contact, phone, addr, email, rating, lead))

# === Users ===
last_names = '张王李赵刘陈杨黄周吴徐孙马朱胡郭林何高罗'
first_names = ['伟', '强', '磊', '军', '勇', '杰', '涛', '明', '超', '刚',
               '芳', '娜', '静', '敏', '婷', '丽', '艳', '雪', '倩', '莹']
cities = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '重庆', '西安',
          '苏州', '天津', '长沙', '青岛', '郑州', '大连', '厦门', '昆明', '宁波', '合肥']
districts = ['朝阳区', '海淀区', '浦东新区', '天河区', '南山区', '西湖区', '武侯区', '江汉区', '鼓楼区', '渝中区']

users = []
for i in range(1, 41):
    name = random.choice(last_names) + random.choice(first_names)
    city = random.choice(cities)
    phone = f'139{random.randint(10000000, 99999999)}'
    email = f'user{i}@example.com'
    level = random.choices([1, 2, 2, 3, 3, 3, 4], k=1)[0]
    addr = f'{city}市{random.choice(districts)}某某街道{i}号'
    reg = datetime(2025, random.randint(1, 12), random.randint(1, 28))
    users.append((i, name, phone, email, addr, level, reg.strftime('%Y-%m-%d %H:%M:%S')))

# === Inventory ===
inventory = []
for prod in products:
    pid_val, _, _, _, _, _ = prod
    qty = random.randint(0, 500)
    safety = random.randint(20, 100)
    wh = random.choice(['A区', 'B区', 'C区'])
    inventory.append((pid_val, qty, safety, wh))

# === Orders + Order Items ===
statuses = ['pending', 'paid', 'shipped', 'completed', 'cancelled']
status_weights = [5, 10, 15, 60, 10]

orders = []
order_items = []
oid = 1
oiid = 1

for _ in range(250):
    uid = random.randint(1, 40)
    month = random.choices([12, 1, 2, 3, 4, 5], weights=[10, 12, 15, 18, 22, 23], k=1)[0]
    year = 2025 if month == 12 else 2026
    day = random.randint(1, 28)
    hour = random.randint(8, 23)
    minute = random.randint(0, 59)
    created = datetime(year, month, day, hour, minute)

    status = random.choices(statuses, weights=status_weights, k=1)[0]

    num_items = random.choices([1, 2, 3, 4, 5], weights=[40, 30, 15, 10, 5], k=1)[0]
    selected = random.sample(products, min(num_items, len(products)))

    total = 0.0
    for prod in selected:
        pid_val, pname, cat, price, cost, _ = prod
        qty = random.choices([1, 2, 3, 4, 5, 10], weights=[40, 25, 15, 10, 5, 5], k=1)[0]
        unit_price = round(price * random.choice([0.9, 0.95, 1.0, 1.0, 1.0]), 2)
        subtotal = round(unit_price * qty, 2)
        total += subtotal
        order_items.append((oiid, oid, pid_val, qty, unit_price, subtotal))
        oiid += 1

    total = round(total, 2)
    paid = (created + timedelta(hours=random.randint(0, 48))).strftime('%Y-%m-%d %H:%M:%S') if status in ['paid', 'shipped', 'completed'] else None
    shipped = (created + timedelta(days=random.randint(1, 5))).strftime('%Y-%m-%d %H:%M:%S') if status in ['shipped', 'completed'] else None
    completed = (created + timedelta(days=random.randint(3, 12))).strftime('%Y-%m-%d %H:%M:%S') if status == 'completed' else None
    cancelled = (created + timedelta(hours=random.randint(1, 72))).strftime('%Y-%m-%d %H:%M:%S') if status == 'cancelled' else None

    orders.append((oid, uid, total, status,
                    created.strftime('%Y-%m-%d %H:%M:%S'), paid, shipped, completed, cancelled))
    oid += 1

# === Reviews ===
reviews = []
rid = 1
review_contents = {
    1: ['质量太差了', '完全不值这个价', '和描述严重不符', '不会再买了', '非常失望'],
    2: ['一般般吧', '不太满意', '质量有待提高', '物流太慢了', '凑合用'],
    3: ['还行', '一般般', '中规中矩', '性价比一般', '质量普通'],
    4: ['不错，推荐', '挺好的', '质量可以', '性价比高', '物流很快'],
    5: ['非常满意', '质量很好，强烈推荐', '超出预期', '完美', '物超所值'],
}

for _ in range(300):
    uid = random.randint(1, 40)
    pid_val = random.randint(1, len(products))
    rating = random.choices([1, 2, 3, 4, 5], weights=[3, 5, 12, 35, 45], k=1)[0]
    content = random.choice(review_contents[rating])
    created = datetime(2026, random.randint(1, 5), random.randint(1, 28))
    reviews.append((rid, uid, pid_val, rating, content, created.strftime('%Y-%m-%d %H:%M:%S')))
    rid += 1

# === Historical Purchase Orders + Items ===
purchase_orders = []
purchase_order_items = []
poid = 1
poiid = 1

for _ in range(12):
    supplier_id = random.randint(1, len(suppliers))
    month = random.choice([1, 2, 3, 4, 5])
    created = datetime(2026, month, random.randint(1, 24), random.randint(9, 17), random.randint(0, 59))
    received = created + timedelta(days=suppliers[supplier_id - 1][7])

    num_items = random.choice([1, 2, 2, 3])
    selected = random.sample(products, num_items)
    total_cost = 0.0

    for prod in selected:
        pid_val, _, _, _, cost, _ = prod
        qty = random.choice([50, 80, 100, 150, 200, 300])
        unit_cost = round(cost * random.choice([0.95, 1.0, 1.0, 1.05]), 2)
        subtotal = round(unit_cost * qty, 2)
        total_cost += subtotal
        purchase_order_items.append((poiid, poid, pid_val, qty, unit_cost, subtotal))
        poiid += 1

    purchase_orders.append((
        poid,
        supplier_id,
        'received',
        round(total_cost, 2),
        created.strftime('%Y-%m-%d %H:%M:%S'),
        received.strftime('%Y-%m-%d %H:%M:%S'),
        None,
    ))
    poid += 1

# === Write schema.sql ===
out_dir = Path(__file__).resolve().parent / 'src' / 'main' / 'resources'

schema = f"""-- ecommerce_db Schema
-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

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
  `status` VARCHAR(10) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected/expired/consumed',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `consumed_at` DATETIME DEFAULT NULL COMMENT '消费时间',
  PRIMARY KEY (`approval_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_session` (`user_id`, `session_id`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HITL审批记录表';
"""

# === Write data.sql ===
lines = ['SET NAMES utf8mb4;', '', 'USE ecommerce_db;', '']

lines.append('-- Products')
for p in products:
    lines.append(f"INSERT INTO `product` VALUES ({p[0]}, '{p[1]}', '{p[2]}', {p[3]}, {p[4]}, '{p[5]}', NOW(), NOW());")
lines.append('')

lines.append('-- Users')
for u in users:
    lines.append(f"INSERT INTO `user` VALUES ({u[0]}, '{u[1]}', '{u[2]}', '{u[3]}', '{u[4]}', {u[5]}, '{u[6]}');")
lines.append('')

lines.append('-- Suppliers')
for s in suppliers:
    lines.append(f"INSERT INTO `supplier` VALUES ({s[0]}, '{s[1]}', '{s[2]}', '{s[3]}', '{s[4]}', '{s[5]}', {s[6]}, {s[7]});")
lines.append('')

lines.append('-- Inventory')
for inv in inventory:
    lines.append(f"INSERT INTO `inventory` VALUES ({inv[0]}, {inv[1]}, {inv[2]}, '{inv[3]}', NOW());")
lines.append('')

lines.append('-- Orders')
for o in orders:
    paid = f"'{o[5]}'" if o[5] else 'NULL'
    shipped = f"'{o[6]}'" if o[6] else 'NULL'
    completed = f"'{o[7]}'" if o[7] else 'NULL'
    cancelled = f"'{o[8]}'" if o[8] else 'NULL'
    lines.append(f"INSERT INTO `orders` VALUES ({o[0]}, {o[1]}, {o[2]}, '{o[3]}', '{o[4]}', {paid}, {shipped}, {completed}, {cancelled});")
lines.append('')

lines.append('-- Order Items')
for oi in order_items:
    lines.append(f"INSERT INTO `order_item` VALUES ({oi[0]}, {oi[1]}, {oi[2]}, {oi[3]}, {oi[4]}, {oi[5]});")
lines.append('')

lines.append('-- Reviews')
for r in reviews:
    content_esc = r[4].replace("'", "\\'")
    lines.append(f"INSERT INTO `review` VALUES ({r[0]}, {r[1]}, {r[2]}, {r[3]}, '{content_esc}', '{r[5]}');")
lines.append('')

lines.append('-- Purchase Orders')
for po in purchase_orders:
    received_at = f"'{po[5]}'" if po[5] else 'NULL'
    cancelled_at = f"'{po[6]}'" if po[6] else 'NULL'
    lines.append(f"INSERT INTO `purchase_order` VALUES ({po[0]}, {po[1]}, '{po[2]}', {po[3]}, '{po[4]}', {received_at}, {cancelled_at});")
lines.append('')

lines.append('-- Purchase Order Items')
for poi in purchase_order_items:
    lines.append(f"INSERT INTO `purchase_order_item` VALUES ({poi[0]}, {poi[1]}, {poi[2]}, {poi[3]}, {poi[4]}, {poi[5]});")

data_sql = '\n'.join(lines)

with open(out_dir / 'schema.sql', 'w', encoding='utf-8') as f:
    f.write(schema)
with open(out_dir / 'data.sql', 'w', encoding='utf-8') as f:
    f.write(data_sql)

# Summary
status_dist = {}
for o in orders:
    status_dist[o[3]] = status_dist.get(o[3], 0) + 1

print("=== Data Summary ===")
print(f"Products:   {len(products)} (5 categories x 10 each)")
print(f"Users:      {len(users)}")
print(f"Suppliers:  {len(suppliers)}")
print(f"Orders:     {len(orders)}")
print(f"OrderItems: {len(order_items)}")
print(f"Inventory:  {len(inventory)}")
print(f"Reviews:    {len(reviews)}")
print(f"POs:        {len(purchase_orders)}")
print(f"POItems:    {len(purchase_order_items)}")
print(f"Order status: {status_dist}")
print(f"\nFiles written to {out_dir}/")
