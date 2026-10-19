package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.support.ProcessNodeParser;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 待办任务服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessTaskServiceImpl implements ProcessTaskService {

    /** 实例状态：运行中 */
    private static final int INSTANCE_RUNNING = 0;

    /** 实例状态：已通过 */
    private static final int INSTANCE_APPROVED = 1;

    /** 实例状态：已驳回 */
    private static final int INSTANCE_REJECTED = 2;

    /** 任务状态：待办 */
    private static final int TASK_PENDING = 0;

    /** 任务状态：已同意 */
    private static final int TASK_APPROVED = 1;

    /** 任务状态：已驳回 */
    private static final int TASK_REJECTED = 2;

    /** 任务状态：已取消 */
    private static final int TASK_CANCELED = 3;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ProcessTaskMapper processTaskMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessDefinitionMapper processDefinitionMapper;

    @Override
    public PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request) {
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAssignee() != null, ProcessTask::getAssignee, request.getAssignee());
        wrapper.eq(request.getStatus() != null, ProcessTask::getStatus, request.getStatus());
        wrapper.eq(request.getInstanceId() != null, ProcessTask::getInstanceId, request.getInstanceId());
        wrapper.orderByDesc(ProcessTask::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ProcessTask> page = processTaskMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ProcessTaskVO> records = page.getRecords().stream().map(ProcessTaskServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String comment) {
        ProcessTask task = requirePendingTask(id);
        ProcessInstance instance = requireRunningInstance(task.getInstanceId());
        task.setStatus(TASK_APPROVED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        ProcessDefinition definition = processDefinitionMapper.selectById(instance.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<ProcessNodeParser.Node> nodes = ProcessNodeParser.parse(definition.getDefinitionJson());
        int nextIndex = instance.getCurrentIndex() + 1;
        if (nextIndex >= nodes.size()) {
            instance.setStatus(INSTANCE_APPROVED);
            processInstanceMapper.updateById(instance);
            return;
        }
        instance.setCurrentIndex(nextIndex);
        processInstanceMapper.updateById(instance);
        createTask(instance.getId(), nextIndex, nodes.get(nextIndex));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment) {
        ProcessTask task = requirePendingTask(id);
        ProcessInstance instance = requireRunningInstance(task.getInstanceId());
        task.setStatus(TASK_REJECTED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        instance.setStatus(INSTANCE_REJECTED);
        processInstanceMapper.updateById(instance);
        cancelPendingTasks(instance.getId());
    }

    /**
     * 校验并返回待办任务：不存在抛 {@code NOT_FOUND}，已处理抛参数错误
     *
     * @param id 任务ID
     * @return 待办任务实体
     */
    private ProcessTask requirePendingTask(Long id) {
        ProcessTask task = processTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (task.getStatus() == null || task.getStatus() != TASK_PENDING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务已处理");
        }
        return task;
    }

    /**
     * 校验并返回运行中的流程实例：不存在抛 {@code NOT_FOUND}，非运行中抛参数错误
     *
     * @param instanceId 流程实例ID
     * @return 流程实例实体
     */
    private ProcessInstance requireRunningInstance(Long instanceId) {
        ProcessInstance instance = processInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (instance.getStatus() == null || instance.getStatus() != INSTANCE_RUNNING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "实例非运行中");
        }
        return instance;
    }

    /**
     * 创建待办任务
     *
     * @param instanceId 流程实例ID
     * @param nodeIndex 节点下标
     * @param node 审批节点
     */
    private void createTask(Long instanceId, int nodeIndex, ProcessNodeParser.Node node) {
        ProcessTask task = new ProcessTask();
        task.setInstanceId(instanceId);
        task.setNodeIndex(nodeIndex);
        task.setNodeName(node.getName());
        task.setAssignee(node.getAssignee());
        task.setStatus(TASK_PENDING);
        processTaskMapper.insert(task);
    }

    /**
     * 将实例下所有待办任务置为「已取消」
     *
     * @param instanceId 流程实例ID
     */
    private void cancelPendingTasks(Long instanceId) {
        ProcessTask update = new ProcessTask();
        update.setStatus(TASK_CANCELED);
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getStatus, TASK_PENDING);
        processTaskMapper.update(update, wrapper);
    }

    /**
     * 任务实体转视图对象
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toVo(ProcessTask entity) {
        ProcessTaskVO vo = new ProcessTaskVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
