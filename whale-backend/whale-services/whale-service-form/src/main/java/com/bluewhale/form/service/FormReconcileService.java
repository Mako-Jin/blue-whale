package com.bluewhale.form.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.model.request.FormReconcileDiffQueryRequest;
import com.bluewhale.form.model.vo.FormReconcileDiffVO;

/**
 * 表单主从数据对账服务：比对主表 {@code form_data} 与各版本物理表，登记差异并支持人工修复。
 *
 * @author BlueWhale
 */
public interface FormReconcileService {

    /**
     * 对指定租户执行一次主从数据对账，逐表登记单向缺失的差异。
     *
     * <p>对账口径：以该租户下状态正常的物理表登记为范围，物理表记录取前
     * {@code 20000} 条参与比对（超出部分记入截断标记），单批次最多登记 {@code 5000} 条差异。</p>
     *
     * @param tenantId 租户ID
     * @return 本次对账执行摘要
     * @throws BizException 租户ID为空时抛出 {@code PARAM_ERROR}
     */
    ReconcileSummary reconcile(Long tenantId);

    /**
     * 分页查询对账差异记录（按租户过滤，创建时间倒序）
     *
     * @param request 查询条件：batchNo / status / physicalTable
     * @param tenantId 租户ID
     * @return 差异记录分页结果
     * @throws BizException 查询条件或租户ID为空时抛出 {@code PARAM_ERROR}
     */
    PageResult<FormReconcileDiffVO> page(FormReconcileDiffQueryRequest request, Long tenantId);

    /**
     * 修复一条对账差异（幂等：仅处理待处理状态）。
     *
     * <p>差异类型 1（主表有物理表无）按主记录的表编码/表版本路由物理表登记，
     * 以 schema 快照与主记录 {@code dataJson} 补写物理表；差异类型 2（物理表有主表无）
     * 逻辑删除物理表残留记录。</p>
     *
     * @param diffId 差异记录ID
     * @param tenantId 租户ID
     * @throws BizException 记录不存在或跨租户时抛出 {@code NOT_FOUND}；
     *         主记录缺失、结构快照缺失或差异类型非法时抛出 {@code PARAM_ERROR}
     */
    void repair(Long diffId, Long tenantId);

    /**
     * 对账执行摘要。
     *
     * @param batchNo 对账批次号（{@code yyyyMMddHHmmss}）
     * @param tableCount 本次遍历的物理表数量
     * @param diffCount 本次登记的新差异条数
     * @param truncated 是否发生截断（物理表扫描超上限或差异登记超上限）
     * @author BlueWhale
     */
    record ReconcileSummary(String batchNo, int tableCount, int diffCount, boolean truncated) {
    }
}
