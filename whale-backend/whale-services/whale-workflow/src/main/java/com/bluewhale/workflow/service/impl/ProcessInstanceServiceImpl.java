package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.dto.InternalProcessStartRequest;
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
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.service.ProcessInstanceService;
import com.bluewhale.workflow.service.impl.task.TaskActivationCommand;
import com.bluewhale.workflow.service.impl.task.TaskCommandSupport;
import com.bluewhale.workflow.support.AssigneeOverrides;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessVariables;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import com.bluewhale.workflow.vo.InstanceStatusVO;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 批量状态查询单次上限 */
    private static final int MAX_BATCH_SIZE = 100;

    /** 版本快照发布状态：已发布 */
    private static final int PUBLISH_PUBLISHED = 1;

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final TaskActivationCommand taskActivationCommand;

    private final ProcessCcService processCcService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessInstanceStartRequest request, String initiator) {
        ProcessDefinition definition = processDefinitionMapper.selectById(request.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return startInstance(definition, request.getTitle(), request.getBusinessKey(), initiator,
                request.getVariables(), request.getAssigneeOverrides(), request.getCcUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO startByCode(InternalProcessStartRequest request) {
        ProcessDefinition definition = findDefinitionByCode(request.getDefinitionCode());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "流程定义不存在：" + request.getDefinitionCode());
        }
        return startInstance(definition, request.getTitle(), request.getBusinessKey(), request.getInitiator(),
                request.getVariables(), request.getAssigneeOverrides(), request.getCcUserIds());
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
        wrapper.eq(StringUtils.hasText(request.getInitiator()), ProcessInstance::getInitiator,
                request.getInitiator());
        wrapper.orderByDesc(ProcessInstance::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ProcessInstance> page = processInstanceMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<ProcessInstanceVO> records = page.getRecords().stream().map(ProcessInstanceServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<InstanceStatusVO> batchStatus(List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = instanceIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(MAX_BATCH_SIZE)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ProcessInstance> wrapper = new LambdaQueryWrapper<ProcessInstance>()
                .in(ProcessInstance::getId, ids);
        List<InstanceStatusVO> result = new ArrayList<>();
        for (ProcessInstance instance : processInstanceMapper.selectList(wrapper)) {
            InstanceStatusVO vo = new InstanceStatusVO();
            vo.setId(instance.getId());
            vo.setBusinessKey(instance.getBusinessKey());
            vo.setStatus(instance.getStatus());
            vo.setCurrentNodeKey(instance.getCurrentNodeKey());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String operator) {
        ProcessInstance instance = requireRunningInstance(id);
        String who = operator == null ? "" : operator.trim();
        if (who.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!who.equals(instance.getInitiator())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "仅发起人可撤销该流程实例");
        }
        doCancel(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInternal(Long id) {
        doCancel(requireRunningInstance(id));
    }

    /**
     * 执行撤销：实例置为已撤销并取消全部待办。
     *
     * @param instance 运行中的流程实例
     */
    private void doCancel(ProcessInstance instance) {
        instance.setStatus(INSTANCE_CANCELED);
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskCommandSupport.cancelPendingTasks(instance.getId());
    }

    /**
     * 查询运行中的流程实例：不存在抛 {@code NOT_FOUND}，非运行中抛参数错误。
     *
     * @param id 流程实例ID
     * @return 运行中的流程实例
     */
    private ProcessInstance requireRunningInstance(Long id) {
        ProcessInstance instance = requireInstance(id);
        if (instance.getStatus() == null || instance.getStatus() != INSTANCE_RUNNING) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "实例非运行中");
        }
        return instance;
    }

    /**
     * 发起实例的公共流程：解析定义 → 校验覆盖 → 建实例 → 激活节点 → 写发起级抄送。
     *
     * @param definition 流程定义
     * @param title 实例标题
     * @param businessKey 业务键
     * @param initiator 发起人账号
     * @param variables 流程变量
     * @param assigneeOverrides 审批人覆盖
     * @param ccUserIds 发起级抄送人
     * @return 流程实例视图对象
     */
    private ProcessInstanceVO startInstance(ProcessDefinition definition, String title, String businessKey,
                                            String initiator, Map<String, Object> variables,
                                            Map<String, List<String>> assigneeOverrides, List<Long> ccUserIds) {
        ProcessDefinitionVersion published = findPublishedSnapshot(definition.getId());
        String definitionJson = published == null ? definition.getDefinitionJson() : published.getDefinitionJson();
        ProcessModel model = ProcessModelParser.parse(definitionJson);
        Map<String, List<String>> normalizedOverrides = AssigneeOverrides.normalize(model, assigneeOverrides);
        String variablesJson = ProcessVariables.write(variables);
        TransitionResult result = new StateMachine(model, variables).start();
        ProcessInstance instance = new ProcessInstance();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionCode(definition.getCode());
        instance.setDefinitionVersion(published == null ? definition.getVersion() : published.getVersion());
        instance.setTitle(title);
        instance.setBusinessKey(businessKey);
        instance.setStatus(result.isUserTask() ? INSTANCE_RUNNING : INSTANCE_APPROVED);
        instance.setCurrentIndex(result.isUserTask() ? model.indexOf(result.getState()) : 0);
        instance.setCurrentNodeKey(result.isUserTask() ? result.getState() : null);
        instance.setInitiator(initiator);
        instance.setVariablesJson(variablesJson);
        instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
        instance.setAssigneeOverridesJson(AssigneeOverrides.write(normalizedOverrides));
        processInstanceMapper.insert(instance);
        taskActivationCommand.activate(instance, model, result);
        processCcService.createStartCc(instance.getId(), ccUserIds);
        return toVo(instance);
    }

    /**
     * 按流程编码查询定义（取最新一条）。
     *
     * @param code 流程编码
     * @return 流程定义，不存在返回 {@code null}
     */
    private ProcessDefinition findDefinitionByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinition> wrapper = new LambdaQueryWrapper<ProcessDefinition>()
                .eq(ProcessDefinition::getCode, code.trim())
                .orderByDesc(ProcessDefinition::getId)
                .last("LIMIT 1");
        List<ProcessDefinition> definitions = processDefinitionMapper.selectList(wrapper);
        return definitions.isEmpty() ? null : definitions.get(0);
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
     * 拼接并行活动节点键（逗号分隔）。
     *
     * @param nodes 审批节点列表
     * @return 逗号分隔的节点键
     */
    private static String joinKeys(List<ProcessModelNode> nodes) {
        return nodes.stream().map(ProcessModelNode::getKey).collect(Collectors.joining(","));
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
