package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建 / 编辑开票申请命令。
 *
 * @author BlueWhale
 */
@Data
public class InvoiceSaveCommand {

    /** 来源类型：CONTRACT / ORDER */
    private String sourceType = "CONTRACT";

    /** 来源单据 ID（合同台账 ID 或商务订单 ID） */
    private Long sourceId;

    /** 币种 */
    private String currency;

    /** 申请含税金额 */
    @NotNull
    private BigDecimal amount;

    /** 发票类型：VAT_SPECIAL / VAT_NORMAL / ELECTRONIC */
    private String invoiceType;

    /** 抬头类型：COMPANY / PERSONAL */
    private String titleType;

    /** 发票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxNo;

    /** 备注 */
    private String remark;

    /** 申请行（可选，填写时合计必须等于申请金额） */
    @Valid
    private List<Item> items = new ArrayList<>();

    /**
     * 申请行命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Item {

        /** 项目名称 */
        private String itemName;

        /** 规格 */
        private String specification;

        /** 数量 */
        @NotNull
        private BigDecimal quantity;

        /** 单价 */
        @NotNull
        private BigDecimal unitPrice;

        /** 税率（百分数） */
        private BigDecimal taxRate;

        /** 行税额 */
        private BigDecimal taxAmount;

        /** 行备注 */
        private String remark;
    }
}
