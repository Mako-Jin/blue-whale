package com.bluewhale.pdm.integration.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.integration.dto.PdmIntSyncQueryRequest;
import com.bluewhale.pdm.integration.vo.PdmIntErpPartResultVO;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;

/**
 * 集成同步日志服务
 *
 * <p>统一记录各通道同步动作，提供按幂等键的查询/覆盖写入与失败重试。</p>
 *
 * @author BlueWhale
 */
public interface PdmIntSyncService {

    /**
     * 分页查询同步日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmIntSyncLogVO> page(PdmIntSyncQueryRequest request);

    /**
     * 按幂等键查询同步日志
     *
     * @param channel        通道
     * @param idempotencyKey 幂等键
     * @return 同步日志，未找到返回 {@code null}
     */
    PdmIntSyncLogVO findByKey(String channel, String idempotencyKey);

    /**
     * 写入或覆盖同步日志
     *
     * @param channel        通道
     * @param action         动作
     * @param direction      方向
     * @param bizType        业务类型
     * @param bizId          业务主键
     * @param bizNo          业务编号
     * @param versionNo      版本号
     * @param idempotencyKey 幂等键
     * @param payload        请求内容快照
     * @param checksum       校验和
     * @param externalRef    外部引用
     * @param error          失败原因，成功传 {@code null}
     * @param operatorId     发起人
     * @return 同步日志视图
     */
    PdmIntSyncLogVO record(String channel, String action, String direction, String bizType, Long bizId,
                           String bizNo, String versionNo, String idempotencyKey, String payload,
                           String checksum, String externalRef, String error, Long operatorId);

    /**
     * 记录 ERP 拉取 BOM 快照
     *
     * @param bomNo     BOM 编号
     * @param versionNo 版本号
     * @param checksum  快照校验和
     */
    void recordBomPull(String bomNo, String versionNo, String checksum);

    /**
     * 重试失败同步
     *
     * @param id         同步日志ID
     * @param operatorId 操作人
     * @return 同步日志视图
     */
    PdmIntSyncLogVO retry(Long id, Long operatorId);

    /**
     * 推送零件到 ERP
     *
     * @param partId     零件ID
     * @param operatorId 操作人
     * @return 同步结果
     */
    PdmIntErpPartResultVO syncPartToErp(Long partId, Long operatorId);
}
