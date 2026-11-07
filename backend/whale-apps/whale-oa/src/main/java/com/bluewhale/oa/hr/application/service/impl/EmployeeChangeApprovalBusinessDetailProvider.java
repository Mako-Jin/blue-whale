package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailFieldView;
import com.bluewhale.oa.hr.application.service.EmployeeChangeAppService;
import com.bluewhale.oa.hr.application.view.EmployeeChangeViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 人员异动审批详情提供者。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class EmployeeChangeApprovalBusinessDetailProvider implements ApprovalBusinessDetailProvider {

    /** 业务类型：人员异动 */
    private static final String BIZ_TYPE_CHANGE = "HR_CHANGE";

    private final EmployeeChangeAppService employeeChangeAppService;

    @Override
    public boolean supports(String businessType) {
        return businessType != null && BIZ_TYPE_CHANGE.equalsIgnoreCase(businessType.trim());
    }

    @Override
    public ApprovalBusinessDetailView detail(String businessType, Long businessId) {
        if (!supports(businessType)) {
            return null;
        }
        EmployeeChangeViews.Detail change;
        try {
            change = employeeChangeAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = new ApprovalBusinessDetailView();
        view.setBusinessType(BIZ_TYPE_CHANGE);
        view.setBusinessId(businessId);
        view.setStatus(change.getStatus());
        view.setUrl("/hr/change");
        view.setSummary("人员异动 " + nullToEmpty(change.getChangeNo())
                + " " + nullToEmpty(change.getChangeTypeLabel())
                + " " + nullToEmpty(change.getEmployeeName()));
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("单号", nullToEmpty(change.getChangeNo())));
        fields.add(new ApprovalDetailFieldView("类型", nullToEmpty(change.getChangeTypeLabel())));
        fields.add(new ApprovalDetailFieldView("员工", nullToEmpty(change.getEmployeeName())));
        fields.add(new ApprovalDetailFieldView("生效日期",
                change.getEffectiveDate() == null ? "" : change.getEffectiveDate().toString()));
        fields.add(new ApprovalDetailFieldView("原因", nullToEmpty(change.getReason())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(change.getApplicantName())));
        view.setFields(fields);
        return view;
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 空串或原值
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
