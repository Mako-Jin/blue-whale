-- =====================================================================
-- 商城售后域增强：退货物流闭环、举证图片校验、超时自动关闭、换货（whale-mall 侧）
-- ---------------------------------------------------------------------
-- 本批次只扩枚举值，不改表结构（status / type 均为 VARCHAR）：
--   1) mall_after_sale.type 新增 EXCHANGE（换货：买家退回原货、运营补发新货、不退钱）。
--      换货行金额记 0、不占退款额度、不动 refunded_quantity/amount，
--      防超占改用「同一订单行存在进行中换货单（CREATED/WAIT_RETURN/EXCHANGING）查重」。
--   2) mall_after_sale.status 新增 WAIT_RETURN（待退货）/ EXCHANGING（换货补发中）
--      / EXCHANGED（已换货，终态）。状态机口径：
--        REFUND_ONLY   : CREATED → APPROVED → REFUNDED（审核即退款，原路径不变）
--        RETURN_REFUND : CREATED → WAIT_RETURN →（买家回填退货物流）→ confirmReturn
--                        → APPROVED → REFUNDED（确认收货后才发起退款）
--        EXCHANGE      : CREATED → WAIT_RETURN → confirmReturn → EXCHANGING
--                        → 补发发货单（复用 ShipmentAppService.create，占用行可发
--                        数量 shipped_quantity 条件更新，语义不变）→ EXCHANGED
--        旁支：reject 放宽为 CREATED|WAIT_RETURN（还退款额度；换货单无额度可还）；
--              cancel 仅 CREATED（换货单在 WAIT_RETURN 也允许买家撤销，无额度可还）；
--              超时自动关闭只扫 CREATED（WAIT_RETURN/EXCHANGING 买家可能已寄回，
--              不自动关）。
--   3) 举证图片：服务端校验最多 6 张、单张 URL ≤500 字符、去重去空，落 images 逗号分隔。
--
-- 存量在途单兼容口径（容忍处理，不迁移数据）：
--   - 上线前 RETURN_REFUND 已 APPROVED 的在途单（旧逻辑审核即退款）：markRefunded
--     仍只认 APPROVED → REFUNDED，照常结单，无需人工迁移。
--   - approve 后 return_logistics_no 允许为空（旧单买家后补），回填物流只对
--     WAIT_RETURN 开放；APPROVED 老单不可回填，走原退款路径收尾。
--   - 换货补发复用发货链路：订单须处于可发货状态（PAID/SHIPPED）且行有可发数量；
--     已发满的行补发会被可发数量条件更新挡住，需先处理原发货单。
--   - ERP/WMS 退货入库联动依赖外部契约，另行批次。
-- =====================================================================

-- 结构无变更，仅刷新列注释使枚举口径与代码一致（重复执行无副作用）
ALTER TABLE mall_after_sale
    MODIFY COLUMN type VARCHAR(16) NOT NULL
        COMMENT '类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 / EXCHANGE 换货';
ALTER TABLE mall_after_sale
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CREATED'
        COMMENT '状态：CREATED 待审核 / WAIT_RETURN 待退货 / APPROVED 审核通过（退款执行中） / REFUNDED 已退款 / EXCHANGING 换货补发中 / EXCHANGED 已换货 / REJECTED 已驳回 / CANCELLED 已撤销';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM mall_after_sale LIKE 'type';
--   SHOW COLUMNS FROM mall_after_sale LIKE 'status';
-- =====================================================================
