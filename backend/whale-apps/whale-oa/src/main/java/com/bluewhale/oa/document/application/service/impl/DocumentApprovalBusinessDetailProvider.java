package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailFieldView;
import com.bluewhale.oa.document.application.service.DocOutgoingAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 公文业务审批详情提供者。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocumentApprovalBusinessDetailProvider implements ApprovalBusinessDetailProvider {

    /** 业务类型：发文 */
    private static final String BIZ_TYPE_OUTGOING = "DOC_OUTGOING";

    private final DocOutgoingAppService outgoingAppService;

    @Override
    public boolean supports(String businessType) {
        return businessType != null && BIZ_TYPE_OUTGOING.equalsIgnoreCase(businessType.trim());
    }

    @Override
    public ApprovalBusinessDetailView detail(String businessType, Long businessId) {
        if (!supports(businessType)) {
            return null;
        }
        DocumentViews.Outgoing outgoing;
        try {
            outgoing = outgoingAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = new ApprovalBusinessDetailView();
        view.setBusinessType(BIZ_TYPE_OUTGOING);
        view.setBusinessId(businessId);
        view.setStatus(outgoing.getStatus());
        view.setUrl("/document/outgoing");
        view.setSummary("发文 " + nullToEmpty(outgoing.getDocNo())
                + "《" + nullToEmpty(outgoing.getTitle()) + "》");
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("文号", nullToEmpty(outgoing.getDocNo())));
        fields.add(new ApprovalDetailFieldView("标题", nullToEmpty(outgoing.getTitle())));
        fields.add(new ApprovalDetailFieldView("拟稿人", nullToEmpty(outgoing.getDrafterName())));
        fields.add(new ApprovalDetailFieldView("主送", nullToEmpty(outgoing.getMainRecipients())));
        fields.add(new ApprovalDetailFieldView("抄送", nullToEmpty(outgoing.getCopyRecipients())));
        fields.add(new ApprovalDetailFieldView("密级",
                outgoing.getSecurityLevel() == null ? "" : String.valueOf(outgoing.getSecurityLevel())));
        view.setFields(fields);
        return view;
    }

    /**
     * 将 null 字符串转换为空串。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
