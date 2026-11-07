package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商务订单聚合根。
 *
 * <p>状态机：{@code DRAFT → PENDING_APPROVAL → APPROVED / REJECTED}；
 * 审批通过后由 outbox 幂等下发 ERP，失败可重试；已发货订单不可取消。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesOrder {

    /** 下发失败原因长度上限 */
    private static final int ERROR_MAX_LENGTH = 512;

    /** 主键 */
    private Long id;

    /** 租户 ID（后台任务显式携带，保证下发租户头正确） */
    private Long tenantId;

    /** 商务订单编号 */
    private String orderNo;

    /** 来源类型：CONTRACT / QUOTE / MANUAL */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单据编号快照 */
    private String sourceOrderNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户编码快照 */
    private String customerCode;

    /** 订单标题 */
    private String title;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 状态 */
    private SalesOrderStatus status;

    /** 审批流程实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** ERP 销售订单 ID */
    private Long erpOrderId;

    /** ERP 履约状态 */
    private String erpStatus;

    /** 下发成功时间 */
    private LocalDateTime pushedAt;

    /** 下发尝试次数 */
    private Integer pushAttempts;

    /** 最近下发失败原因 */
    private String lastPushError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 订单行 */
    private List<SalesOrderLine> lines = new ArrayList<>();

    /**
     * 校验当前订单可编辑。
     */
    public void requireEditable() {
        if (status != SalesOrderStatus.DRAFT) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "仅草稿订单允许编辑");
        }
    }

    /**
     * 校验订单可提交审批：至少一行，且每行物料编码、数量与单价合法。
     */
    public void validateForSubmit() {
        if (status != SalesOrderStatus.DRAFT && status != SalesOrderStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "仅草稿或已驳回订单允许提交审批");
        }
        if (lines == null || lines.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID, "商务订单至少需要一行明细");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : lines) {
            line.requirePushable();
            line.setAmount(line.calculateAmount());
            total = total.add(line.getAmount());
        }
        if (total.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID, "商务订单金额必须大于 0");
        }
        this.totalAmount = total;
    }

    /**
     * 提交审批。
     *
     * @param instanceId 流程实例 ID
     * @param at 提交时间
     */
    public void submit(Long instanceId, LocalDateTime at) {
        if (status != SalesOrderStatus.DRAFT && status != SalesOrderStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "仅草稿或已驳回订单允许提交审批");
        }
        this.status = SalesOrderStatus.PENDING_APPROVAL;
        this.approvalInstanceId = instanceId;
        this.submittedAt = at;
        this.lastPushError = null;
    }

    /**
     * 应用审批结果。
     *
     * @param approved 是否通过
     * @param at 决策时间
     */
    public void applyApproval(boolean approved, LocalDateTime at) {
        if (status != SalesOrderStatus.PENDING_APPROVAL) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_APPROVAL_STATE_CONFLICT);
        }
        this.status = approved ? SalesOrderStatus.APPROVED : SalesOrderStatus.REJECTED;
        if (approved) {
            this.approvedAt = at;
        }
    }

    /**
     * 进入下发中状态。
     */
    public void beginPush() {
        if (status != SalesOrderStatus.APPROVED && status != SalesOrderStatus.PUSH_FAILED) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "仅审批通过或下发失败订单允许下发");
        }
        this.status = SalesOrderStatus.PUSHING;
    }

    /**
     * 标记下发成功。
     *
     * @param erpOrderNo ERP 销售订单号
     * @param erpOrderId ERP 销售订单 ID
     * @param erpStatus ERP 履约状态
     * @param at 下发时间
     */
    public void markPushed(String erpOrderNo, Long erpOrderId, String erpStatus, LocalDateTime at) {
        this.status = SalesOrderStatus.PUSHED;
        this.erpOrderNo = erpOrderNo;
        this.erpOrderId = erpOrderId;
        this.erpStatus = erpStatus;
        this.pushedAt = at;
        this.pushAttempts = nextAttempts();
        this.lastPushError = null;
    }

    /**
     * 对账采纳 ERP 已存在的订单（防止下发超时导致的重复建单）。
     *
     * @param erpOrderNo ERP 销售订单号
     * @param erpOrderId ERP 销售订单 ID
     * @param erpStatus ERP 履约状态
     * @param at 对账时间
     */
    public void adoptErpOrder(String erpOrderNo, Long erpOrderId, String erpStatus, LocalDateTime at) {
        if (erpOrderNo == null || erpOrderNo.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_PUSH_FAILED, "ERP 未返回销售订单号");
        }
        this.status = SalesOrderStatus.PUSHED;
        this.erpOrderNo = erpOrderNo;
        this.erpOrderId = erpOrderId;
        this.erpStatus = erpStatus;
        this.pushedAt = at;
        this.lastPushError = null;
    }

    /**
     * 标记下发失败。
     *
     * @param error 失败原因
     */
    public void markPushFailed(String error) {
        this.status = SalesOrderStatus.PUSH_FAILED;
        this.pushAttempts = nextAttempts();
        String message = error == null ? "下发 ERP 失败" : error.trim();
        this.lastPushError = message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }

    /**
     * 刷新 ERP 履约状态（对账回读）。
     *
     * @param erpStatus ERP 履约状态
     * @param erpOrderNo ERP 销售订单号
     * @param erpOrderId ERP 销售订单 ID
     */
    public void refreshErpStatus(String erpStatus, String erpOrderNo, Long erpOrderId) {
        this.erpStatus = erpStatus;
        this.erpOrderNo = erpOrderNo;
        this.erpOrderId = erpOrderId;
    }

    /**
     * 取消订单（下发中不可取消，已发货由 ERP 拒绝）。
     */
    public void cancel() {
        if (status == SalesOrderStatus.PUSHING) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_STATE_CONFLICT, "订单正在下发 ERP，请稍后重试");
        }
        if (status == SalesOrderStatus.CANCELLED) {
            return;
        }
        this.status = SalesOrderStatus.CANCELLED;
    }

    /**
     * 计算下一次下发尝试次数。
     *
     * @return 尝试次数
     */
    private int nextAttempts() {
        return (pushAttempts == null ? 0 : pushAttempts) + 1;
    }
}
