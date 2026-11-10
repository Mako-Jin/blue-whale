package com.bluewhale.oa.expense.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报销单领域模型。
 *
 * <p>合计金额始终由服务端按明细重算；状态机 {@code PENDING → APPROVED / REJECTED / CANCELLED}。</p>
 *
 * @author BlueWhale
 */
@Data
public class Expense {

    /** 主键ID */
    private Long id;

    /** 报销单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 主费用类别编码 */
    private String categoryCode;

    /** 报销事由标题 */
    private String title;

    /** 合计金额 */
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    /** 状态 */
    private ExpenseStatus status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** ERP 推送状态 */
    private ErpPushStatus erpPushStatus;

    /** ERP 应付单号 */
    private String erpPayableNo;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 明细 */
    private List<ExpenseItem> items = new ArrayList<>();

    /** 发票 */
    private List<ExpenseInvoice> invoices = new ArrayList<>();

    /**
     * 校验单据处于可编辑状态。
     */
    public void ensureEditable() {
        if (status != ExpenseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "仅待审批的报销单可编辑或删除");
        }
    }

    /**
     * 变更状态，仅允许 {@code PENDING} 转向终态。
     *
     * @param target 目标状态
     * @return 状态是否发生变化
     */
    public boolean changeStatus(ExpenseStatus target) {
        if (status == target) {
            return false;
        }
        if (status != ExpenseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "报销单已处于终态，不可再次变更");
        }
        if (target == ExpenseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_STATUS_CONFLICT, "报销单不能回到待审批状态");
        }
        this.status = target;
        return true;
    }
}
