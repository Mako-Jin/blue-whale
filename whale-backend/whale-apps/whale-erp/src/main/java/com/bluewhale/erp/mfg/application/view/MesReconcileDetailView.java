package com.bluewhale.erp.mfg.application.view;

import com.bluewhale.erp.mfg.domain.model.MesCallbackLog;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import lombok.Data;

import java.util.List;

/**
 * MES 对账详情视图。
 *
 * @author BlueWhale
 */
@Data
public class MesReconcileDetailView {

    /** 生产订单详情 */
    private ProductionOrderView order;

    /** MES 进度快照 */
    private MesProgressSnapshot progress;

    /** 回调流水 */
    private List<MesCallbackLog> callbacks;
}
