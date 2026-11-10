package com.bluewhale.oa.administrative.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import com.bluewhale.oa.administrative.application.service.SealUseAppService;
import com.bluewhale.oa.administrative.application.service.VehicleAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 行政审批结果同步监听器：回写用车 / 用章 / 采购业务状态。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AdministrativeApprovalResultListener {

    private static final String BIZ_TYPE_VEHICLE = "VEHICLE";

    private static final String BIZ_TYPE_SEAL = "SEAL_USE";

    private static final String BIZ_TYPE_PURCHASE = "PURCHASE";

    private final VehicleAppService vehicleAppService;

    private final SealUseAppService sealUseAppService;

    private final PurchaseAppService purchaseAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || event.businessId() == null || event.businessType() == null) {
            return;
        }
        String businessType = event.businessType().trim();
        if (BIZ_TYPE_VEHICLE.equalsIgnoreCase(businessType)) {
            vehicleAppService.syncApprovalResult(event.businessId(), event.result());
        } else if (BIZ_TYPE_SEAL.equalsIgnoreCase(businessType)) {
            sealUseAppService.syncApprovalResult(event.businessId(), event.result());
        } else if (BIZ_TYPE_PURCHASE.equalsIgnoreCase(businessType)) {
            purchaseAppService.syncApprovalResult(event.businessId(), event.result());
        }
    }
}
