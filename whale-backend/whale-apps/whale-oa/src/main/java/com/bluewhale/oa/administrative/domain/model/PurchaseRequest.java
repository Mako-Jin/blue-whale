package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购申请聚合根。
 *
 * <p>合计金额由服务端按明细重算；审批通过后推送 ERP 采购订单草稿。</p>
 *
 * @author BlueWhale
 */
@Data
public class PurchaseRequest {

    /** 主键ID */
    private Long id;

    /** 采购申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 采购事由 */
    private String reason;

    /** 期望到货日期 */
    private LocalDate expectedDate;

    /** 供应商ID（ERP 主数据） */
    private Long supplierId;

    /** 收货仓库ID（ERP 主数据） */
    private Long warehouseId;

    /** 合计金额 */
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** ERP 推送状态 */
    private String erpPushStatus;

    /** ERP 采购订单号 */
    private String erpPoNo;

    /** 备注 */
    private String remark;

    /** 采购明细 */
    private List<PurchaseItem> items = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 汇总明细金额（服务端重算，不接受客户端合计）。
     */
    public void calculateTotal() {
        if (items == null || items.isEmpty()) {
            throw com.bluewhale.oa.common.OaErrorCode.exception(
                    com.bluewhale.oa.common.OaErrorCode.PURCHASE_INVALID, "采购明细不能为空");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItem item : items) {
            item.calculateAmount();
            total = total.add(item.getAmount());
        }
        this.totalAmount = total;
    }

    /**
     * 变更状态：终态不可回流，重复终态幂等返回 false。
     *
     * @param target 目标状态
     * @return 是否发生变更
     */
    public boolean changeStatus(PurchaseStatus target) {
        PurchaseStatus current = PurchaseStatus.of(status);
        if (current == target) {
            return false;
        }
        if (current != PurchaseStatus.PENDING) {
            throw com.bluewhale.oa.common.OaErrorCode.exception(
                    com.bluewhale.oa.common.OaErrorCode.PURCHASE_STATUS_CONFLICT, "采购申请已处于终态，不可再次变更");
        }
        if (target == PurchaseStatus.PENDING) {
            throw com.bluewhale.oa.common.OaErrorCode.exception(
                    com.bluewhale.oa.common.OaErrorCode.PURCHASE_STATUS_CONFLICT, "采购申请不能回到待审批状态");
        }
        this.status = target.name();
        return true;
    }

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (PurchaseStatus.of(status) != PurchaseStatus.PENDING) {
            throw com.bluewhale.oa.common.OaErrorCode.exception(
                    com.bluewhale.oa.common.OaErrorCode.PURCHASE_STATUS_CONFLICT, "仅待审批的采购申请可编辑");
        }
    }
}
