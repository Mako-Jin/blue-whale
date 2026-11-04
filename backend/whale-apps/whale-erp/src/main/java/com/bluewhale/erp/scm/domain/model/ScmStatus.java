package com.bluewhale.erp.scm.domain.model;

/**
 * 供应链单据状态与库存方向常量。
 *
 * @author BlueWhale
 */
public final class ScmStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";

    /** 已审核（可收货/可发货） */
    public static final String APPROVED = "APPROVED";

    /** 客户已接受（销售报价） */
    public static final String ACCEPTED = "ACCEPTED";

    /** 已转单（申请/报价生成下游订单） */
    public static final String CONVERTED = "CONVERTED";

    /** 部分收货 */
    public static final String PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";

    /** 全部收货 */
    public static final String RECEIVED = "RECEIVED";

    /** 部分发货 */
    public static final String PARTIALLY_SHIPPED = "PARTIALLY_SHIPPED";

    /** 全部发货 */
    public static final String SHIPPED = "SHIPPED";

    /** 已关闭 */
    public static final String CLOSED = "CLOSED";

    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    /** 已过账（调拨、盘点执行完成） */
    public static final String POSTED = "POSTED";

    /** 入库 */
    public static final String IN = "IN";

    /** 出库 */
    public static final String OUT = "OUT";

    /** 来源：采购收货 */
    public static final String SOURCE_GOODS_RECEIPT = "GOODS_RECEIPT";

    /** 来源：销售发货 */
    public static final String SOURCE_SHIPMENT = "SHIPMENT";

    /** 来源：库存调整 */
    public static final String SOURCE_STOCK_ADJUST = "STOCK_ADJUST";

    /** 来源：库存调拨 */
    public static final String SOURCE_TRANSFER = "TRANSFER";

    /** 来源：库存盘点 */
    public static final String SOURCE_STOCKTAKE = "STOCKTAKE";

    /** 来源：采购退货 */
    public static final String SOURCE_PURCHASE_RETURN = "PURCHASE_RETURN";

    /** 来源：销售退货 */
    public static final String SOURCE_SALES_RETURN = "SALES_RETURN";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private ScmStatus() {
    }
}
