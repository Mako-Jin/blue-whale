package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;

/**
 * 流程定义服务
 *
 * @author BlueWhale
 */
public interface ProcessDefinitionService {

    /**
     * 新增流程定义
     *
     * @param request 流程定义请求参数
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO create(ProcessDefinitionSaveRequest request);

    /**
     * 更新流程定义
     *
     * @param id 流程定义ID
     * @param request 流程定义请求参数
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO update(Long id, ProcessDefinitionSaveRequest request);

    /**
     * 删除流程定义（逻辑删除）
     *
     * @param id 流程定义ID
     */
    void delete(Long id);

    /**
     * 流程定义详情
     *
     * @param id 流程定义ID
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO getDetail(Long id);

    /**
     * 分页查询流程定义
     *
     * @param request 查询条件
     * @return 流程定义分页结果
     */
    PageResult<ProcessDefinitionVO> page(ProcessQueryRequest request);
}
