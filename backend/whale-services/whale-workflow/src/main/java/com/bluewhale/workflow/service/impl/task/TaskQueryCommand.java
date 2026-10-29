package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
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
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAssignee() != null, ProcessTask::getAssignee, request.getAssignee());
        wrapper.eq(request.getStatus() != null, ProcessTask::getStatus, request.getStatus());
        wrapper.eq(request.getInstanceId() != null, ProcessTask::getInstanceId, request.getInstanceId());
        wrapper.orderByDesc(ProcessTask::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), TaskConstants.MAX_PAGE_SIZE);
        Page<ProcessTask> page = processTaskMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ProcessTaskVO> records = page.getRecords().stream().map(TaskQueryCommand::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 任务实体转视图对象。
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toVo(ProcessTask entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
