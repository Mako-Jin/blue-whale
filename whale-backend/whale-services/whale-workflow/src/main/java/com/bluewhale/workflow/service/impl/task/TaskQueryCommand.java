package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.request.ProcessTaskQueryRequest;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待办任务查询命令：按条件分页查询待办并映射为视图对象。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TaskQueryCommand {

    private final ProcessTaskMapper processTaskMapper;

    /**
     * 分页查询待办任务。
     *
     * @param request 查询条件
     * @return 待办任务分页结果
     */
    public PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request) {
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAssignee() != null, ProcessTaskPo::getAssignee, request.getAssignee());
        wrapper.eq(request.getStatus() != null, ProcessTaskPo::getStatus, request.getStatus());
        wrapper.eq(request.getInstanceId() != null, ProcessTaskPo::getInstanceId, request.getInstanceId());
        wrapper.eq(request.getOperatorId() != null, ProcessTaskPo::getOperatorId, request.getOperatorId());
        if (Boolean.TRUE.equals(request.getHandled())) {
            wrapper.in(ProcessTaskPo::getStatus,
                    List.of(WorkflowConstants.TASK_APPROVED, WorkflowConstants.TASK_REJECTED));
        }
        applyActorFilter(wrapper, request);
        wrapper.orderByDesc(ProcessTaskPo::getCreateTime);
        long current = Math.min(Math.max(request.getCurrent(), 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long size = Math.min(Math.max(request.getSize(), 1L), WorkflowConstants.MAX_PAGE_SIZE);
        Page<ProcessTaskPo> page = processTaskMapper.selectPage(new Page<>(current, size), wrapper);
        List<ProcessTaskVO> records = page.getRecords().stream().map(TaskQueryCommand::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 应用「我审批的」视角过滤：{@code assignee = actorAccount OR operator_id = actorId}。
     *
     * @param wrapper 查询条件
     * @param request 查询条件
     */
    private static void applyActorFilter(LambdaQueryWrapper<ProcessTaskPo> wrapper, ProcessTaskQueryRequest request) {
        boolean hasAccount = request.getActorAccount() != null && !request.getActorAccount().isBlank();
        boolean hasId = request.getActorId() != null;
        if (hasAccount && hasId) {
            wrapper.and(item -> item.eq(ProcessTaskPo::getAssignee, request.getActorAccount())
                    .or()
                    .eq(ProcessTaskPo::getOperatorId, request.getActorId()));
        } else if (hasAccount) {
            wrapper.eq(ProcessTaskPo::getAssignee, request.getActorAccount());
        } else if (hasId) {
            wrapper.eq(ProcessTaskPo::getOperatorId, request.getActorId());
        }
    }

    /**
     * 任务实体转视图对象。
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toVo(ProcessTaskPo entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
