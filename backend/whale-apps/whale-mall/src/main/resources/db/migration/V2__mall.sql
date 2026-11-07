-- =====================================================================
-- BlueWhale 商城应用（whale-mall）M0 业务表（Wave 5.94）
-- =====================================================================
-- 范围       : 类目 / 商品 SPU / 规格 SKU / 多价目 / 可售库存 / 商城渠道 /
--              购物车 / 订单 / 订单明细 / 订单状态流水 / 编号流水
-- 需求       : REQ-FUNC-095（商城 M0）、REQ-ARCH-003（自持 /mall 前缀）
-- 边界       : 素材字节归 whale-file（本表只存 file_id / URL）；
--              支付归 whale-pay（本表只存 pay_no 与时间）；
--              商品主数据由本服务自建，不与 whale-erp 实时双写。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 类目（树，最多三级）
--    同级不允许重名：跨级同名是常态（「配件/电池」与「耗材/电池」），
--    用全路径唯一会把正常业务挡在门外，故唯一性只在应用层按 (parent_id, name) 校验。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_category (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    parent_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '上级类目ID，0 为一级类目',
    category_name       VARCHAR(64)   NOT NULL                COMMENT '类目名称',
    category_level      INT           NOT NULL DEFAULT 1      COMMENT '层级（1 起，最多 3 级）',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_category_parent (tenant_id, parent_id),
    KEY idx_mall_category_status (tenant_id, status)
) COMMENT='商城类目';

-- ---------------------------------------------------------------------
-- 2. 商品 SPU
--    spu_code 唯一：删除时由应用层改写为 `{code}#D{id}` 释放编码（软删 + 唯一键的标准解法），
--    否则删掉再建同名编码会撞唯一键，表现为「编码没被占用却建不进去」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_spu (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    spu_code            VARCHAR(64)   NOT NULL                COMMENT '商品编码（租户内唯一）',
    spu_name            VARCHAR(128)  NOT NULL                COMMENT '商品名称',
    category_id         BIGINT        NOT NULL                COMMENT '所属类目ID',
    brand               VARCHAR(64)   DEFAULT NULL            COMMENT '品牌',
    main_image          VARCHAR(512)  DEFAULT NULL            COMMENT '主图（文件ID或URL）',
    images              TEXT          NULL                    COMMENT '轮播图（JSON 数组，元素为文件ID/URL）',
    detail              MEDIUMTEXT    NULL                    COMMENT '图文详情（富文本，图片为引用）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ON_SALE/OFF_SALE',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_spu_code (tenant_id, spu_code),
    KEY idx_mall_spu_category (tenant_id, category_id),
    KEY idx_mall_spu_status (tenant_id, status)
) COMMENT='商城商品（SPU）';

-- ---------------------------------------------------------------------
-- 3. 规格 SKU
--    sku_code 全局唯一（不是 SPU 内唯一）：拣货、对账、售后都按 SKU 编码单点定位，
--    允许两个 SPU 撞码等于埋雷。删除同样由应用层改写编码释放。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_sku (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    spu_id              BIGINT        NOT NULL                COMMENT '所属商品ID',
    sku_code            VARCHAR(64)   NOT NULL                COMMENT 'SKU 编码（租户内唯一）',
    spec_json           VARCHAR(512)  DEFAULT NULL            COMMENT '规格描述（JSON，如 {"颜色":"黑","容量":"128G"}）',
    image_url           VARCHAR(512)  DEFAULT NULL            COMMENT 'SKU 图（文件ID或URL）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_sku_code (tenant_id, sku_code),
    KEY idx_mall_sku_spu (tenant_id, spu_id)
) COMMENT='商城商品规格（SKU）';

-- ---------------------------------------------------------------------
-- 4. 多价目
--    一种价目一行，不用「一列一个价」：加一种价目就要改表 + 改所有 DTO。
--    price_type：RETAIL（零售）/ B2B（经销）/ POINTS（福利积分）。
--    POINTS 价为积分数，应用层强制整数（0.5 积分没有业务含义）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_sku_price (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID',
    price_type          VARCHAR(16)   NOT NULL                COMMENT '价目类型：RETAIL/B2B/POINTS',
    price               DECIMAL(12,2) NOT NULL                COMMENT '价格（元或积分，按价目类型解释）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_sku_price (tenant_id, sku_id, price_type)
) COMMENT='商城 SKU 价目';

-- ---------------------------------------------------------------------
-- 5. 可售库存
--    available_qty 可售 / reserved_qty 已预占（下单未发货）。
--    扣减用条件更新（WHERE available_qty >= ?），不先查后改——并发下必然超卖且不报错。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_sku_stock (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID',
    available_qty       INT           NOT NULL DEFAULT 0      COMMENT '可售数量',
    reserved_qty        INT           NOT NULL DEFAULT 0      COMMENT '已预占数量（下单未发货）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_sku_stock (tenant_id, sku_id)
) COMMENT='商城 SKU 可售库存';

-- ---------------------------------------------------------------------
-- 6. 商城渠道
--    mode：RETAIL（零售）/ B2B（经销订货）/ WELFARE（员工福利）。
--    settlement_type：ONLINE（在线支付）/ CREDIT（账期，M1）/ POINTS（积分扣减，M1）。
--    currency_type：CASH（元）/ POINTS（积分），决定订单金额口径。
--    一套内核服务三种形态，差异全部收敛到这张表的两个枚举上。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_channel (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    channel_code        VARCHAR(64)   NOT NULL                COMMENT '渠道编码（租户内唯一）',
    channel_name        VARCHAR(128)  NOT NULL                COMMENT '渠道名称',
    mode                VARCHAR(16)   NOT NULL                COMMENT '形态：RETAIL/B2B/WELFARE',
    settlement_type     VARCHAR(16)   NOT NULL                COMMENT '结算方式：ONLINE/CREDIT/POINTS',
    currency_type       VARCHAR(16)   NOT NULL                COMMENT '币种口径：CASH/POINTS',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_channel_code (tenant_id, channel_code),
    KEY idx_mall_channel_status (tenant_id, status)
) COMMENT='商城渠道（一套内核三种形态）';

-- ---------------------------------------------------------------------
-- 7. 购物车
--    刻意无 deleted：购物车条目的正确删除语义是物理删除。
--    挂逻辑删除会让 (channel, user, sku) 唯一键在「删了再加」时撞键，
--    把 deleted 编进唯一键也救不了（只有 0/1，删两次照样撞）。
--    价格不落库：加购只存 SKU 与数量，价格在下单试算时实时解析。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_cart_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    channel_id          BIGINT        NOT NULL                COMMENT '渠道ID',
    user_id             BIGINT        NOT NULL                COMMENT '买家用户ID',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID',
    quantity            INT           NOT NULL DEFAULT 1      COMMENT '数量',
    selected            TINYINT       NOT NULL DEFAULT 1      COMMENT '是否选中：1 选中 / 0 未选',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_cart_item (tenant_id, channel_id, user_id, sku_id),
    KEY idx_mall_cart_user (tenant_id, user_id)
) COMMENT='购物车条目';

-- ---------------------------------------------------------------------
-- 8. 订单
--    idempotency_key 落唯一索引（不用 Redis）：Redis 过期后重复提交照样建单，
--    唯一索引永远有效。冲突时返回已有订单，不返回 409——用户双击不该看到错误。
--    currency_type / mode / settlement_type 全部落快照：渠道改配置不影响历史单。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_order (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_no            VARCHAR(32)   NOT NULL                COMMENT '订单号（MO+yyyyMMdd+6位流水）',
    channel_id          BIGINT        NOT NULL                COMMENT '渠道ID',
    channel_code        VARCHAR(64)   NOT NULL                COMMENT '渠道编码（快照）',
    mode                VARCHAR(16)   NOT NULL                COMMENT '形态快照：RETAIL/B2B/WELFARE',
    settlement_type     VARCHAR(16)   NOT NULL                COMMENT '结算方式快照',
    currency_type       VARCHAR(16)   NOT NULL                COMMENT '币种快照：CASH/POINTS',
    buyer_user_id       BIGINT        NOT NULL                COMMENT '买家用户ID',
    buyer_name          VARCHAR(64)   DEFAULT NULL            COMMENT '买家名称（快照）',
    status              VARCHAR(24)   NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '状态：PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED',
    total_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '订单总额（元或积分，按 currency_type）',
    total_quantity      INT           NOT NULL DEFAULT 0      COMMENT '商品总件数',
    item_count          INT           NOT NULL DEFAULT 0      COMMENT '明细行数',
    idempotency_key     VARCHAR(64)   NOT NULL                COMMENT '幂等键（客户端生成）',
    pay_no              VARCHAR(64)   DEFAULT NULL            COMMENT '支付单号（whale-pay 返回）',
    pay_time            DATETIME      DEFAULT NULL            COMMENT '支付时间',
    ship_time           DATETIME      DEFAULT NULL            COMMENT '发货时间',
    complete_time       DATETIME      DEFAULT NULL            COMMENT '完成时间',
    cancel_time         DATETIME      DEFAULT NULL            COMMENT '取消时间',
    cancel_reason       VARCHAR(255)  DEFAULT NULL            COMMENT '取消原因',
    expire_time         DATETIME      DEFAULT NULL            COMMENT '待支付超时时间（M1 由定时任务关闭）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '买家备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_order_no (tenant_id, order_no),
    UNIQUE KEY uk_mall_order_idem (tenant_id, buyer_user_id, idempotency_key),
    KEY idx_mall_order_buyer (tenant_id, buyer_user_id, status),
    KEY idx_mall_order_status (tenant_id, status, create_time)
) COMMENT='商城订单';

-- ---------------------------------------------------------------------
-- 9. 订单明细
--    商品名/规格/图/单价全部存快照：只存 sku_id 的话，商品删除后订单详情只剩一串数字。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_order_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        NOT NULL                COMMENT '订单ID',
    spu_id              BIGINT        NOT NULL                COMMENT '商品ID',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID',
    spu_name            VARCHAR(128)  NOT NULL                COMMENT '商品名称（快照）',
    sku_code            VARCHAR(64)   NOT NULL                COMMENT 'SKU 编码（快照）',
    spec_json           VARCHAR(512)  DEFAULT NULL            COMMENT '规格（快照）',
    image_url           VARCHAR(512)  DEFAULT NULL            COMMENT '图片（快照）',
    price_type          VARCHAR(16)   NOT NULL                COMMENT '成交价目类型：RETAIL/B2B/POINTS',
    unit_price          DECIMAL(12,2) NOT NULL                COMMENT '成交单价（快照）',
    quantity            INT           NOT NULL                COMMENT '数量',
    amount              DECIMAL(12,2) NOT NULL                COMMENT '小计 = 单价 × 数量',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_order_item_order (tenant_id, order_id),
    KEY idx_mall_order_item_sku (tenant_id, sku_id)
) COMMENT='商城订单明细';

-- ---------------------------------------------------------------------
-- 10. 订单状态流水（只追加，不更新不删除）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_order_log (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        NOT NULL                COMMENT '订单ID',
    from_status         VARCHAR(24)   DEFAULT NULL            COMMENT '变更前状态（新建时为空）',
    to_status           VARCHAR(24)   NOT NULL                COMMENT '变更后状态',
    action              VARCHAR(24)   NOT NULL                COMMENT '动作：CREATE/PAY/CANCEL/SHIP/COMPLETE',
    operator_id         BIGINT        DEFAULT NULL            COMMENT '操作人ID（系统动作可空）',
    operator_name       VARCHAR(64)   DEFAULT NULL            COMMENT '操作人名称（快照）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '说明',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_order_log_order (tenant_id, order_id, create_time)
) COMMENT='商城订单状态流水';

-- ---------------------------------------------------------------------
-- 11. 业务编号流水（订单号取号；无 deleted 列，不做逻辑删除）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_sequence (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    seq_key             VARCHAR(64)   NOT NULL                COMMENT '编号键，如 ORDER',
    seq_date            VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '周期标识：空串不重置；yyyyMMdd 按日重置',
    current_seq         BIGINT        NOT NULL DEFAULT 0      COMMENT '当前已发到的最末流水号（只增不减）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_sequence (tenant_id, seq_key, seq_date)
) COMMENT='商城业务编号流水';
