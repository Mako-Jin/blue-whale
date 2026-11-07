package com.bluewhale.erp.mfg.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.mfg.application.view.MesReconcileDetailView;
import com.bluewhale.erp.mfg.application.view.MesReconcileView;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.model.MesReconcileQuery;

import java.math.BigDecimal;

/**
 * ERP ↔ MES 集成应用服务。
 *
 * @author BlueWhale
 */
public interface MesIntegrationAppService {

    /**
     * 手动下发/重推生产订单到 MES。
     *
     * @param orderId 生产订单主键
     * @return 生产订单视图（含同步状态）
     */
    ProductionOrderView push(Long orderId);

    /**
     * 从 MES 拉取工单进度并刷新快照。
     *
     * @param orderId 生产订单主键
     * @return 进度快照；MES 无对应工单时返回 null
     */
    MesProgressSnapshot pull(Long orderId);

    /**
     * 对账分页。
     *
     * @param query 查询条件
     * @return 对账行分页
     */
    PageResult<MesReconcileView> reconcilePage(MesReconcileQuery query);

    /**
     * 对账详情（订单 + 进度 + 回调流水）。
     *
     * @param orderId 生产订单主键
     * @return 详情
     */
    MesReconcileDetailView reconcileDetail(Long orderId);

    /**
     * 处理 MES 完工回传。
     *
     * @param eventId MES 事件幂等键
     * @param payload JSON 报文
     */
    void handleCompletion(String eventId, String payload);

    /**
     * 处理 MES 进度回传。
     *
     * @param eventId MES 事件幂等键
     * @param payload JSON 报文
     */
    void handleProgress(String eventId, String payload);

    /**
     * 人工确认 MES 完工入库。
     *
     * @param orderId    生产订单主键
     * @param quantity   本次入库数量
     * @param operatorId 操作人
     * @return 生产订单视图
     */
    ProductionOrderView applyCompletion(Long orderId, BigDecimal quantity, Long operatorId);
}
