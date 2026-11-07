package com.bluewhale.oa.administrative.application.service.impl;

import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import com.bluewhale.oa.administrative.application.service.SealUseAppService;
import com.bluewhale.oa.administrative.application.service.VehicleAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.approval.application.service.ApprovalBusinessDetailProvider;
import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;
import com.bluewhale.oa.approval.application.view.ApprovalDetailFieldView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 行政业务审批详情提供者。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AdministrativeApprovalBusinessDetailProvider implements ApprovalBusinessDetailProvider {

    private static final String BIZ_TYPE_VEHICLE = "VEHICLE";

    private static final String BIZ_TYPE_SEAL = "SEAL_USE";

    private static final String BIZ_TYPE_PURCHASE = "PURCHASE";

    private final VehicleAppService vehicleAppService;

    private final SealUseAppService sealUseAppService;

    private final PurchaseAppService purchaseAppService;

    @Override
    public boolean supports(String businessType) {
        if (businessType == null) {
            return false;
        }
        String type = businessType.trim();
        return BIZ_TYPE_VEHICLE.equalsIgnoreCase(type)
                || BIZ_TYPE_SEAL.equalsIgnoreCase(type)
                || BIZ_TYPE_PURCHASE.equalsIgnoreCase(type);
    }

    @Override
    public ApprovalBusinessDetailView detail(String businessType, Long businessId) {
        String type = businessType == null ? "" : businessType.trim();
        if (BIZ_TYPE_VEHICLE.equalsIgnoreCase(type)) {
            return vehicleDetail(businessId);
        }
        if (BIZ_TYPE_SEAL.equalsIgnoreCase(type)) {
            return sealDetail(businessId);
        }
        if (BIZ_TYPE_PURCHASE.equalsIgnoreCase(type)) {
            return purchaseDetail(businessId);
        }
        return null;
    }

    private ApprovalBusinessDetailView vehicleDetail(Long businessId) {
        AdministrativeViews.BookingView booking;
        try {
            booking = vehicleAppService.bookingDetail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = baseView(BIZ_TYPE_VEHICLE, businessId, booking.getStatus(),
                "/vehicle", "用车申请 " + nullToEmpty(booking.getCode()));
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("用车单号", nullToEmpty(booking.getCode())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(booking.getApplicant())));
        String timeRange = nullToEmpty(String.valueOf(booking.getStartTime())) + " ~ "
                + nullToEmpty(String.valueOf(booking.getEndTime()));
        fields.add(new ApprovalDetailFieldView("用车时间", timeRange));
        fields.add(new ApprovalDetailFieldView("行程",
                nullToEmpty(booking.getOrigin()) + " → " + nullToEmpty(booking.getDestination())));
        fields.add(new ApprovalDetailFieldView("用车事由", nullToEmpty(booking.getReason())));
        view.setFields(fields);
        return view;
    }

    private ApprovalBusinessDetailView sealDetail(Long businessId) {
        AdministrativeViews.SealUseView sealUse;
        try {
            sealUse = sealUseAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = baseView(BIZ_TYPE_SEAL, businessId, sealUse.getStatus(),
                "/seal", "用章申请 " + nullToEmpty(sealUse.getCode()));
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("用章单号", nullToEmpty(sealUse.getCode())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(sealUse.getApplicant())));
        fields.add(new ApprovalDetailFieldView("印章", nullToEmpty(sealUse.getSealName())));
        fields.add(new ApprovalDetailFieldView("用章事由", nullToEmpty(sealUse.getReason())));
        fields.add(new ApprovalDetailFieldView("文件数",
                sealUse.getFileCount() == null ? "0" : String.valueOf(sealUse.getFileCount())));
        view.setFields(fields);
        return view;
    }

    private ApprovalBusinessDetailView purchaseDetail(Long businessId) {
        AdministrativeViews.PurchaseView purchase;
        try {
            purchase = purchaseAppService.detail(businessId);
        } catch (RuntimeException exception) {
            return null;
        }
        ApprovalBusinessDetailView view = baseView(BIZ_TYPE_PURCHASE, businessId, purchase.getStatus(),
                "/purchase", "采购申请 " + nullToEmpty(purchase.getCode()));
        List<ApprovalDetailFieldView> fields = new ArrayList<>();
        fields.add(new ApprovalDetailFieldView("采购单号", nullToEmpty(purchase.getCode())));
        fields.add(new ApprovalDetailFieldView("申请人", nullToEmpty(purchase.getApplicant())));
        fields.add(new ApprovalDetailFieldView("采购事由", nullToEmpty(purchase.getReason())));
        fields.add(new ApprovalDetailFieldView("合计金额",
                purchase.getTotalAmount() == null ? ""
                        : purchase.getTotalAmount().stripTrailingZeros().toPlainString()
                        + " " + nullToEmpty(purchase.getCurrency())));
        fields.add(new ApprovalDetailFieldView("明细笔数",
                purchase.getItems() == null ? "0" : String.valueOf(purchase.getItems().size())));
        view.setFields(fields);
        return view;
    }

    private ApprovalBusinessDetailView baseView(String businessType, Long businessId, String status,
                                                String url, String summary) {
        ApprovalBusinessDetailView view = new ApprovalBusinessDetailView();
        view.setBusinessType(businessType);
        view.setBusinessId(businessId);
        view.setStatus(status);
        view.setUrl(url);
        view.setSummary(summary);
        return view;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
