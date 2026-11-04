package com.bluewhale.erp.mfg.domain.model;

/**
 * 生产计划状态与来源类型常量。
 *
 * @author BlueWhale
 */
public final class MfgStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";

    /** 已发布（需求预测） */
    public static final String PUBLISHED = "PUBLISHED";

    /** 已下达（主生产计划 / 生产订单） */
    public static final String RELEASED = "RELEASED";

    /** 生产中 */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /** 已完工 */
    public static final String COMPLETED = "COMPLETED";

    /** 已结案 */
    public static final String CLOSED = "CLOSED";

    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    /** 来源：生产领料 */
    public static final String SOURCE_PRODUCTION_ISSUE = "PRODUCTION_ISSUE";

    /** 来源：生产完工入库 */
    public static final String SOURCE_PRODUCTION_RECEIPT = "PRODUCTION_RECEIPT";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private MfgStatus() {
    }
}
