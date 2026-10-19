package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.vo.ProcessTaskVO;

/**
 * 待办任务服务
 *
 * @author BlueWhale
 */
public interface ProcessTaskService {

    /**
     * 分页查询待办任务
     *
     * @param request 查询条件
     * @return 待办任务分页结果
     */
    PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request);

    /**
     * 同意任务：推进至下一节点；若为末节点则实例「已通过」
     *
     * @param id 任务ID
     * @param comment 审批意见，可为 {@code null}
     */
    void approve(Long id, String comment);

    /**
     * 驳回任务：实例「已驳回」，同实例其余待办置「已取消」
     *
     * @param id 任务ID
     * @param comment 审批意见，可为 {@code null}
     */
    void reject(Long id, String comment);
}
