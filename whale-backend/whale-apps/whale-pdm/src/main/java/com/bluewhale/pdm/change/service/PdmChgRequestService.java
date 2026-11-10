package com.bluewhale.pdm.change.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgRequestQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgRequestSaveRequest;
import com.bluewhale.pdm.change.vo.PdmChgRequestVO;

/**
 * 变更申请服务（ECR）
 *
 * <p>状态机：草稿 → 已提交 → 已通过/已驳回 → 已关闭。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgRequestService {

    /**
     * 分页查询变更申请
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmChgRequestVO> page(PdmChgRequestQueryRequest request);

    /**
     * 变更申请详情
     *
     * @param id 申请ID
     * @return 申请视图
     */
    PdmChgRequestVO detail(Long id);

    /**
     * 新建变更申请（草稿）
     *
     * @param request 保存请求
     * @param userId  发起人
     * @return 申请视图
     */
    PdmChgRequestVO create(PdmChgRequestSaveRequest request, Long userId);

    /**
     * 修改草稿变更申请
     *
     * @param id      申请ID
     * @param request 保存请求
     * @return 申请视图
     */
    PdmChgRequestVO update(Long id, PdmChgRequestSaveRequest request);

    /**
     * 删除草稿变更申请
     *
     * @param id 申请ID
     */
    void delete(Long id);

    /**
     * 提交变更申请（影响对象不能为空）
     *
     * @param id 申请ID
     * @return 申请视图
     */
    PdmChgRequestVO submit(Long id);

    /**
     * 审批通过
     *
     * @param id      申请ID
     * @param comment 审批意见
     * @param userId  审批人
     * @return 申请视图
     */
    PdmChgRequestVO approve(Long id, String comment, Long userId);

    /**
     * 审批驳回（审批意见必填）
     *
     * @param id      申请ID
     * @param comment 审批意见
     * @param userId  审批人
     * @return 申请视图
     */
    PdmChgRequestVO reject(Long id, String comment, Long userId);

    /**
     * 关闭申请（已通过/已驳回可关闭）
     *
     * @param id 申请ID
     * @return 申请视图
     */
    PdmChgRequestVO close(Long id);
}
