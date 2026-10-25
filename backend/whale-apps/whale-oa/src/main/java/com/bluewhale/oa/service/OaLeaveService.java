package com.bluewhale.oa.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.dto.OaLeaveQueryRequest;
import com.bluewhale.oa.dto.OaLeaveSaveRequest;
import com.bluewhale.oa.vo.OaLeaveVO;

/**
 * OA 请假申请服务
 *
 * @author BlueWhale
 */
public interface OaLeaveService {

    /**
     * 分页查询请假申请
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<OaLeaveVO> page(OaLeaveQueryRequest request);

    /**
     * 请假申请详情
     *
     * @param id 主键ID
     * @return 请假申请
     */
    OaLeaveVO getDetail(Long id);

    /**
     * 新增请假申请（初始状态 PENDING）
     *
     * @param request 新增请求
     * @return 新增后的申请
     */
    OaLeaveVO create(OaLeaveSaveRequest request);

    /**
     * 更新请假申请（仅 PENDING 可编辑）
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的申请
     */
    OaLeaveVO update(Long id, OaLeaveSaveRequest request);

    /**
     * 变更申请状态（仅 PENDING → APPROVED / REJECTED / CANCELLED）
     *
     * @param id     主键ID
     * @param status 目标状态
     */
    void updateStatus(Long id, String status);

    /**
     * 删除请假申请（仅 PENDING 可删除，逻辑删除）
     *
     * @param id 主键ID
     */
    void delete(Long id);
}
