package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessInstanceService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流程实例服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    /** 实例状态：运行中 */
    private static final int INSTANCE_RUNNING = 0;

    /** 实例状态：已通过 */
    private static final int INSTANCE_APPROVED = 1;

    /** 实例状态：已撤销 */
    private static final int INSTANCE_CANCELED = 3;

    /** 任务状态：待办 */
    private static final int TASK_PENDING = 0;

    /** 任务状态：已取消 */
    private static final int TASK_CANCELED = 3;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessInstanceStartRequest request, String initiator) {
        ProcessDefinition definition = processDefinitionMapper.selectById(request.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ProcessModel model = ProcessModelParser.parse(definition.getDefinitionJson());
        TransitionResult result = new StateMachine(model).start();
        ProcessInstance instance = new ProcessInstance();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionCode(definition.getCode());
        instance.setDefinitionVersion(definition.getVersion());
        instance.setTitle(request.getTitle());
        instance.setBusinessKey(request.getBusinessKey());
        instance.setStatus(result.isUserTask() ? INSTANCE_RUNNING : INSTANCE_APPROVED);
        instance.setCurrentIndex(result.isUserTask() ? model.indexOf(result.getState()) : 0);
        instance.setCurrentNodeKey(result.isUserTask() ? result.getState() : null);
        instance.setInitiator(initiator);
        processInstanceMapper.insert(instance);
        if (result.isUserTask()) {
            createTask(instance.getId(), instance.getCurrentIndex(), result.getNode());
        }
        return toVo(instance);
    }

    @Override
    public ProcessInstanceVO getDetail(Long id) {
        ProcessInstance instance = requireInstance(id);
        ProcessInstanceVO vo = toVo(instance);
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, id);
        wrapper.orderByAsc(ProcessTask::getNodeIndex);
        wrapper.orderByAsc(ProcessTask::getCreateTime);
        vo.setTasks(processTaskMapper.selectList(wrapper).stream().map(ProcessInstanceServiceImpl::toTaskVo).toList());
        return vo;
    }

    @Override
    public PageResult<ProcessInstanceVO> page(ProcessInstanceQueryRequest request) {
        LambdaQueryWrapper<ProcessInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getTitle()), ProcessInstance::getTitle, request.getTitle());
        wrapper.eq(request.getStatus() != null, ProcessInstance::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), ProcessInstance::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.orderByDesc(ProcessInstance::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ProcessInstanceVO> records = page.getRecords().stream().map(ProcessInstanceServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ProcessInstance instance = requireInstance(id);
        if (instance.getStatus() == null || instance.getStatus() != INSTANCE_RUNNING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "实例非运行中");
        }
        instance.setStatus(INSTANCE_CANCELED);
        processInstanceMapper.updateById(instance);
        cancelPendingTasks(id);
    }

    /**
     * 创建待办任务
     *
     * @param instanceId 流程实例ID
     * @param nodeIndex 节点下标
     * @param node 审批节点
     */
    private void createTask(Long instanceId, int nodeIndex, ProcessModelNode node) {
        ProcessTask task = new ProcessTask();
        task.setInstanceId(instanceId);
        task.setNodeIndex(nodeIndex);
        task.setNodeName(node.getName());
        task.setNodeKey(node.getKey());
        task.setFormId(node.getFormId());
        task.setFormCode(node.getFormCode());
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
     * 查询流程实例，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 流程实例ID
     * @return 流程实例实体
     */
    private ProcessInstance requireInstance(Long id) {
        ProcessInstance entity = processInstanceMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 实例实体转视图对象
     *
     * @param entity 流程实例实体
     * @return 流程实例视图对象
     */
    private static ProcessInstanceVO toVo(ProcessInstance entity) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 任务实体转视图对象
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toTaskVo(ProcessTask entity) {
        ProcessTaskVO vo = new ProcessTaskVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
