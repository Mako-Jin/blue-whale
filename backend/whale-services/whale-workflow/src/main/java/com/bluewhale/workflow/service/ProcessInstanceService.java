package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.vo.ProcessInstanceVO;

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
     * 撤销流程实例：仅运行中可撤销，其待办全部置「已取消」
     *
     * @param id 流程实例ID
     */
    void cancel(Long id);
}
