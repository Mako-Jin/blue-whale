package com.bluewhale.erp.hr.domain.model;

/**
 * 人力模块状态、异动类型与凭证来源类型常量。
 *
 * <p>集中定义避免各聚合重复声明字符串字面量。</p>
 *
 * @author BlueWhale
 */
public final class HrStatus {

    /** 组织启用 */
    public static final String ORG_ENABLED = "ENABLED";

    /** 组织停用 */
    public static final String ORG_DISABLED = "DISABLED";

    /** 员工在职 */
    public static final String EMPLOYEE_ACTIVE = "ACTIVE";

    /** 员工离职 */
    public static final String EMPLOYEE_LEFT = "LEFT";

    /** 异动草稿 */
    public static final String CHANGE_DRAFT = "DRAFT";

    /** 异动生效 */
    public static final String CHANGE_EFFECTIVE = "EFFECTIVE";

    /** 异动取消 */
    public static final String CHANGE_CANCELLED = "CANCELLED";

    /** 入职 */
    public static final String CHANGE_HIRE = "HIRE";

    /** 调动 */
    public static final String CHANGE_TRANSFER = "TRANSFER";

    /** 晋升 */
    public static final String CHANGE_PROMOTION = "PROMOTION";

    /** 离职 */
    public static final String CHANGE_RESIGN = "RESIGN";

    /** 返聘/复职 */
    public static final String CHANGE_RETURN = "RETURN";

    /** 薪资批次草稿 */
    public static final String PAYROLL_DRAFT = "DRAFT";

    /** 薪资已计算 */
    public static final String PAYROLL_CALCULATED = "CALCULATED";

    /** 薪资已审批 */
    public static final String PAYROLL_APPROVED = "APPROVED";

    /** 薪资已发放 */
    public static final String PAYROLL_PAID = "PAID";

    /** 薪资已结账 */
    public static final String PAYROLL_CLOSED = "CLOSED";

    /** 薪资取消 */
    public static final String PAYROLL_CANCELLED = "CANCELLED";

    /** 绩效周期草稿 */
    public static final String CYCLE_DRAFT = "DRAFT";

    /** 绩效周期进行中 */
    public static final String CYCLE_IN_PROGRESS = "IN_PROGRESS";

    /** 绩效周期已关闭 */
    public static final String CYCLE_CLOSED = "CLOSED";

    /** 薪资计提凭证来源 */
    public static final String SOURCE_PAYROLL_ACCRUAL = "PAYROLL_ACCRUAL";

    /** 薪资发放凭证来源 */
    public static final String SOURCE_PAYROLL_PAYMENT = "PAYROLL_PAYMENT";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private HrStatus() {
    }
}
