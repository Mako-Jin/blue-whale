package com.bluewhale.pdm.integration.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.integration.dto.PdmIntReconcileQueryRequest;
import com.bluewhale.pdm.integration.vo.PdmIntReconcileVO;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;

import java.util.List;

/**
 * 集成对账服务
 *
 * <p>对账只读本地发布记录与同步日志，给出 MATCH/DIFF/MISSING 结论；
 * 差异可通过重新通知或重试下发补偿，不直接改写外部系统数据。</p>
 *
 * @author BlueWhale
 */
public interface PdmIntReconcileService {

    /**
     * 分页查询对账结果
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmIntReconcileVO> page(PdmIntReconcileQueryRequest request);

    /**
     * 对账 BOM：ERP 是否已按版本拉取且校验和一致
     *
     * @return 对账结果
     */
    List<PdmIntReconcileVO> reconcileBom();

    /**
     * 对账 MES 工艺路线下发状态
     *
     * @return 对账结果
     */
    List<PdmIntReconcileVO> reconcileRoute();

    /**
     * 对账 OA 审批状态与业务状态一致性
     *
     * @return 对账结果
     */
    List<PdmIntReconcileVO> reconcileOa();

    /**
     * 重新通知 ERP 拉取 BOM 快照
     *
     * @param bomNo     BOM 编号
     * @param versionNo 版本号
     * @param operatorId 操作人
     * @return 同步日志
     */
    PdmIntSyncLogVO notifyBom(String bomNo, String versionNo, Long operatorId);

    /**
     * 重推 MES 工艺路线
     *
     * @param routeId 路线ID
     * @param operatorId 操作人
     * @return 路线视图
     */
    PdmPrcRouteVO repushRoute(Long routeId, Long operatorId);

    /**
     * 按 OA 审批状态补偿业务状态
     *
     * @param bizType    业务类型：REQUEST/ORDER
     * @param bizId      业务ID
     * @param operatorId 操作人
     * @return 对账结果
     */
    PdmIntReconcileVO compensateOa(String bizType, Long bizId, Long operatorId);
}
