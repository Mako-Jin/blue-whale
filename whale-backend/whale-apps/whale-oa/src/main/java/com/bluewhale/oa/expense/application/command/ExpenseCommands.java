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
        @NotBlank(message = "{validation.oa.expense.applicant-required}")
        @Size(max = 64, message = "{validation.oa.expense.applicant-size}")
        private String applicant;

        /** 申请人用户ID */
        private Long applicantId;

        /** 申请部门ID */
        private Long deptId;

        /** 主费用类别编码 */
        @NotBlank(message = "{validation.oa.expense.category-code-required}")
        @Size(max = 32, message = "{validation.oa.expense.category-code-size}")
        private String categoryCode;

        /** 报销事由标题 */
        @NotBlank(message = "{validation.oa.expense.title-required}")
        @Size(max = 128, message = "{validation.oa.expense.title-size}")
        private String title;

        /** 币种 */
        @Size(max = 8, message = "{validation.oa.expense.currency-size}")
        private String currency;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.expense.remark-size}")
        private String remark;

        /** 关联出差单ID（可选） */
        private Long tripId;

        /** 明细 */
        @NotEmpty(message = "{validation.oa.expense.items-required}")
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
        @NotBlank(message = "{validation.oa.expense-item.category-code-required}")
        @Size(max = 32, message = "{validation.oa.expense-item.category-code-size}")
        private String categoryCode;

        /** 费用发生日期 */
        @NotNull(message = "{validation.oa.expense-item.expense-date-required}")
        @PastOrPresent(message = "{validation.oa.expense-item.expense-date-pattern}")
        private LocalDate expenseDate;

        /** 金额 */
        @NotNull(message = "{validation.oa.expense-item.amount-required}")
        @DecimalMin(value = "0.01", message = "{validation.oa.expense-item.amount-min}")
        private BigDecimal amount;

        /** 税额 */
        @DecimalMin(value = "0.00", message = "{validation.oa.expense-item.tax-amount-min}")
        private BigDecimal taxAmount;

        /** 费用说明 */
        @Size(max = 255, message = "{validation.oa.expense-item.description-size}")
        private String description;
    }

    /**
     * 报销发票命令。
     */
    @Data
    public static class ExpenseInvoiceCommand {

        /** 发票代码 */
        @Size(max = 32, message = "{validation.oa.expense-invoice.invoice-code-size}")
        private String invoiceCode;

        /** 发票号码 */
        @NotBlank(message = "{validation.oa.expense-invoice.invoice-no-required}")
        @Size(max = 64, message = "{validation.oa.expense-invoice.invoice-no-size}")
        private String invoiceNo;

        /** 开票日期 */
        private LocalDate invoiceDate;

        /** 发票金额 */
        @NotNull(message = "{validation.oa.expense-invoice.amount-required}")
        @DecimalMin(value = "0.01", message = "{validation.oa.expense-invoice.amount-min}")
        private BigDecimal amount;

        /** 发票税额 */
        @DecimalMin(value = "0.00", message = "{validation.oa.expense-invoice.tax-amount-min}")
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
        @NotBlank(message = "{validation.oa.expense-status.status-required}")
        @Pattern(regexp = "APPROVED|REJECTED|CANCELLED",
                message = "{validation.oa.expense-status.status-pattern}")
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
        @NotBlank(message = "{validation.oa.category.code-required}")
        @Size(max = 32, message = "{validation.oa.category.code-size}")
        private String code;

        /** 类别名称 */
        @NotBlank(message = "{validation.oa.category.name-required}")
        @Size(max = 64, message = "{validation.oa.category.name-size}")
        private String name;

        /** 会计科目 */
        @Size(max = 64, message = "{validation.oa.category.account-subject-size}")
        private String accountSubject;

        /** 是否需要发票 */
        private Boolean requireInvoice;

        /** 单笔限额 */
        @DecimalMin(value = "0.00", message = "{validation.oa.category.single-limit-min}")
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
        @NotNull(message = "{validation.oa.standard.category-id-required}")
        private Long categoryId;

        /** 适用职级/城市编码 */
        @NotBlank(message = "{validation.oa.standard.level-code-required}")
        @Size(max = 32, message = "{validation.oa.standard.level-code-size}")
        private String levelCode;

        /** 限额 */
        @NotNull(message = "{validation.oa.standard.limit-amount-required}")
        @DecimalMin(value = "0.00", message = "{validation.oa.standard.limit-amount-min}")
        private BigDecimal limitAmount;

        /** 是否启用 */
        private Boolean enabled;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.standard.remark-size}")
        private String remark;
    }
}
