package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 开票申请聚合根。
 *
 * <p>CRM 只维护申请、审批状态、开票记录与寄送跟踪；票面记账、税额与实收核销归 ERP。
 * 正向申请金额必须大于 0 且不超过合同 / 订单可开票余额；红冲单据金额为负数，
 * 且累计红冲不得超过原单已开票金额。</p>
 *
 * @author BlueWhale
 */
@Data
public class InvoiceRequest {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 开票申请单号 */
    private String invoiceNo;

    /** 申请类型 */
    private InvoiceRequestType requestType;

    /** 红冲原申请 ID */
    private Long originInvoiceId;

    /** 来源类型：CONTRACT / ORDER */
    private String sourceType;

    /** 来源单据 ID（合同台账 ID 或商务订单 ID） */
    private Long sourceId;

    /** 来源单据编号快照 */
    private String sourceOrderNo;

    /** 合同台账 ID */
    private Long contractId;

    /** 合同编号快照 */
    private String contractNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户编码快照 */
    private String customerCode;

    /** 客户名称快照 */
    private String customerName;

    /** 币种 */
    private String currency;

    /** 申请含税金额（红冲为负数） */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 发票类型 */
    private String invoiceType;

    /** 抬头类型 */
    private String titleType;

    /** 发票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxNo;

    /** 状态 */
    private InvoiceStatus status;

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

    /** 红冲完成时间 */
    private LocalDateTime redFlushedAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 申请行 */
    private List<InvoiceItem> items = new ArrayList<>();

    /**
     * 校验当前申请可编辑。
     */
    public void requireEditable() {
        if (status != InvoiceStatus.DRAFT) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅草稿开票申请允许编辑");
        }
    }

    /**
     * 校验可提交审批：金额大于 0；有明细时行金额合计必须等于表头金额。
     */
    public void validateForSubmit() {
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅草稿或已驳回申请允许提交审批");
        }
        if (requestType != InvoiceRequestType.NORMAL) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "红冲单据无需提交审批");
        }
        if (amount == null || amount.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "开票申请金额必须大于 0");
        }
        validateItemTotal();
    }

    /**
     * 校验明细金额合计。
     */
    public void validateItemTotal() {
        if (items == null || items.isEmpty()) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (InvoiceItem item : items) {
            item.setLineNo(lineNo++);
            item.setAmount(item.calculateAmount());
            total = total.add(item.getAmount());
        }
        BigDecimal rounded = total.setScale(AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
        if (amount == null || rounded.compareTo(amount) != 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID,
                    "开票明细行金额合计 " + rounded + " 与申请金额 " + amount + " 不一致");
        }
    }

    /**
     * 提交审批。
     *
     * @param instanceId 流程实例 ID
     * @param at 提交时间
     */
    public void submit(Long instanceId, LocalDateTime at) {
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅草稿或已驳回申请允许提交审批");
        }
        this.status = InvoiceStatus.PENDING_APPROVAL;
        this.approvalInstanceId = instanceId;
        this.submittedAt = at;
    }

    /**
     * 应用审批结果。
     *
     * @param approved 是否通过
     * @param at 决策时间
     */
    public void applyApproval(boolean approved, LocalDateTime at) {
        if (status != InvoiceStatus.PENDING_APPROVAL) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅待审批申请允许应用审批结果");
        }
        this.status = approved ? InvoiceStatus.APPROVED : InvoiceStatus.REJECTED;
        if (approved) {
            this.approvedAt = at;
        }
    }

    /**
     * 登记开票记录。
     *
     * @param code 发票代码
     * @param number 发票号码
     * @param issued 实际开票金额
     * @param tax 税额
     * @param at 开票时间
     */
    public void issue(String code, String number, BigDecimal issued, BigDecimal tax, LocalDateTime at) {
        if (status != InvoiceStatus.APPROVED) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅审批通过的申请允许登记开票");
        }
        if (issued == null || issued.signum() <= 0 || issued.compareTo(amount) != 0) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "实际开票金额必须等于申请金额");
        }
        this.status = InvoiceStatus.ISSUED;
        this.invoiceCode = code;
        this.invoiceNumber = number;
        this.issuedAmount = issued;
        this.taxAmount = tax == null ? BigDecimal.ZERO : tax.setScale(AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
        this.issuedAt = at;
    }

    /**
     * 登记寄送。
     *
     * @param express 快递公司
     * @param tracking 运单号
     * @param at 寄送时间
     */
    public void send(String express, String tracking, LocalDateTime at) {
        if (status != InvoiceStatus.ISSUED) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅已开票申请允许登记寄送");
        }
        if (express == null || express.isBlank() || tracking == null || tracking.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_SEND_INVALID);
        }
        this.status = InvoiceStatus.SENT;
        this.courier = express.trim();
        this.trackingNo = tracking.trim();
        this.sentAt = at;
    }

    /**
     * 取消申请（仅草稿或已驳回）。
     */
    public void cancel() {
        if (status == InvoiceStatus.CANCELLED) {
            return;
        }
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅草稿或已驳回申请允许取消");
        }
        this.status = InvoiceStatus.CANCELLED;
    }

    /**
     * 校验原单可红冲（已开票或已寄送的正向申请）。
     */
    public void requireRedFlushable() {
        if (requestType != InvoiceRequestType.NORMAL) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅正向开票申请允许红冲");
        }
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.SENT) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_STATE_CONFLICT, "仅已开票或已寄送申请允许红冲");
        }
    }

    /**
     * 标记红冲完成。
     *
     * @param reason 红冲原因
     * @param at 红冲时间
     */
    public void markRedFlushed(String reason, LocalDateTime at) {
        if (reason == null || reason.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.INVOICE_LINE_INVALID, "红冲原因不能为空");
        }
        this.status = InvoiceStatus.RED_FLUSHED;
        this.redFlushReason = reason.trim();
        this.redFlushedAt = at;
        this.issuedAt = at;
    }

    /**
     * 标记原单已被全额红冲。
     *
     * <p>部分红冲不改原单状态（剩余余额仍可继续红冲），只有累计红冲额冲满开票额时调用。
     * 与 {@link #markRedFlushed} 的区别：<b>不改 issuedAt</b>，原单的实际开票时间必须保留作审计口径。</p>
     *
     * @param reason 最后一次红冲原因
     * @param at 红冲时间
     */
    public void markOriginFullyRedFlushed(String reason, LocalDateTime at) {
        this.status = InvoiceStatus.RED_FLUSHED;
        this.redFlushReason = reason == null ? null : reason.trim();
        this.redFlushedAt = at;
    }
}
