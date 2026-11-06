package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailFieldView;
import com.bluewhale.oa.hr.application.service.LeaveAppService;
import com.bluewhale.oa.hr.application.view.LeaveView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 请假审批业务详情提供者。
 *
 * <p>向审批中心输出请假单白名单字段，业务单据不存在时返回 {@code null}。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class LeaveApprovalBusinessDetailProvider implements ApprovalBusinessDetailProvider {

    /** 请假业务类型 */
    private static final String BIZ_TYPE_LEAVE = "LEAVE";

    /** 请假详情前端路由 */
    private static final String DETAIL_URL = "/hr/leave";

    private final LeaveAppService leaveAppService;

    @Override
    public boolean supports(String businessType) {
        return businessType != null && BIZ_TYPE_LEAVE.equalsIgnoreCase(businessType.trim());
    }

    @Override
    public ApprovalBusinessDetailView detail(String businessType, Long businessId) {
        LeaveView leave;
        try {
            leave = leaveAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = new ApprovalBusinessDetailView();
        view.setBusinessType(BIZ_TYPE_LEAVE);
        view.setBusinessId(businessId);
        view.setStatus(leave.getStatus());
        view.setUrl(DETAIL_URL);
        view.setSummary("请假申请 " + nullToEmpty(leave.getCode()));
        view.setFields(fields(leave));
        return view;
    }

    /**
     * 组装白名单字段。
     *
     * @param leave 请假申请
     * @return 明细字段
     */
    private List<ApprovalDetailFieldView> fields(LeaveView leave) {
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("申请单号", nullToEmpty(leave.getCode())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(leave.getApplicant())));
        fields.add(new ApprovalDetailFieldView("请假类型", nullToEmpty(leave.getLeaveType())));
        fields.add(new ApprovalDetailFieldView("请假时段", period(leave)));
        fields.add(new ApprovalDetailFieldView("请假天数",
                leave.getDays() == null ? "" : leave.getDays().stripTrailingZeros().toPlainString()));
        fields.add(new ApprovalDetailFieldView("请假事由", nullToEmpty(leave.getReason())));
        if (StringUtils.hasText(leave.getRemark())) {
            fields.add(new ApprovalDetailFieldView("备注", leave.getRemark()));
        }
        return fields;
    }

    /**
     * 拼接起止时段。
     *
     * @param leave 请假申请
     * @return 时段文本
     */
    private String period(LeaveView leave) {
        if (leave.getStartDate() == null || leave.getEndDate() == null) {
            return "";
        }
        String start = leave.getStartDate() + " " + nullToEmpty(leave.getStartPeriod());
        String end = leave.getEndDate() + " " + nullToEmpty(leave.getEndPeriod());
        return start + " ~ " + end;
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
