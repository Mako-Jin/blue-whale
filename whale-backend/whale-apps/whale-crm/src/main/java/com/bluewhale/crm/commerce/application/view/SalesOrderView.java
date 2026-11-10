package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商务订单视图。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderView {

    /** 订单 ID */
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceOrderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 订单标题 */
    private String title;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 状态 */
    private String status;

    /** 审批实例 ID */
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

    /** 最近失败原因 */
    private String lastPushError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 订单行 */
    private List<LineView> lines;

    /**
     * 由聚合构建视图。
     *
     * @param order 订单聚合
     * @return 视图
     */
    public static SalesOrderView from(SalesOrder order) {
        SalesOrderView view = new SalesOrderView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setSourceType(order.getSourceType());
        view.setSourceId(order.getSourceId());
        view.setSourceOrderNo(order.getSourceOrderNo());
        view.setCustomerId(order.getCustomerId());
        view.setCustomerCode(order.getCustomerCode());
        view.setTitle(order.getTitle());
        view.setCurrency(order.getCurrency());
        view.setTotalAmount(order.getTotalAmount());
        view.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        view.setApprovalInstanceId(order.getApprovalInstanceId());
        view.setSubmittedAt(order.getSubmittedAt());
        view.setApprovedAt(order.getApprovedAt());
        view.setErpOrderNo(order.getErpOrderNo());
        view.setErpOrderId(order.getErpOrderId());
        view.setErpStatus(order.getErpStatus());
        view.setPushedAt(order.getPushedAt());
        view.setPushAttempts(order.getPushAttempts());
        view.setLastPushError(order.getLastPushError());
        view.setOwnerId(order.getOwnerId());
        view.setDeptId(order.getDeptId());
        view.setRemark(order.getRemark());
        if (order.getLines() != null) {
            view.setLines(order.getLines().stream().map(LineView::from).toList());
        }
        return view;
    }

    /**
     * 订单行视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class LineView {

        /** 行 ID */
        private Long id;

        /** 行号 */
        private Integer lineNo;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 规格 */
        private String specification;

        /** 计量单位 */
        private String uom;

        /** 数量 */
        private BigDecimal quantity;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 行金额 */
        private BigDecimal amount;

        /** 行备注 */
        private String remark;

        /**
         * 由行构建视图。
         *
         * @param line 订单行
         * @return 行视图
         */
        public static LineView from(SalesOrderLine line) {
            LineView view = new LineView();
            view.setId(line.getId());
            view.setLineNo(line.getLineNo());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setSpecification(line.getSpecification());
            view.setUom(line.getUom());
            view.setQuantity(line.getQuantity());
            view.setUnitPrice(line.getUnitPrice());
            view.setAmount(line.getAmount());
            view.setRemark(line.getRemark());
            return view;
        }
    }
}
