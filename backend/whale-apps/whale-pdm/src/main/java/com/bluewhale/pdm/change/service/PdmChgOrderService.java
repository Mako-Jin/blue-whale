package com.bluewhale.pdm.change.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgOrderAssessRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgOrderSaveRequest;
import com.bluewhale.pdm.change.dto.PdmChgTaskSaveRequest;
import com.bluewhale.pdm.change.vo.PdmChgOrderVO;

/**
 * 变更单服务（ECO）
 *
 * <p>状态机：草稿 → 评估中 → 审批中 → 已通过 → 执行中 → 已完成 → 已关闭；
 * 旁支为已驳回 / 已取消。审批通过前不可执行。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgOrderService {

    /**
     * 分页查询变更单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmChgOrderVO> page(PdmChgOrderQueryRequest request);

    /**
     * 变更单详情（含任务）
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO detail(Long id);

    /**
     * 从已通过 ECR 新建变更单
     *
     * @param request 保存请求
     * @param userId  发起人
     * @return 变更单视图
     */
    PdmChgOrderVO create(PdmChgOrderSaveRequest request, Long userId);

    /**
     * 修改变更单（草稿/评估中）
     *
     * @param id      变更单ID
     * @param request 保存请求
     * @return 变更单视图
     */
    PdmChgOrderVO update(Long id, PdmChgOrderSaveRequest request);

    /**
     * 删除草稿变更单（级联任务）
     *
     * @param id 变更单ID
     */
    void delete(Long id);

    /**
     * 影响评估（草稿 → 评估中）
     *
     * @param id      变更单ID
     * @param request 评估请求
     * @return 变更单视图
     */
    PdmChgOrderVO assess(Long id, PdmChgOrderAssessRequest request);

    /**
     * 新增变更任务（草稿/评估中）
     *
     * @param id      变更单ID
     * @param request 任务请求
     * @return 变更单视图
     */
    PdmChgOrderVO addTask(Long id, PdmChgTaskSaveRequest request);

    /**
     * 修改变更任务（草稿/评估中）
     *
     * @param id      变更单ID
     * @param taskId  任务ID
     * @param request 任务请求
     * @return 变更单视图
     */
    PdmChgOrderVO updateTask(Long id, Long taskId, PdmChgTaskSaveRequest request);

    /**
     * 删除变更任务（草稿/评估中）
     *
     * @param id     变更单ID
     * @param taskId 任务ID
     * @return 变更单视图
     */
    PdmChgOrderVO deleteTask(Long id, Long taskId);

    /**
     * 提交审批（评估中 → 审批中）
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO submit(Long id);

    /**
     * 审批通过
     *
     * @param id      变更单ID
     * @param comment 审批意见
     * @param userId  审批人
     * @return 变更单视图
     */
    PdmChgOrderVO approve(Long id, String comment, Long userId);

    /**
     * 审批驳回（审批意见必填）
     *
     * @param id      变更单ID
     * @param comment 审批意见
     * @param userId  审批人
     * @return 变更单视图
     */
    PdmChgOrderVO reject(Long id, String comment, Long userId);

    /**
     * 执行变更单（仅已通过可执行）
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO execute(Long id);

    /**
     * 完成任务并登记结果版本号
     *
     * @param id              变更单ID
     * @param taskId          任务ID
     * @param resultVersionNo 结果版本号
     * @return 变更单视图
     */
    PdmChgOrderVO completeTask(Long id, Long taskId, String resultVersionNo);

    /**
     * 完成变更单（全部任务完成后）
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO complete(Long id);

    /**
     * 取消变更单
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO cancel(Long id);

    /**
     * 关闭变更单（已完成/已驳回/已取消）
     *
     * @param id 变更单ID
     * @return 变更单视图
     */
    PdmChgOrderVO close(Long id);
}
