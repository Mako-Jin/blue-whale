package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessDefinitionVersion;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessInstanceService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ProcessVariables;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
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

    /** 版本快照发布状态：已发布 */
    private static final int PUBLISH_PUBLISHED = 1;

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessInstanceStartRequest request, String initiator) {
        ProcessDefinition definition = processDefinitionMapper.selectById(request.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ProcessDefinitionVersion published = findPublishedSnapshot(definition.getId());
        String definitionJson = published == null ? definition.getDefinitionJson() : published.getDefinitionJson();
        ProcessModel model = ProcessModelParser.parse(definitionJson);
        String variablesJson = ProcessVariables.write(request.getVariables());
        TransitionResult result = new StateMachine(model, request.getVariables()).start();
        ProcessInstance instance = new ProcessInstance();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionCode(definition.getCode());
        instance.setDefinitionVersion(published == null ? definition.getVersion() : published.getVersion());
        instance.setTitle(request.getTitle());
        instance.setBusinessKey(request.getBusinessKey());
        instance.setStatus(result.isUserTask() ? INSTANCE_RUNNING : INSTANCE_APPROVED);
        instance.setCurrentIndex(result.isUserTask() ? model.indexOf(result.getState()) : 0);
        instance.setCurrentNodeKey(result.isUserTask() ? result.getState() : null);
        instance.setInitiator(initiator);
        instance.setVariablesJson(variablesJson);
        instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
        processInstanceMapper.insert(instance);
        if (result.isUserTask()) {
            createTasks(instance.getId(), model, result.getNodes());
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
     * 查询定义当前已发布的版本快照（版本号最大者）。
     *
     * <p>发起时优先使用已发布快照：编辑中的草稿版本不会影响新实例，
     * 只有发布动作才会切换新实例使用的定义内容。</p>
     *
     * @param definitionId 流程定义ID
     * @return 已发布快照，不存在返回 {@code null}
     */
    private ProcessDefinitionVersion findPublishedSnapshot(Long definitionId) {
        LambdaQueryWrapper<ProcessDefinitionVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersion::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersion::getPublishStatus, PUBLISH_PUBLISHED);
        wrapper.orderByDesc(ProcessDefinitionVersion::getVersion);
        List<ProcessDefinitionVersion> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 为本次推进抵达的全部审批节点创建待办任务（会签节点按 assignees 展开）。
     *
     * @param instanceId 流程实例ID
     * @param model 流程模型
     * @param nodes 审批节点列表
     */
    private void createTasks(Long instanceId, ProcessModel model, List<ProcessModelNode> nodes) {
        for (ProcessModelNode node : nodes) {
            int nodeIndex = model.indexOf(node.getKey());
            if (node.getType() == ProcessNodeType.COUNTER_SIGN && node.getAssignees() != null) {
                for (String assignee : node.getAssignees()) {
                    createTask(instanceId, nodeIndex, node, assignee, node.getCounterSignPolicy());
                }
                continue;
            }
            createTask(instanceId, nodeIndex, node, node.getAssignee(), null);
        }
    }

    /**
     * 创建单条待办任务
     *
     * @param instanceId 流程实例ID
     * @param nodeIndex 节点下标
     * @param node 审批节点
     * @param assignee 审批人
     * @param signPolicy 会签策略（非会签传 {@code null}）
     */
    private void createTask(Long instanceId, int nodeIndex, ProcessModelNode node, String assignee, String signPolicy) {
        ProcessTask task = new ProcessTask();
        task.setInstanceId(instanceId);
        task.setNodeIndex(nodeIndex);
        task.setNodeName(node.getName());
        task.setNodeKey(node.getKey());
        task.setFormId(node.getFormId());
        task.setFormCode(node.getFormCode());
        task.setAssignee(assignee);
        task.setStatus(TASK_PENDING);
        if (node.getType() == ProcessNodeType.COUNTER_SIGN) {
            task.setSignType(ProcessNodeType.COUNTER_SIGN.name());
            task.setSignPolicy(signPolicy == null || signPolicy.isBlank() ? "ALL" : signPolicy);
        }
        processTaskMapper.insert(task);
    }

    /**
     * 拼接并行活动节点键（逗号分隔）。
     *
     * @param nodes 审批节点列表
     * @return 逗号分隔的节点键
     */
    private static String joinKeys(List<ProcessModelNode> nodes) {
        return nodes.stream().map(ProcessModelNode::getKey).collect(java.util.stream.Collectors.joining(","));
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
        return WorkflowConverter.INSTANCE.toInstanceVo(entity);
    }

    /**
     * 任务实体转视图对象
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toTaskVo(ProcessTask entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
