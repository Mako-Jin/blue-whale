package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.InvoiceItem;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票申请视图。
 *
 * @author BlueWhale
 */
@Data
public class InvoiceView {

    /** 申请 ID */
    private Long id;

    /** 申请单号 */
    private String invoiceNo;

    /** 申请类型 */
    private String requestType;

    /** 红冲原申请 ID */
    private Long originInvoiceId;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceOrderNo;

    /** 合同台账 ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 币种 */
    private String currency;

    /** 申请金额 */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 发票类型 */
    private String invoiceType;

    /** 抬头类型 */
    private String titleType;

    /** 发票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号（脱敏） */
    private String taxNo;

    /** 状态 */
    private String status;

    /** 审批实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 发票代码 */
    private String invoiceCode;

    /** 发票号码 */
    private String invoiceNumber;

    /** 开票时间 */
    private LocalDateTime issuedAt;

    /** 实际开票金额 */
    private BigDecimal issuedAmount;

    /** 快递公司 */
    private String courier;

    /** 运单号 */
    private String trackingNo;

    /** 寄送时间 */
    private LocalDateTime sentAt;

    /** 红冲原因 */
    private String redFlushReason;

    /** 红冲时间 */
    private LocalDateTime redFlushedAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 合同 / 订单可开票余额（仅详情返回） */
    private BigDecimal availableAmount;

    /** 申请行 */
    private List<ItemView> items;

    /**
     * 由聚合构建视图。
     *
     * @param request 申请聚合
     * @return 视图
     */
    public static InvoiceView from(InvoiceRequest request) {
        InvoiceView view = new InvoiceView();
        view.setId(request.getId());
        view.setInvoiceNo(request.getInvoiceNo());
        view.setRequestType(request.getRequestType() == null ? null : request.getRequestType().name());
        view.setOriginInvoiceId(request.getOriginInvoiceId());
        view.setSourceType(request.getSourceType());
        view.setSourceId(request.getSourceId());
        view.setSourceOrderNo(request.getSourceOrderNo());
        view.setContractId(request.getContractId());
        view.setContractNo(request.getContractNo());
        view.setCustomerId(request.getCustomerId());
        view.setCustomerCode(request.getCustomerCode());
        view.setCustomerName(request.getCustomerName());
        view.setCurrency(request.getCurrency());
        view.setAmount(request.getAmount());
        view.setTaxAmount(request.getTaxAmount());
        view.setInvoiceType(request.getInvoiceType());
        view.setTitleType(request.getTitleType());
        view.setInvoiceTitle(request.getInvoiceTitle());
        view.setTaxNo(maskTaxNo(request.getTaxNo()));
        view.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        view.setApprovalInstanceId(request.getApprovalInstanceId());
        view.setSubmittedAt(request.getSubmittedAt());
        view.setApprovedAt(request.getApprovedAt());
        view.setInvoiceCode(request.getInvoiceCode());
        view.setInvoiceNumber(request.getInvoiceNumber());
        view.setIssuedAt(request.getIssuedAt());
        view.setIssuedAmount(request.getIssuedAmount());
        view.setCourier(request.getCourier());
        view.setTrackingNo(request.getTrackingNo());
        view.setSentAt(request.getSentAt());
        view.setRedFlushReason(request.getRedFlushReason());
        view.setRedFlushedAt(request.getRedFlushedAt());
        view.setOwnerId(request.getOwnerId());
        view.setDeptId(request.getDeptId());
        view.setRemark(request.getRemark());
        if (request.getItems() != null) {
            view.setItems(request.getItems().stream().map(ItemView::from).toList());
        }
        return view;
    }

    /**
     * 税号脱敏：保留首 4 位与末 2 位。
     *
     * @param taxNo 税号
     * @return 脱敏税号
     */
    private static String maskTaxNo(String taxNo) {
        if (taxNo == null || taxNo.length() <= 6) {
            return taxNo;
        }
        return taxNo.substring(0, 4) + "****" + taxNo.substring(taxNo.length() - 2);
    }

    /**
     * 申请行视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class ItemView {

        /** 行 ID */
        private Long id;

        /** 行号 */
        private Integer lineNo;

        /** 项目名称 */
        private String itemName;

        /** 规格 */
        private String specification;

        /** 数量 */
        private BigDecimal quantity;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 税率 */
        private BigDecimal taxRate;

        /** 行税额 */
        private BigDecimal taxAmount;

        /** 行金额 */
        private BigDecimal amount;

        /** 行备注 */
        private String remark;

        /**
         * 由行构建视图。
         *
         * @param item 申请行
         * @return 行视图
         */
        public static ItemView from(InvoiceItem item) {
            ItemView view = new ItemView();
            view.setId(item.getId());
            view.setLineNo(item.getLineNo());
            view.setItemName(item.getItemName());
            view.setSpecification(item.getSpecification());
            view.setQuantity(item.getQuantity());
            view.setUnitPrice(item.getUnitPrice());
            view.setTaxRate(item.getTaxRate());
            view.setTaxAmount(item.getTaxAmount());
            view.setAmount(item.getAmount());
            view.setRemark(item.getRemark());
            return view;
        }
    }
}
