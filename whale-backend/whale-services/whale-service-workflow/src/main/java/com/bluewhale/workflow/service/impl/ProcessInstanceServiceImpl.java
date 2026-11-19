package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.request.InternalProcessStartRequest;
import com.bluewhale.workflow.request.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.request.ProcessInstanceStartRequest;
import com.bluewhale.workflow.po.ProcessDefinitionPo;
import com.bluewhale.workflow.po.ProcessDefinitionVersionPo;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
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
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import com.bluewhale.workflow.vo.InstanceStatusVO;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    /** 批量状态查询单次上限 */
    private static final int MAX_BATCH_SIZE = 100;

    /** 是否允许未发布定义发起：默认关闭，仅存量数据需要时显式开启 */
    @Value("${bluewhale.workflow.allow-unpublished-start:false}")
    private boolean allowUnpublishedStart;

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final TaskActivationCommand taskActivationCommand;

    private final ProcessCcService processCcService;

    private final WorkflowMetrics workflowMetrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessInstanceStartRequest request, String initiator) {
        ProcessDefinitionPo definition = processDefinitionMapper.selectById(request.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return startInstance(definition, request.getTitle(), request.getBusinessKey(), initiator,
                request.getVariables(), request.getAssigneeOverrides(), request.getCcAccounts());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO startByCode(InternalProcessStartRequest request) {
        ProcessDefinitionPo definition = findDefinitionByCode(request.getDefinitionCode());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "workflow.definition.not-found",
                    "流程定义不存在：" + request.getDefinitionCode(), request.getDefinitionCode());
        }
        return startInstance(definition, request.getTitle(), request.getBusinessKey(), request.getInitiator(),
                request.getVariables(), request.getAssigneeOverrides(), request.getCcAccounts());
    }

    @Override
    public ProcessInstanceVO getDetail(Long id) {
        ProcessInstancePo instance = requireInstance(id);
        ProcessInstanceVO vo = toVo(instance);
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, id);
        wrapper.orderByAsc(ProcessTaskPo::getNodeIndex);
        wrapper.orderByAsc(ProcessTaskPo::getCreateTime);
        vo.setTasks(processTaskMapper.selectList(wrapper).stream().map(ProcessInstanceServiceImpl::toTaskVo).toList());
        return vo;
    }

    @Override
    public PageResult<ProcessInstanceVO> page(ProcessInstanceQueryRequest request) {
        LambdaQueryWrapper<ProcessInstancePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getTitle()), ProcessInstancePo::getTitle, request.getTitle());
        wrapper.eq(request.getStatus() != null, ProcessInstancePo::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), ProcessInstancePo::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getInitiator()), ProcessInstancePo::getInitiator,
                request.getInitiator());
        wrapper.orderByDesc(ProcessInstancePo::getCreateTime);
        long current = Math.min(Math.max(request.getCurrent(), 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long size = Math.min(Math.max(request.getSize(), 1L), WorkflowConstants.MAX_PAGE_SIZE);
        Page<ProcessInstancePo> page = processInstanceMapper.selectPage(new Page<>(current, size), wrapper);
        List<ProcessInstanceVO> records = page.getRecords().stream().map(ProcessInstanceServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), current, size);
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
        LambdaQueryWrapper<ProcessInstancePo> wrapper = new LambdaQueryWrapper<ProcessInstancePo>()
                .in(ProcessInstancePo::getId, ids);
        List<InstanceStatusVO> result = new ArrayList<>();
        for (ProcessInstancePo instance : processInstanceMapper.selectList(wrapper)) {
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
    @AuditLog(operation = "撤销流程实例", type = OperateType.UPDATE,
            targetType = "process_instance", targetId = "#id")
    public void cancel(Long id, String operator) {
        ProcessInstancePo instance = requireRunningInstance(id);
        String who = operator == null ? "" : operator.trim();
        if (who.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!who.equals(instance.getInitiator())) {
            throw new BizException(ErrorCode.FORBIDDEN, "workflow.instance.cancel-not-initiator",
                    "仅发起人可撤销该流程实例");
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
    private void doCancel(ProcessInstancePo instance) {
        instance.setStatus(WorkflowConstants.INSTANCE_CANCELED);
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        taskCommandSupport.cancelPendingTasks(instance.getId());
        workflowMetrics.recordInstanceAction(WorkflowMetrics.INSTANCE_CANCEL);
        log.info("流程实例已撤销: instanceId={}, definitionCode={}", instance.getId(), instance.getDefinitionCode());
    }

    /**
     * 查询运行中的流程实例：不存在抛 {@code NOT_FOUND}，非运行中抛参数错误。
     *
     * @param id 流程实例ID
     * @return 运行中的流程实例
     */
    private ProcessInstancePo requireRunningInstance(Long id) {
        ProcessInstancePo instance = requireInstance(id);
        if (instance.getStatus() == null || instance.getStatus() != WorkflowConstants.INSTANCE_RUNNING) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.instance.not-running", "实例非运行中");
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
     * @param ccAccounts 发起级抄送人账号
     * @return 流程实例视图对象
     */
    private ProcessInstanceVO startInstance(ProcessDefinitionPo definition, String title, String businessKey,
                                            String initiator, Map<String, Object> variables,
                                            Map<String, List<String>> assigneeOverrides, List<String> ccAccounts) {
        ProcessDefinitionVersionPo published = findPublishedSnapshot(definition.getId());
        if (published == null && !allowUnpublishedStart) {
            // 草稿内容可能正在编辑，默认不允许新实例落到未发布定义上
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.instance.definition-unpublished",
                    "流程定义未发布，无法发起");
        }
        if (published == null) {
            log.warn("流程定义无已发布快照，按定义当前内容发起: definitionId={}, code={}",
                    definition.getId(), definition.getCode());
        }
        String definitionJson = published == null ? definition.getDefinitionJson() : published.getDefinitionJson();
        ProcessModel model = ProcessModelParser.parse(definitionJson);
        Map<String, List<String>> normalizedOverrides = AssigneeOverrides.normalize(model, assigneeOverrides);
        String variablesJson = ProcessVariables.write(variables);
        TransitionResult result = new StateMachine(model, variables).start();
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionCode(definition.getCode());
        instance.setDefinitionVersion(published == null ? definition.getVersion() : published.getVersion());
        instance.setTitle(title);
        instance.setBusinessKey(businessKey);
        instance.setStatus(result.isUserTask() ? WorkflowConstants.INSTANCE_RUNNING : WorkflowConstants.INSTANCE_APPROVED);
        instance.setCurrentIndex(result.isUserTask() ? model.indexOf(result.getState()) : 0);
        instance.setCurrentNodeKey(result.isUserTask() ? result.getState() : null);
        instance.setInitiator(initiator);
        instance.setVariablesJson(variablesJson);
        instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
        instance.setAssigneeOverridesJson(AssigneeOverrides.write(normalizedOverrides));
        processInstanceMapper.insert(instance);
        taskActivationCommand.activate(instance, model, result);
        processCcService.createStartCc(instance.getId(), ccAccounts);
        workflowMetrics.recordInstanceAction(WorkflowMetrics.INSTANCE_START);
        log.info("流程实例已发起: instanceId={}, definitionCode={}, initiator={}, status={}",
                instance.getId(), instance.getDefinitionCode(), initiator, instance.getStatus());
        return toVo(instance);
    }

    /**
     * 按流程编码查询定义（取最新一条）。
     *
     * @param code 流程编码
     * @return 流程定义，不存在返回 {@code null}
     */
    private ProcessDefinitionPo findDefinitionByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinitionPo> wrapper = new LambdaQueryWrapper<ProcessDefinitionPo>()
                .eq(ProcessDefinitionPo::getCode, code.trim())
                .orderByDesc(ProcessDefinitionPo::getId)
                .last("LIMIT 1");
        List<ProcessDefinitionPo> definitions = processDefinitionMapper.selectList(wrapper);
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
    private ProcessDefinitionVersionPo findPublishedSnapshot(Long definitionId) {
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersionPo::getPublishStatus, WorkflowConstants.PUBLISH_PUBLISHED);
        wrapper.orderByDesc(ProcessDefinitionVersionPo::getVersion);
        List<ProcessDefinitionVersionPo> snapshots = processDefinitionVersionMapper.selectList(wrapper);
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
    private ProcessInstancePo requireInstance(Long id) {
        ProcessInstancePo entity = processInstanceMapper.selectById(id);
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
    private static ProcessInstanceVO toVo(ProcessInstancePo entity) {
        return WorkflowConverter.INSTANCE.toInstanceVo(entity);
    }

    /**
     * 任务实体转视图对象
     *
     * @param entity 待办任务实体
     * @return 待办任务视图对象
     */
    private static ProcessTaskVO toTaskVo(ProcessTaskPo entity) {
        return WorkflowConverter.INSTANCE.toTaskVo(entity);
    }
}
