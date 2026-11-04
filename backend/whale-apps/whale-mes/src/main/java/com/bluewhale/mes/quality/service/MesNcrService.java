package com.bluewhale.mes.quality.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.quality.dto.MesDispositionCreateRequest;
import com.bluewhale.mes.quality.dto.MesDispositionQueryRequest;
import com.bluewhale.mes.quality.dto.MesNcrCloseRequest;
import com.bluewhale.mes.quality.dto.MesNcrCreateRequest;
import com.bluewhale.mes.quality.dto.MesNcrQueryRequest;
import com.bluewhale.mes.quality.entity.MesInspectionTask;
import com.bluewhale.mes.quality.vo.MesDispositionVO;
import com.bluewhale.mes.quality.vo.MesNcrVO;

/**
 * MES 不合格品记录（NCR）与处置应用服务
 *
 * <p>状态机：{@code OPEN → DISPOSITION → REWORK/REPAIR/SCRAP/CONCESSION → CLOSED}。
 * 不变量：检验 FAILED 自动建 NCR；一个 NCR 仅一条处置且处置数量等于不良数量；关闭必须给出结论；
 * 返工处置的 NCR 必须等返工单完成后才能关闭。</p>
 *
 * @author BlueWhale
 */
public interface MesNcrService {

    /**
     * 由不合格检验任务创建 NCR（判定 FAILED 时调用）
     *
     * @param task 检验任务
     * @return NCR ID
     */
    Long createFromInspectionTask(MesInspectionTask task);

    /**
     * 手工创建 NCR
     *
     * @param request 创建请求
     * @return NCR 视图
     */
    MesNcrVO create(MesNcrCreateRequest request);

    /**
     * 分页查询 NCR
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesNcrVO> page(MesNcrQueryRequest request);

    /**
     * 查询 NCR 详情
     *
     * @param id NCR ID
     * @return NCR 视图
     */
    MesNcrVO get(Long id);

    /**
     * 创建处置（数量必须等于 NCR 不良数量）
     *
     * @param request 处置请求
     * @return 处置视图
     */
    MesDispositionVO createDisposition(MesDispositionCreateRequest request);

    /**
     * 执行处置（返工处置自动生成返工单）
     *
     * @param dispositionId 处置 ID
     * @return 处置视图
     */
    MesDispositionVO executeDisposition(Long dispositionId);

    /**
     * 关闭 NCR
     *
     * @param id      NCR ID
     * @param request 关闭请求
     * @return NCR 视图
     */
    MesNcrVO close(Long id, MesNcrCloseRequest request);

    /**
     * 分页查询处置
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesDispositionVO> pageDispositions(MesDispositionQueryRequest request);
}
