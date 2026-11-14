package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.InternalProcessStartRequest;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.vo.InstanceStatusVO;
import com.bluewhale.workflow.vo.ProcessInstanceVO;

import java.util.List;

/**
 * 流程实例服务
 *
 * @author BlueWhale
 */
public interface ProcessInstanceService {

    /**
     * 发起流程实例：解析流程定义节点，建实例并生成首节点待办
     *
     * @param request 发起请求：definitionId / title 必填，businessKey 可选
     * @param initiator 发起人账号，可为 {@code null}
     * @return 流程实例视图对象
     */
    ProcessInstanceVO start(ProcessInstanceStartRequest request, String initiator);

    /**
     * 内网按流程编码发起实例（OA 审批中心使用）。
     *
     * @param request 发起请求：definitionCode / title 必填
     * @return 流程实例视图对象
     */
    ProcessInstanceVO startByCode(InternalProcessStartRequest request);

    /**
     * 批量查询实例状态摘要（最多 100 个）。
     *
     * @param instanceIds 流程实例ID列表
     * @return 状态摘要列表
     */
    List<InstanceStatusVO> batchStatus(List<Long> instanceIds);

    /**
     * 流程实例详情（含该实例任务列表）
     *
     * @param id 流程实例ID
     * @return 流程实例视图对象
     */
    ProcessInstanceVO getDetail(Long id);

    /**
     * 分页查询流程实例
     *
     * @param request 查询条件
     * @return 流程实例分页结果
     */
    PageResult<ProcessInstanceVO> page(ProcessInstanceQueryRequest request);

    /**
     * 撤销流程实例：仅运行中可撤销，且仅发起人本人可撤销，其待办全部置「已取消」
     *
     * @param id 流程实例ID
     * @param operator 当前操作人账号
     */
    void cancel(Long id, String operator);

    /**
     * 内网撤销流程实例（服务间调用，不做发起人校验，由内网令牌保护）
     *
     * @param id 流程实例ID
     */
    void cancelInternal(Long id);
}
