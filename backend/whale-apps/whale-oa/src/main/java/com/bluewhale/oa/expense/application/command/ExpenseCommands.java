package com.bluewhale.oa.expense.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报销单相关命令集合。
 *
 * @author BlueWhale
 */
public final class ExpenseCommands {

    /**
     * 工具类私有构造器。
     */
    private ExpenseCommands() {
    }

    /**
     * 报销单保存命令。
     */
    @Data
    public static class ExpenseSaveCommand {

        /** 申请人账号 */
        @NotBlank(message = "申请人不能为空")
        @Size(max = 64, message = "申请人账号长度不能超过 64")
        private String applicant;

        /** 申请人用户ID */
        private Long applicantId;

        /** 申请部门ID */
        private Long deptId;

        /** 主费用类别编码 */
        @NotBlank(message = "费用类别不能为空")
        @Size(max = 32, message = "费用类别编码长度不能超过 32")
        private String categoryCode;

        /** 报销事由标题 */
        @NotBlank(message = "报销事由不能为空")
        @Size(max = 128, message = "报销事由长度不能超过 128")
        private String title;

        /** 币种 */
        @Size(max = 8, message = "币种长度不能超过 8")
        private String currency;

        /** 备注 */
        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;

        /** 明细 */
        @NotEmpty(message = "报销明细不能为空")
        @Valid
        private List<ExpenseItemCommand> items;

        /** 发票 */
        @Valid
        private List<ExpenseInvoiceCommand> invoices;
    }

    /**
     * 报销明细命令。
     */
    @Data
    public static class ExpenseItemCommand {

        /** 费用类别编码 */
        @NotBlank(message = "明细费用类别不能为空")
        @Size(max = 32, message = "费用类别编码长度不能超过 32")
        private String categoryCode;

        /** 费用发生日期 */
        @NotNull(message = "费用发生日期不能为空")
        @PastOrPresent(message = "费用发生日期不能晚于今天")
        private LocalDate expenseDate;

        /** 金额 */
        @NotNull(message = "明细金额不能为空")
        @DecimalMin(value = "0.01", message = "明细金额必须大于 0")
        private BigDecimal amount;

        /** 税额 */
        @DecimalMin(value = "0.00", message = "税额不能为负")
        private BigDecimal taxAmount;

        /** 费用说明 */
        @Size(max = 255, message = "费用说明长度不能超过 255")
        private String description;
    }

    /**
     * 报销发票命令。
     */
    @Data
    public static class ExpenseInvoiceCommand {

        /** 发票代码 */
        @Size(max = 32, message = "发票代码长度不能超过 32")
        private String invoiceCode;

        /** 发票号码 */
        @NotBlank(message = "发票号码不能为空")
        @Size(max = 64, message = "发票号码长度不能超过 64")
        private String invoiceNo;

        /** 开票日期 */
        private LocalDate invoiceDate;

        /** 发票金额 */
        @NotNull(message = "发票金额不能为空")
        @DecimalMin(value = "0.01", message = "发票金额必须大于 0")
        private BigDecimal amount;

        /** 发票税额 */
        @DecimalMin(value = "0.00", message = "发票税额不能为负")
        private BigDecimal taxAmount;

        /** 发票影像文件ID */
        private Long fileId;
    }

    /**
     * 报销单状态变更命令。
     */
    @Data
    public static class ExpenseStatusCommand {

        /** 目标状态 */
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "APPROVED|REJECTED|CANCELLED",
                message = "状态仅支持 APPROVED / REJECTED / CANCELLED")
        private String status;
    }

    /**
     * 费用类别保存命令。
     */
    @Data
    public static class CategorySaveCommand {

        /** 主键ID（为空表示新增） */
        private Long id;

        /** 类别编码 */
        @NotBlank(message = "类别编码不能为空")
        @Size(max = 32, message = "类别编码长度不能超过 32")
        private String code;

        /** 类别名称 */
        @NotBlank(message = "类别名称不能为空")
        @Size(max = 64, message = "类别名称长度不能超过 64")
        private String name;

        /** 会计科目 */
        @Size(max = 64, message = "会计科目长度不能超过 64")
        private String accountSubject;

        /** 是否需要发票 */
        private Boolean requireInvoice;

        /** 单笔限额 */
        @DecimalMin(value = "0.00", message = "单笔限额不能为负")
        private BigDecimal singleLimit;

        /** 排序号 */
        private Integer sortNo;

        /** 是否启用 */
        private Boolean enabled;
    }

    /**
     * 费用标准保存命令。
     */
    @Data
    public static class StandardSaveCommand {

        /** 主键ID（为空表示新增） */
        private Long id;

        /** 费用类别ID */
        @NotNull(message = "费用类别不能为空")
        private Long categoryId;

        /** 适用职级/城市编码 */
        @NotBlank(message = "适用范围不能为空")
        @Size(max = 32, message = "适用范围编码长度不能超过 32")
        private String levelCode;

        /** 限额 */
        @NotNull(message = "限额不能为空")
        @DecimalMin(value = "0.00", message = "限额不能为负")
        private BigDecimal limitAmount;

        /** 是否启用 */
        private Boolean enabled;

        /** 备注 */
        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }
}
