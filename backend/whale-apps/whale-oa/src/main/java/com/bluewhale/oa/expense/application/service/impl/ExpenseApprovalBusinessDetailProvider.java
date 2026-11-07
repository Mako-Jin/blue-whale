package com.bluewhale.oa.expense.application.service.impl;

import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailFieldView;
import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 报销审批业务详情提供者。
 *
 * <p>向审批中心输出报销单白名单字段，业务单据不存在时返回 {@code null}。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ExpenseApprovalBusinessDetailProvider implements ApprovalBusinessDetailProvider {

    /** 报销业务类型 */
    private static final String BIZ_TYPE_EXPENSE = "EXPENSE";

    /** 报销详情前端路由 */
    private static final String DETAIL_URL = "/expense";

    private final ExpenseAppService expenseAppService;

    @Override
    public boolean supports(String businessType) {
        return businessType != null && BIZ_TYPE_EXPENSE.equalsIgnoreCase(businessType.trim());
    }

    @Override
    public ApprovalBusinessDetailView detail(String businessType, Long businessId) {
        ExpenseViews.ExpenseView expense;
        try {
            expense = expenseAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = new ApprovalBusinessDetailView();
        view.setBusinessType(BIZ_TYPE_EXPENSE);
        view.setBusinessId(businessId);
        view.setStatus(expense.getStatus());
        view.setUrl(DETAIL_URL);
        view.setSummary("报销申请 " + nullToEmpty(expense.getCode()));
        view.setFields(fields(expense));
        return view;
    }

    /**
     * 组装白名单字段。
     *
     * @param expense 报销单
     * @return 明细字段
     */
    private List<ApprovalDetailFieldView> fields(ExpenseViews.ExpenseView expense) {
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("报销单号", nullToEmpty(expense.getCode())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(expense.getApplicant())));
        fields.add(new ApprovalDetailFieldView("费用类别", nullToEmpty(expense.getCategoryCode())));
        fields.add(new ApprovalDetailFieldView("报销事由", nullToEmpty(expense.getTitle())));
        fields.add(new ApprovalDetailFieldView("合计金额",
                expense.getTotalAmount() == null ? ""
                        : expense.getTotalAmount().stripTrailingZeros().toPlainString()
                        + " " + nullToEmpty(expense.getCurrency())));
        fields.add(new ApprovalDetailFieldView("明细笔数",
                expense.getItems() == null ? "0" : String.valueOf(expense.getItems().size())));
        if (StringUtils.hasText(expense.getRemark())) {
            fields.add(new ApprovalDetailFieldView("备注", expense.getRemark()));
        }
        return fields;
    }

    /**
     * 空值转空串。
     *
     * @param value 文本
     * @return 非空文本或空串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
