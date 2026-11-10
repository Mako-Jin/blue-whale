package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 退换货申请聚合。
 *
 * <p>换货字段本期仅预留，执行链路只处理退货。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequest {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 申请单号（RET-yyyyMMdd-xxxx） */
    private String returnNo;

    /** 申请类型：RETURN / EXCHANGE */
    private String requestType;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码快照 */
    private String customerCode;

    /** 客户名称快照 */
    private String customerName;

    /** 退货原因 */
    private String reason;

    /** 申请状态 */
    private ReturnStatus status;

    /** 审批流程实例 ID */
    private Long approvalInstanceId;

    /** ERP 退货单号 */
    private String erpReturnNo;

    /** ERP 退货单状态快照 */
    private String erpStatus;

    /** 下发 ERP 尝试次数 */
    private Integer pushAttempts;

    /** 最近一次下发失败原因 */
    private String lastError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 申请行（聚合内） */
    private List<ReturnRequestItem> items = new ArrayList<>();

    /**
     * 校验当前状态允许编辑。
     */
    public void requireEditable() {
        if (status != ReturnStatus.DRAFT && status != ReturnStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                    "仅草稿或已驳回的退换货申请允许编辑");
        }
    }

    /**
     * 提交审批。
     *
     * @param instanceId 流程实例 ID
     */
    public void submit(Long instanceId) {
        requireEditable();
        this.approvalInstanceId = instanceId;
        this.status = ReturnStatus.PENDING_APPROVAL;
    }

    /**
     * 应用审批结果。
     *
     * @param approved 是否通过
     */
    public void applyApproval(boolean approved) {
        if (status != ReturnStatus.PENDING_APPROVAL) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                    "退换货申请不在审批中，不能应用审批结果");
        }
        this.status = approved ? ReturnStatus.APPROVED : ReturnStatus.REJECTED;
    }

    /**
     * 进入执行中并累计下发次数。
     */
    public void beginExecute() {
        if (!isExecutable()) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                    "当前状态不允许下发 ERP 退货");
        }
        this.status = ReturnStatus.EXECUTING;
        this.pushAttempts = (pushAttempts == null ? 0 : pushAttempts) + 1;
        this.lastError = null;
    }

    /**
     * 标记 ERP 退货过账完成。
     *
     * @param erpReturnNo ERP 退货单号
     * @param erpStatus ERP 退货单状态
     */
    public void markCompleted(String erpReturnNo, String erpStatus) {
        this.status = ReturnStatus.COMPLETED;
        this.erpReturnNo = erpReturnNo;
        this.erpStatus = erpStatus;
        this.lastError = null;
    }

    /**
     * 标记 ERP 退货执行失败。
     *
     * @param error 失败原因
     */
    public void markFailed(String error) {
        this.status = ReturnStatus.FAILED;
        this.lastError = error;
    }

    /**
     * 刷新 ERP 退货状态快照。
     *
     * @param erpStatus ERP 状态
     * @param erpReturnNo ERP 退货单号
     */
    public void refreshErpStatus(String erpStatus, String erpReturnNo) {
        this.erpStatus = erpStatus;
        if (erpReturnNo != null) {
            this.erpReturnNo = erpReturnNo;
        }
    }

    /**
     * 取消申请。
     */
    public void cancel() {
        if (status != ReturnStatus.DRAFT && status != ReturnStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.RETURN_STATE_CONFLICT,
                    "仅草稿或已驳回的退换货申请允许取消");
        }
        this.status = ReturnStatus.CANCELLED;
    }

    /**
     * 判断当前状态是否允许下发 ERP。
     *
     * @return 允许下发返回 true
     */
    public boolean isExecutable() {
        return status == ReturnStatus.APPROVED || status == ReturnStatus.EXECUTING
                || status == ReturnStatus.FAILED;
    }

    /**
     * 判断是否已审批通过（审批实例时间口径）。
     *
     * @return 已通过返回 true
     */
    public boolean isApproved() {
        return status == ReturnStatus.APPROVED || status == ReturnStatus.EXECUTING
                || status == ReturnStatus.COMPLETED || status == ReturnStatus.FAILED;
    }

    /**
     * 判断是否终态。
     *
     * @return 终态返回 true
     */
    public boolean isFinished() {
        return status == ReturnStatus.COMPLETED || status == ReturnStatus.CANCELLED;
    }
}
