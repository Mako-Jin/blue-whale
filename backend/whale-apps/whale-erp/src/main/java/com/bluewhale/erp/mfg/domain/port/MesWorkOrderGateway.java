package com.bluewhale.erp.mfg.domain.port;

import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.model.MesPushResult;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;

import java.util.Optional;

/**
 * MES 工单内网网关。
 *
 * @author BlueWhale
 */
public interface MesWorkOrderGateway {

    /**
     * 下发生产订单到 MES（按 erpOrderNo 幂等）。
     *
     * @param order 生产订单（需含用料/工序快照可选）
     * @return 下发结果
     */
    MesPushResult push(ProductionOrder order);

    /**
     * 按 ERP 订单号拉取 MES 工单进度。
     *
     * @param erpOrderNo ERP 生产订单号
     * @return 进度快照；MES 无此工单时为空
     */
    Optional<MesProgressSnapshot> pull(String erpOrderNo);
}
