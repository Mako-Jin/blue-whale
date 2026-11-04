package com.bluewhale.erp.qc.domain.model;

/**
 * 质量与设备模块状态、类型与处置方式常量。
 *
 * <p>集中定义避免各聚合重复声明字符串字面量，并作为状态机校验的唯一来源。</p>
 *
 * @author BlueWhale
 */
public final class QcStatus {

    /** 检验单草稿 */
    public static final String INSPECTION_DRAFT = "DRAFT";

    /** 检验中 */
    public static final String INSPECTION_INSPECTING = "INSPECTING";

    /** 检验合格 */
    public static final String INSPECTION_PASSED = "PASSED";

    /** 检验不合格 */
    public static final String INSPECTION_FAILED = "FAILED";

    /** 检验单取消 */
    public static final String INSPECTION_CANCELLED = "CANCELLED";

    /** 检验项合格 */
    public static final String RESULT_PASS = "PASS";

    /** 检验项不合格 */
    public static final String RESULT_FAIL = "FAIL";

    /** 来料检验 */
    public static final String TYPE_IQC = "IQC";

    /** 过程检验 */
    public static final String TYPE_IPQC = "IPQC";

    /** 成品检验 */
    public static final String TYPE_FQC = "FQC";

    /** 来源：采购收货 */
    public static final String SOURCE_PURCHASE_RECEIPT = "PURCHASE_RECEIPT";

    /** 来源：生产订单 */
    public static final String SOURCE_PRODUCTION_ORDER = "PRODUCTION_ORDER";

    /** 来源：手工建单 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 来源：检验单 */
    public static final String SOURCE_INSPECTION = "INSPECTION";

    /** 来源：不合格品单 */
    public static final String SOURCE_NCR = "NCR";

    /** 不合格品单待处置 */
    public static final String NCR_OPEN = "OPEN";

    /** 不合格品单已处置 */
    public static final String NCR_DISPOSED = "DISPOSED";

    /** 不合格品单已关闭 */
    public static final String NCR_CLOSED = "CLOSED";

    /** 处置：返工 */
    public static final String DISPOSITION_REWORK = "REWORK";

    /** 处置：让步接收 */
    public static final String DISPOSITION_CONCESSION = "CONCESSION";

    /** 处置：报废 */
    public static final String DISPOSITION_SCRAP = "SCRAP";

    /** 处置：退货 */
    public static final String DISPOSITION_RETURN = "RETURN";

    /** 质量成本：返工 */
    public static final String COST_REWORK = "REWORK";

    /** 质量成本：报废 */
    public static final String COST_SCRAP = "SCRAP";

    /** 质量成本：检验 */
    public static final String COST_INSPECTION = "INSPECTION";

    /** 设备运行中 */
    public static final String EQUIPMENT_RUNNING = "RUNNING";

    /** 设备闲置 */
    public static final String EQUIPMENT_IDLE = "IDLE";

    /** 设备故障停机 */
    public static final String EQUIPMENT_DOWN = "DOWN";

    /** 设备退役 */
    public static final String EQUIPMENT_RETIRED = "RETIRED";

    /** 计划/量具启用 */
    public static final String ENABLED = "ACTIVE";

    /** 计划/量具停用 */
    public static final String DISABLED = "DISABLED";

    /** 保养工单待执行 */
    public static final String MAINTENANCE_OPEN = "OPEN";

    /** 保养工单执行中 */
    public static final String MAINTENANCE_IN_PROGRESS = "IN_PROGRESS";

    /** 保养工单已完成 */
    public static final String MAINTENANCE_COMPLETED = "COMPLETED";

    /** 保养工单取消 */
    public static final String MAINTENANCE_CANCELLED = "CANCELLED";

    /** 预防性保养 */
    public static final String MAINTENANCE_PREVENTIVE = "PREVENTIVE";

    /** 故障性保养 */
    public static final String MAINTENANCE_CORRECTIVE = "CORRECTIVE";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private QcStatus() {
    }
}
