package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnRequestItem;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 退换货申请视图。
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequestView {

    /** 主键 */
    private Long id;

    /** 申请单号 */
    private String returnNo;

    /** 申请类型 */
    private String requestType;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 退货原因 */
    private String reason;

    /** 申请状态 */
    private String status;

    /** 审批实例 ID */
    private Long approvalInstanceId;

    /** ERP 退货单号 */
    private String erpReturnNo;

    /** ERP 退货单状态 */
    private String erpStatus;

    /** 下发尝试次数 */
    private Integer pushAttempts;

    /** 最近失败原因 */
    private String lastError;

    /** 退货总额 */
    private BigDecimal totalAmount;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 申请行 */
    private List<Item> items;

    /**
     * 领域对象转视图。
     *
     * @param request 申请
     * @param items 申请行
     * @return 视图
     */
    public static ReturnRequestView from(ReturnRequest request, List<ReturnRequestItem> items) {
        ReturnRequestView view = new ReturnRequestView();
        view.setId(request.getId());
        view.setReturnNo(request.getReturnNo());
        view.setRequestType(request.getRequestType());
        view.setOrderId(request.getOrderId());
        view.setErpOrderNo(request.getErpOrderNo());
        view.setCustomerId(request.getCustomerId());
        view.setCustomerCode(request.getCustomerCode());
        view.setCustomerName(request.getCustomerName());
        view.setReason(request.getReason());
        view.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        view.setApprovalInstanceId(request.getApprovalInstanceId());
        view.setErpReturnNo(request.getErpReturnNo());
        view.setErpStatus(request.getErpStatus());
        view.setPushAttempts(request.getPushAttempts());
        view.setLastError(request.getLastError());
        view.setOwnerId(request.getOwnerId());
        view.setDeptId(request.getDeptId());
        List<Item> rows = items == null ? List.of() : items.stream().map(Item::from).toList();
        view.setItems(rows);
        view.setTotalAmount(rows.stream().map(Item::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return view;
    }

    /**
     * 申请行视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class Item {

        /** 行号 */
        private Integer lineNo;

        /** 来源订单行号 */
        private Integer orderLineNo;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 退货数量 */
        private BigDecimal quantity;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 行金额 */
        private BigDecimal amount;

        /**
         * 领域对象转行视图。
         *
         * @param item 申请行
         * @return 行视图
         */
        public static Item from(ReturnRequestItem item) {
            Item view = new Item();
            view.setLineNo(item.getLineNo());
            view.setOrderLineNo(item.getOrderLineNo());
            view.setItemCode(item.getItemCode());
            view.setItemName(item.getItemName());
            view.setQuantity(item.getQuantity());
            view.setUnitPrice(item.getUnitPrice());
            view.setAmount(item.getAmount());
            return view;
        }
    }
}
