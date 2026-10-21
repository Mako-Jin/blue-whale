package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ApprovalRequest;
import com.bluewhale.workflow.dto.ProcessTaskQueryRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessTaskService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ProcessVariables;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.TransitionResult;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    /** 会签策略：全员同意 */
    private static final String POLICY_ALL = "ALL";

    /** 会签策略：任一同意 */
    private static final String POLICY_ANY = "ANY";

    /** 会签策略：比例前缀 */
    private static final String POLICY_RATIO_PREFIX = "RATIO:";

    /** 百分比基数 */
    private static final int PERCENT_BASE = 100;

    /** 加签任务类型标记（普通审批节点追加的审批人） */
    private static final String SIGN_TYPE_ADD_SIGN = "ADD_SIGN";

    /** 单次加签人数上限 */
    private static final int MAX_ADD_SIGNERS = 10;

    /** 审批人账号最大长度 */
    private static final int MAX_ASSIGNEE_LENGTH = 64;

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
    public void approve(Long id, ApprovalRequest request) {
        approve(id, request == null ? null : request.getComment(),
                request == null ? null : request.getVariables());
    }

    /**
     * 同意任务：更新任务状态 → 会签策略判定 → 并行分支收敛 → 推进状态机。
     *
     * @param id 任务ID
     * @param comment 审批意见
     * @param variables 本次追加的流程变量（可为 {@code null}）
     */
    private void approve(Long id, String comment, Map<String, Object> variables) {
        ProcessTask task = requirePendingTask(id);
        ProcessInstance instance = requireRunningInstance(task.getInstanceId());
        task.setStatus(TASK_APPROVED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        ProcessDefinition definition = processDefinitionMapper.selectById(instance.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ProcessModel model = ProcessModelParser.parse(definition.getDefinitionJson());
        Map<String, Object> merged = ProcessVariables.merge(instance.getVariablesJson(), variables);
        if (variables != null && !variables.isEmpty()) {
            instance.setVariablesJson(ProcessVariables.write(merged));
        }
        String nodeKey = resolveCurrentKey(task, model, instance);
        ProcessModelNode node = model.node(nodeKey);
        if (isWaitingForSameNode(instance.getId(), nodeKey, node)) {
            processInstanceMapper.updateById(instance);
            return;
        }
        String remaining = removeActiveKey(instance.getActiveNodeKeys(), nodeKey);
        if (!remaining.isEmpty()) {
            instance.setActiveNodeKeys(remaining);
            processInstanceMapper.updateById(instance);
            return;
        }
        if (instance.getActiveNodeKeys() != null && !instance.getActiveNodeKeys().isBlank()) {
            instance.setActiveNodeKeys(null);
        }
        TransitionResult result = new StateMachine(model, merged).approve(nodeKey);
        applyResult(instance, model, result);
    }

    /**
     * 解析任务对应的节点键：优先任务快照 {@code nodeKey}，其次实例 {@code current_node_key}，
     * 最后回落 {@code currentIndex} 下标（旧行）。
     *
     * @param task 待办任务
     * @param model 流程模型
     * @param instance 流程实例
     * @return 当前节点键，无法解析返回 {@code null}
     */
    private static String resolveCurrentKey(ProcessTask task, ProcessModel model, ProcessInstance instance) {
        if (task.getNodeKey() != null && !task.getNodeKey().isBlank()) {
            return task.getNodeKey();
        }
        return resolveCurrentKey(model, instance);
    }

    /**
     * 解析实例当前节点键：优先 {@code current_node_key}，为空时回落 {@code currentIndex} 下标（旧行）。
     *
     * @param model 流程模型
     * @param instance 流程实例
     * @return 当前节点键，无法解析返回 {@code null}
     */
    private static String resolveCurrentKey(ProcessModel model, ProcessInstance instance) {
        if (instance.getCurrentNodeKey() != null && !instance.getCurrentNodeKey().isBlank()) {
            return instance.getCurrentNodeKey();
        }
        int index = instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex();
        ProcessModelNode node = model.nodeAt(index);
        return node == null ? null : node.getKey();
    }

    /**
     * 应用状态机推进结果：停在审批节点则更新当前节点并建任务，否则实例置为已通过。
     *
     * @param instance 流程实例
     * @param model 流程模型
     * @param result 迁移结果
     */
    private void applyResult(ProcessInstance instance, ProcessModel model, TransitionResult result) {
        if (result.isUserTask()) {
            int nextIndex = model.indexOf(result.getState());
            instance.setCurrentIndex(nextIndex);
            instance.setCurrentNodeKey(result.getState());
            instance.setActiveNodeKeys(result.getNodes().size() > 1 ? joinKeys(result.getNodes()) : null);
            processInstanceMapper.updateById(instance);
            createTasks(instance.getId(), model, result.getNodes());
            return;
        }
        instance.setStatus(INSTANCE_APPROVED);
        instance.setActiveNodeKeys(null);
        if (result.getState() != null) {
            instance.setCurrentNodeKey(result.getState());
        }
        processInstanceMapper.updateById(instance);
    }

    /**
     * 会签策略判定：节点待办是否已满足完成条件（ANY/RATIO 达成时取消其余待办）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 会签节点键
     * @param node 会签节点
     * @return 满足返回 {@code true}
     */
    private boolean counterSignSatisfied(Long instanceId, String nodeKey, ProcessModelNode node) {
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        List<ProcessTask> tasks = processTaskMapper.selectList(wrapper);
        long total = tasks.size();
        long approved = tasks.stream().filter(item -> isStatus(item, TASK_APPROVED)).count();
        long pending = tasks.stream().filter(item -> isStatus(item, TASK_PENDING)).count();
        String policy = policyOf(node);
        if (policy.startsWith(POLICY_RATIO_PREFIX)) {
            int percent = parseRatio(policy);
            if (approved * PERCENT_BASE >= total * percent) {
                cancelPendingTasksOfNode(instanceId, nodeKey);
                return true;
            }
            return false;
        }
        if (POLICY_ANY.equals(policy)) {
            if (approved > 0) {
                cancelPendingTasksOfNode(instanceId, nodeKey);
                return true;
            }
            return false;
        }
        return pending == 0;
    }

    /**
     * 读取节点会签策略（缺省 ALL）。
     *
     * @param node 会签节点
     * @return 大写的策略文本
     */
    private static String policyOf(ProcessModelNode node) {
        String policy = node.getCounterSignPolicy();
        return policy == null || policy.isBlank() ? POLICY_ALL : policy.trim().toUpperCase();
    }

    /**
     * 解析 RATIO:n 的百分比阈值。
     *
     * @param policy 策略文本（形如 {@code RATIO:60}）
     * @return 百分比；非法时回落 100
     */
    private static int parseRatio(String policy) {
        try {
            return Integer.parseInt(policy.substring(POLICY_RATIO_PREFIX.length()));
        } catch (NumberFormatException e) {
            return PERCENT_BASE;
        }
    }

    /**
     * 判断任务状态是否等于指定值。
     *
     * @param task 任务
     * @param status 状态值
     * @return 相等返回 {@code true}
     */
    private static boolean isStatus(ProcessTask task, int status) {
        return task.getStatus() != null && task.getStatus() == status;
    }

    /**
     * 取消同节点其余待办（会签 ANY/RATIO 达成时收敛）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     */
    private void cancelPendingTasksOfNode(Long instanceId, String nodeKey) {
        ProcessTask update = new ProcessTask();
        update.setStatus(TASK_CANCELED);
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        wrapper.eq(ProcessTask::getStatus, TASK_PENDING);
        processTaskMapper.update(update, wrapper);
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
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        cancelPendingTasks(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, String targetUser) {
        ProcessTask task = requirePendingTask(id);
        String target = requireTargetUser(targetUser);
        if (task.getOwnerId() == null || task.getOwnerId().isBlank()) {
            task.setOwnerId(task.getAssignee());
        }
        task.setAssignee(target);
        processTaskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegate(Long id, String targetUser) {
        ProcessTask task = requirePendingTask(id);
        String target = requireTargetUser(targetUser);
        if (task.getDelegateFrom() == null || task.getDelegateFrom().isBlank()) {
            task.setDelegateFrom(task.getAssignee());
        }
        task.setAssignee(target);
        processTaskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, String comment) {
        ProcessTask task = requirePendingTask(id);
        String origin = task.getDelegateFrom();
        if (origin == null || origin.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务不是委派任务");
        }
        task.setStatus(TASK_CANCELED);
        task.setComment(comment);
        processTaskMapper.updateById(task);
        ProcessTask restored = new ProcessTask();
        restored.setInstanceId(task.getInstanceId());
        restored.setNodeIndex(task.getNodeIndex());
        restored.setNodeName(task.getNodeName());
        restored.setNodeKey(task.getNodeKey());
        restored.setFormId(task.getFormId());
        restored.setFormCode(task.getFormCode());
        restored.setParentNodeKey(task.getParentNodeKey());
        restored.setSignType(task.getSignType());
        restored.setSignPolicy(task.getSignPolicy());
        restored.setOwnerId(task.getOwnerId());
        restored.setAssignee(origin);
        restored.setStatus(TASK_PENDING);
        processTaskMapper.insert(restored);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String targetNodeKey) {
        ProcessTask task = requirePendingTask(id);
        ProcessInstance instance = requireRunningInstance(task.getInstanceId());
        ProcessDefinition definition = processDefinitionMapper.selectById(instance.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ProcessModel model = ProcessModelParser.parse(definition.getDefinitionJson());
        ProcessModelNode target = model.node(targetNodeKey);
        if (target == null || target.getType() == null || !target.getType().isApproval()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "回退目标必须为审批节点");
        }
        cancelPendingTasks(instance.getId());
        instance.setStatus(INSTANCE_RUNNING);
        instance.setCurrentIndex(model.indexOf(target.getKey()));
        instance.setCurrentNodeKey(target.getKey());
        instance.setActiveNodeKeys(null);
        processInstanceMapper.updateById(instance);
        createTasks(instance.getId(), model, List.of(target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSign(Long id, List<String> assignees) {
        ProcessTask task = requirePendingTask(id);
        requireRunningInstance(task.getInstanceId());
        List<String> targets = normalizeAddSigners(assignees);
        boolean counterSign = ProcessNodeType.COUNTER_SIGN.name().equals(task.getSignType());
        if (counterSign && !POLICY_ALL.equals(policyOfTask(task))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "ANY/RATIO 会签节点不支持加签");
        }
        for (ProcessTask existing : listTasksOfNode(task)) {
            if (existing.getStatus() != null && existing.getStatus() == TASK_CANCELED) {
                continue;
            }
            if (existing.getAssignee() != null && targets.contains(existing.getAssignee())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                        "加签人已在本节点任务中：" + existing.getAssignee());
            }
        }
        for (String target : targets) {
            ProcessTask added = new ProcessTask();
            added.setInstanceId(task.getInstanceId());
            added.setNodeIndex(task.getNodeIndex());
            added.setNodeName(task.getNodeName());
            added.setNodeKey(task.getNodeKey());
            added.setFormId(task.getFormId());
            added.setFormCode(task.getFormCode());
            added.setParentNodeKey(task.getParentNodeKey());
            added.setAssignee(target);
            added.setStatus(TASK_PENDING);
            added.setOwnerId(task.getAssignee());
            added.setSignType(counterSign ? ProcessNodeType.COUNTER_SIGN.name() : SIGN_TYPE_ADD_SIGN);
            added.setSignPolicy(counterSign ? POLICY_ALL : null);
            processTaskMapper.insert(added);
        }
    }

    /**
     * 规范化加签人列表：去空白、去重、校验非空与长度/数量上限。
     *
     * @param assignees 原始加签人列表，可为 {@code null}
     * @return 去重后的加签人列表
     * @throws BizException 列表为空、账号过长或超过单次上限时抛出 {@code PARAM_ERROR}
     */
    private static List<String> normalizeAddSigners(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人不能为空");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : assignees) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String target = raw.trim();
            if (target.length() > MAX_ASSIGNEE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人账号长度不能超过 64");
            }
            unique.add(target);
        }
        if (unique.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "加签人不能为空");
        }
        if (unique.size() > MAX_ADD_SIGNERS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单次最多加签 10 人");
        }
        return new ArrayList<>(unique);
    }

    /**
     * 读取任务快照的会签策略（缺省 ALL）。
     *
     * @param task 待办任务
     * @return 大写的策略文本
     */
    private static String policyOfTask(ProcessTask task) {
        String policy = task.getSignPolicy();
        return policy == null || policy.isBlank() ? POLICY_ALL : policy.trim().toUpperCase();
    }

    /**
     * 查询同一节点上的全部任务（优先按节点键，旧行无节点键时回落节点下标）。
     *
     * @param task 当前待办任务
     * @return 同节点任务列表
     */
    private List<ProcessTask> listTasksOfNode(ProcessTask task) {
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, task.getInstanceId());
        if (task.getNodeKey() != null && !task.getNodeKey().isBlank()) {
            wrapper.eq(ProcessTask::getNodeKey, task.getNodeKey());
        } else {
            wrapper.eq(ProcessTask::getNodeIndex, task.getNodeIndex());
        }
        return processTaskMapper.selectList(wrapper);
    }

    /**
     * 判断同节点是否仍有待办（加签/会签场景需等待其余审批人）。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     * @return 仍有待办返回 {@code true}
     */
    private boolean hasPendingTasksOfNode(Long instanceId, String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<ProcessTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTask::getInstanceId, instanceId);
        wrapper.eq(ProcessTask::getNodeKey, nodeKey);
        wrapper.eq(ProcessTask::getStatus, TASK_PENDING);
        Long pending = processTaskMapper.selectCount(wrapper);
        return pending != null && pending > 0;
    }

    /**
     * 判定当前同意是否仍需等待同节点其他待办：会签走策略判定，普通/加签节点有待办即等待。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 节点键
     * @param node 流程模型节点（可为 {@code null}）
     * @return 需等待返回 {@code true}
     */
    private boolean isWaitingForSameNode(Long instanceId, String nodeKey, ProcessModelNode node) {
        if (node != null && node.getType() == ProcessNodeType.COUNTER_SIGN) {
            return !counterSignSatisfied(instanceId, nodeKey, node);
        }
        return hasPendingTasksOfNode(instanceId, nodeKey);
    }

    /**
     * 校验转办/委派目标人。
     *
     * @param targetUser 目标人账号
     * @return 去空白后的目标人
     * @throws BizException 为空时抛出 {@code PARAM_ERROR}
     */
    private static String requireTargetUser(String targetUser) {
        if (targetUser == null || targetUser.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "目标人不能为空");
        }
        return targetUser.trim();
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
     * 为推进抵达的审批节点创建待办任务（会签节点按 assignees 展开）。
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
            task.setSignPolicy(signPolicy == null || signPolicy.isBlank() ? POLICY_ALL : signPolicy);
        }
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
     * 从并行活动节点键串中移除指定节点键。
     *
     * @param activeNodeKeys 逗号分隔的活动节点键，可为 {@code null}
     * @param nodeKey 待移除节点键
     * @return 剩余键串（无剩余返回空串）
     */
    private static String removeActiveKey(String activeNodeKeys, String nodeKey) {
        if (activeNodeKeys == null || activeNodeKeys.isBlank()) {
            return "";
        }
        StringBuilder remaining = new StringBuilder();
        for (String part : activeNodeKeys.split(",")) {
            String key = part.trim();
            if (key.isEmpty() || key.equals(nodeKey)) {
                continue;
            }
            if (remaining.length() > 0) {
                remaining.append(',');
            }
            remaining.append(key);
        }
        return remaining.toString();
    }

    /**
     * 拼接并行活动节点键（逗号分隔）。
     *
     * @param nodes 审批节点列表
     * @return 逗号分隔的节点键
     */
    private static String joinKeys(List<ProcessModelNode> nodes) {
        return nodes.stream().map(ProcessModelNode::getKey)
                .collect(java.util.stream.Collectors.joining(","));
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
