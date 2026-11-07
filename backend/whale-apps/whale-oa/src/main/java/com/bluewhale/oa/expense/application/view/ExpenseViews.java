package com.bluewhale.oa.expense.application.view;

import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseInvoice;
import com.bluewhale.oa.expense.domain.model.ExpenseItem;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报销相关视图集合。
 *
 * @author BlueWhale
 */
public final class ExpenseViews {

    /**
     * 工具类私有构造器。
     */
    private ExpenseViews() {
    }

    /**
     * 报销单视图。
     */
    @Data
    public static class ExpenseView {

        /** 主键ID */
        private Long id;

        /** 报销单号 */
        private String code;

        /** 申请人账号 */
        private String applicant;

        /** 申请人用户ID */
        private Long applicantId;

        /** 申请部门ID */
        private Long deptId;

        /** 主费用类别编码 */
        private String categoryCode;

        /** 报销事由标题 */
        private String title;

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

        /** ERP 应付单号 */
        private String erpPayableNo;

        /** 提交时间 */
        private LocalDateTime submitTime;

        /** 备注 */
        private String remark;

        /** 创建时间 */
        private LocalDateTime createTime;

        /** 更新时间 */
        private LocalDateTime updateTime;

        /** 明细 */
        private List<ItemView> items;

        /** 发票 */
        private List<InvoiceView> invoices;

        /** 审批条件变量（amount / categoryCode / deptId） */
        private Map<String, Object> approvalVariables;
    }

    /**
     * 报销明细视图。
     */
    @Data
    public static class ItemView {

        /** 主键ID */
        private Long id;

        /** 行号 */
        private Integer lineNo;

        /** 费用类别编码 */
        private String categoryCode;

        /** 费用发生日期 */
        private LocalDate expenseDate;

        /** 金额 */
        private BigDecimal amount;

        /** 税额 */
        private BigDecimal taxAmount;

        /** 费用说明 */
        private String description;

        /**
         * 明细模型转视图。
         *
         * @param item 明细模型
         * @return 明细视图
         */
        public static ItemView from(ExpenseItem item) {
            ItemView view = new ItemView();
            view.setId(item.getId());
            view.setLineNo(item.getLineNo());
            view.setCategoryCode(item.getCategoryCode());
            view.setExpenseDate(item.getExpenseDate());
            view.setAmount(item.getAmount());
            view.setTaxAmount(item.getTaxAmount());
            view.setDescription(item.getDescription());
            return view;
        }
    }

    /**
     * 报销发票视图。
     */
    @Data
    public static class InvoiceView {

        /** 主键ID */
        private Long id;

        /** 发票代码 */
        private String invoiceCode;

        /** 发票号码 */
        private String invoiceNo;

        /** 开票日期 */
        private LocalDate invoiceDate;

        /** 发票金额 */
        private BigDecimal amount;

        /** 发票税额 */
        private BigDecimal taxAmount;

        /** 发票影像文件ID */
        private Long fileId;

        /**
         * 发票模型转视图。
         *
         * @param invoice 发票模型
         * @return 发票视图
         */
        public static InvoiceView from(ExpenseInvoice invoice) {
            InvoiceView view = new InvoiceView();
            view.setId(invoice.getId());
            view.setInvoiceCode(invoice.getInvoiceCode());
            view.setInvoiceNo(invoice.getInvoiceNo());
            view.setInvoiceDate(invoice.getInvoiceDate());
            view.setAmount(invoice.getAmount());
            view.setTaxAmount(invoice.getTaxAmount());
            view.setFileId(invoice.getFileId());
            return view;
        }
    }

    /**
     * 费用类别视图。
     */
    @Data
    public static class CategoryView {

        /** 主键ID */
        private Long id;

        /** 类别编码 */
        private String code;

        /** 类别名称 */
        private String name;

        /** 会计科目 */
        private String accountSubject;

        /** 是否需要发票 */
        private Boolean requireInvoice;

        /** 单笔限额 */
        private BigDecimal singleLimit;

        /** 排序号 */
        private Integer sortNo;

        /** 是否启用 */
        private Boolean enabled;

        /**
         * 类别模型转视图。
         *
         * @param category 类别模型
         * @return 类别视图
         */
        public static CategoryView from(ExpenseCategory category) {
            CategoryView view = new CategoryView();
            view.setId(category.getId());
            view.setCode(category.getCode());
            view.setName(category.getName());
            view.setAccountSubject(category.getAccountSubject());
            view.setRequireInvoice(category.getRequireInvoice());
            view.setSingleLimit(category.getSingleLimit());
            view.setSortNo(category.getSortNo());
            view.setEnabled(category.getEnabled());
            return view;
        }
    }

    /**
     * 费用标准视图。
     */
    @Data
    public static class StandardView {

        /** 主键ID */
        private Long id;

        /** 费用类别ID */
        private Long categoryId;

        /** 适用职级/城市编码 */
        private String levelCode;

        /** 限额 */
        private BigDecimal limitAmount;

        /** 是否启用 */
        private Boolean enabled;

        /** 备注 */
        private String remark;

        /**
         * 标准模型转视图。
         *
         * @param standard 标准模型
         * @return 标准视图
         */
        public static StandardView from(ExpenseStandard standard) {
            StandardView view = new StandardView();
            view.setId(standard.getId());
            view.setCategoryId(standard.getCategoryId());
            view.setLevelCode(standard.getLevelCode());
            view.setLimitAmount(standard.getLimitAmount());
            view.setEnabled(standard.getEnabled());
            view.setRemark(standard.getRemark());
            return view;
        }
    }
}
