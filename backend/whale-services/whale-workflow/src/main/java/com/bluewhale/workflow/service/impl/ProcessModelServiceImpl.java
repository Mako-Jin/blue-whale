package com.bluewhale.workflow.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.dto.BpmnImportRequest;
import com.bluewhale.workflow.dto.DefinitionSimulateRequest;
import com.bluewhale.workflow.dto.DefinitionValidateRequest;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.service.ProcessModelService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessModelValidator;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ValidationIssue;
import com.bluewhale.workflow.support.ValidationReport;
import com.bluewhale.workflow.support.bpmn.BpmnXmlParser;
import com.bluewhale.workflow.support.bpmn.BpmnXmlWriter;
import com.bluewhale.workflow.support.machine.GuardContext;
import com.bluewhale.workflow.support.machine.StateMachine;
import com.bluewhale.workflow.support.machine.StateMachineEvent;
import com.bluewhale.workflow.support.machine.Transition;
import com.bluewhale.workflow.vo.ProcessDefinitionSimulateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionValidateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.SimulateStepVO;
import com.bluewhale.workflow.vo.ValidationIssueVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程模型服务实现：定义校验 / BPMN 导入 / BPMN 导出。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessModelServiceImpl implements ProcessModelService {

    /** JSON 字段：定义类型 */
    private static final String FIELD_TYPE = "type";

    /** JSON 字段：节点数组 */
    private static final String FIELD_NODES = "nodes";

    /** JSON 字段：流转数组 */
    private static final String FIELD_FLOWS = "flows";

    /** JSON 序列化器（Jackson 2，与 support 层一致） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单次模拟的最大步骤数（防御异常定义导致的路径爆炸） */
    private static final int MAX_SIMULATE_STEPS = 128;

    private final ProcessDefinitionService processDefinitionService;

    @Override
    public ProcessDefinitionValidateVO validate(DefinitionValidateRequest request) {
        if (StringUtils.hasText(request.getDefinitionJson())) {
            return validateJson(request.getDefinitionJson());
        }
        if (StringUtils.hasText(request.getBpmnXml())) {
            return validateBpmn(request.getBpmnXml());
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "definitionJson 与 bpmnXml 不能同时为空");
    }

    @Override
    public ProcessDefinitionSimulateVO simulate(DefinitionSimulateRequest request) {
        ValidationReport report = ProcessModelValidator.validate(request.getDefinitionJson());
        ProcessDefinitionSimulateVO vo = new ProcessDefinitionSimulateVO();
        vo.setValid(report.isValid());
        vo.setMode(report.getMode());
        vo.setErrors(new ArrayList<>(report.getErrors().stream()
                .map(ProcessModelServiceImpl::toIssueVo)
                .toList()));
        if (!report.isValid()) {
            vo.setTerminated(true);
            return vo;
        }
        ProcessModel model;
        try {
            model = ProcessModelParser.parse(request.getDefinitionJson());
        } catch (ProcessModelException e) {
            vo.setValid(false);
            vo.getErrors().add(toIssueVo(new ValidationIssue(
                    e.getValidationCode(), e.getMessage(), e.getRefKey())));
            vo.setTerminated(true);
            return vo;
        }
        Map<String, Object> variables = request.getVariables() == null ? Map.of() : request.getVariables();
        Simulation simulation = new Simulation(model, variables, vo.getSteps());
        try {
            SimulationOutcome outcome = simulation.walk(model.entryKey(), null);
            vo.setReachedEnd(outcome.reachedEnd());
            vo.setTerminalNodeKey(outcome.terminalKey());
        } catch (ProcessModelException e) {
            vo.setErrorCode(e.getValidationCode().name());
            vo.setErrorMessage(e.getMessage());
            vo.setTerminalNodeKey(simulation.currentKey());
        }
        vo.setTerminated(true);
        return vo;
    }

    @Override
    public ProcessDefinitionVO importBpmn(BpmnImportRequest request) {
        ProcessModel model = BpmnXmlParser.parse(request.getBpmnXml());
        ValidationReport report = ProcessModelValidator.validateModel(model, ValidationReport.MODE_BPMN);
        if (!report.isValid()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "BPMN 流程定义非法：" + report.getErrors().get(0).getCode().name());
        }
        ProcessDefinitionSaveRequest save = new ProcessDefinitionSaveRequest();
        save.setCode(request.getCode());
        save.setName(request.getName());
        save.setDefinitionJson(toJson(model));
        save.setVersion(request.getVersion());
        save.setStatus(request.getStatus());
        save.setRemark(request.getRemark());
        return processDefinitionService.create(save);
    }

    @Override
    public byte[] exportBpmn(Long id) {
        ProcessDefinitionVO definition = processDefinitionService.getDetail(id);
        ProcessModel model = ProcessModelParser.parse(definition.getDefinitionJson());
        String xml = BpmnXmlWriter.write(model, definition.getCode(), definition.getName());
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 节点 + 入边 → 模拟步骤视图对象。
     *
     * @param order 步骤序号（1 起）
     * @param node 节点
     * @param incoming 进入该节点的流转，首节点为 {@code null}
     * @return 模拟步骤
     */
    private static SimulateStepVO toStep(int order, ProcessModelNode node, ProcessModelFlow incoming) {
        SimulateStepVO step = new SimulateStepVO();
        step.setOrder(order);
        step.setNodeKey(node.getKey());
        step.setNodeName(node.getName());
        step.setNodeType(node.getType() == null ? null : node.getType().name());
        if (incoming != null) {
            step.setFlowKey(incoming.getKey());
            step.setCondition(incoming.getCondition());
            step.setIsDefault(Boolean.TRUE.equals(incoming.getIsDefault()));
        }
        if (node.getType() == ProcessNodeType.USER_TASK) {
            step.setAssignee(node.getAssignee());
        } else if (node.getType() == ProcessNodeType.COUNTER_SIGN) {
            step.setAssignees(node.getAssignees());
            step.setCounterSignPolicy(node.getCounterSignPolicy());
        }
        return step;
    }

    /**
     * 单次选路模拟的遍历器：持有流程模型、变量、状态机与已收集路径。
     *
     * <p>按真实选路规则（{@link StateMachine#pickTransition}）逐节点推进；并行网关多出流
     * 逐分支推进并按定义顺序合并结果；节点重入视为已合并（跳过），步骤数达到
     * {@link ProcessModelServiceImpl#MAX_SIMULATE_STEPS} 时终止。</p>
     *
     * @author BlueWhale
     */
    private static final class Simulation {

        /** 流程模型 */
        private final ProcessModel model;

        /** 流程变量 */
        private final Map<String, Object> variables;

        /** 状态机（复用真实选路逻辑） */
        private final StateMachine machine;

        /** 路径收集器 */
        private final List<SimulateStepVO> steps;

        /** 已访问节点（防重入） */
        private final Set<String> visited = new HashSet<>();

        /** 最近一次访问的节点键 */
        private String currentKey;

        /**
         * 构造模拟器。
         *
         * @param model 流程模型
         * @param variables 流程变量
         * @param steps 路径收集器
         */
        private Simulation(ProcessModel model, Map<String, Object> variables, List<SimulateStepVO> steps) {
            this.model = model;
            this.variables = variables;
            this.machine = new StateMachine(model, variables);
            this.steps = steps;
        }

        /**
         * 获取最近一次访问的节点键。
         *
         * @return 节点键；尚未访问任何节点时为 {@code null}
         */
        private String currentKey() {
            return currentKey;
        }

        /**
         * 从指定节点开始模拟推进。
         *
         * @param nodeKey 起始节点键
         * @param incoming 进入该节点的流转，首节点为 {@code null}
         * @return 推进结果
         */
        private SimulationOutcome walk(String nodeKey, ProcessModelFlow incoming) {
            ProcessModelNode node = model.node(nodeKey);
            if (node == null) {
                return SimulationOutcome.stopped(currentKey);
            }
            if (!visited.add(nodeKey)) {
                return SimulationOutcome.skippedResult();
            }
            if (steps.size() >= MAX_SIMULATE_STEPS) {
                return SimulationOutcome.stopped(nodeKey);
            }
            currentKey = nodeKey;
            steps.add(toStep(steps.size() + 1, node, incoming));
            if (node.getType() == ProcessNodeType.END_EVENT) {
                return SimulationOutcome.reachedEnd(nodeKey);
            }
            List<Transition> transitions = machine.transitions(nodeKey);
            if (transitions.isEmpty()) {
                return SimulationOutcome.stopped(nodeKey);
            }
            if (node.getType() == ProcessNodeType.PARALLEL_GATEWAY && transitions.size() > 1) {
                return walkParallel(transitions);
            }
            Transition picked = machine.pickTransition(nodeKey, guardContext(node));
            if (picked == null) {
                return SimulationOutcome.stopped(nodeKey);
            }
            return walk(picked.to(), picked.getFlow());
        }

        /**
         * 并行网关多出流：按定义顺序逐分支推进并合并结果。
         *
         * @param transitions 出流迁移列表
         * @return 合并后的推进结果
         */
        private SimulationOutcome walkParallel(List<Transition> transitions) {
            SimulationOutcome merged = null;
            for (Transition transition : transitions) {
                SimulationOutcome branch = walk(transition.to(), transition.getFlow());
                merged = merged == null ? branch : merged.merge(branch);
            }
            return merged == null ? SimulationOutcome.stopped(currentKey) : merged;
        }

        /**
         * 构造节点守卫上下文（审批节点用 APPROVE，其余用 COMPLETE）。
         *
         * @param node 当前节点
         * @return 守卫上下文
         */
        private GuardContext guardContext(ProcessModelNode node) {
            StateMachineEvent event = node.getType() != null && node.getType().isApproval()
                    ? StateMachineEvent.APPROVE : StateMachineEvent.COMPLETE;
            return GuardContext.of(model, node, event, variables);
        }
    }

    /**
     * 单条路径的推进结果。
     *
     * @param reachedEnd 是否抵达结束事件
     * @param skipped 是否因节点已访问而跳过
     * @param terminalKey 终止节点键，可为 {@code null}
     * @author BlueWhale
     */
    private record SimulationOutcome(boolean reachedEnd, boolean skipped, String terminalKey) {

        /**
         * 因节点已访问而跳过（该路径已由其它分支模拟）。
         *
         * @return 跳过结果
         */
        private static SimulationOutcome skippedResult() {
            return new SimulationOutcome(false, true, null);
        }

        /**
         * 抵达结束事件。
         *
         * @param key 结束事件节点键
         * @return 抵达结果
         */
        private static SimulationOutcome reachedEnd(String key) {
            return new SimulationOutcome(true, false, key);
        }

        /**
         * 停在指定节点（无出流 / 无匹配流转 / 超出步数上限）。
         *
         * @param key 停留节点键
         * @return 停止结果
         */
        private static SimulationOutcome stopped(String key) {
            return new SimulationOutcome(false, false, key);
        }

        /**
         * 合并并行分支结果：跳过分支不参与判定，其余以「全部抵达结束」为准。
         *
         * @param other 另一分支结果
         * @return 合并结果
         */
        private SimulationOutcome merge(SimulationOutcome other) {
            if (other == null || other.skipped) {
                return this;
            }
            if (skipped) {
                return other;
            }
            String terminal = other.terminalKey == null ? terminalKey : other.terminalKey;
            return new SimulationOutcome(reachedEnd && other.reachedEnd, false, terminal);
        }
    }

    /**
     * 校验 JSON 定义。
     *
     * @param definitionJson 定义 JSON 文本
     * @return 校验视图对象
     */
    private ProcessDefinitionValidateVO validateJson(String definitionJson) {
        ValidationReport report = ProcessModelValidator.validate(definitionJson);
        ProcessDefinitionValidateVO vo = toVo(report);
        if (report.isValid()) {
            try {
                ProcessModel model = ProcessModelParser.parse(definitionJson);
                fillModel(vo, model);
            } catch (ProcessModelException e) {
                vo.setValid(false);
                vo.getErrors().add(toIssueVo(new ValidationIssue(e.getValidationCode(), e.getMessage(), e.getRefKey())));
            }
        }
        return vo;
    }

    /**
     * 校验 BPMN XML。
     *
     * @param bpmnXml BPMN XML 文本
     * @return 校验视图对象
     */
    private ProcessDefinitionValidateVO validateBpmn(String bpmnXml) {
        try {
            ProcessModel model = BpmnXmlParser.parse(bpmnXml);
            ValidationReport report = ProcessModelValidator.validateModel(model, ValidationReport.MODE_BPMN);
            ProcessDefinitionValidateVO vo = toVo(report);
            if (report.isValid()) {
                fillModel(vo, model);
            }
            return vo;
        } catch (ProcessModelException e) {
            ValidationReport report = new ValidationReport();
            report.setMode(ValidationReport.MODE_BPMN);
            report.add(new ValidationIssue(e.getValidationCode(), e.getMessage(), e.getRefKey()));
            return toVo(report);
        }
    }

    /**
     * 校验报告 → 视图对象。
     *
     * @param report 校验报告
     * @return 校验视图对象
     */
    private ProcessDefinitionValidateVO toVo(ValidationReport report) {
        ProcessDefinitionValidateVO vo = new ProcessDefinitionValidateVO();
        vo.setValid(report.isValid());
        vo.setMode(report.getMode());
        vo.setErrors(report.getErrors().stream().map(ProcessModelServiceImpl::toIssueVo).toList());
        vo.setWarnings(report.getWarnings().stream().map(ProcessModelServiceImpl::toIssueVo).toList());
        return vo;
    }

    /**
     * 用流程模型填充视图对象的归一化 JSON 与计数。
     *
     * @param vo 校验视图对象
     * @param model 流程模型
     */
    private void fillModel(ProcessDefinitionValidateVO vo, ProcessModel model) {
        vo.setNormalizedJson(toJson(model));
        vo.setNodeCount(model.getNodes().size());
        vo.setFlowCount(model.getFlows().size());
    }

    /**
     * 问题条目 → 视图对象。
     *
     * @param issue 问题条目
     * @return 问题视图对象
     */
    private static ValidationIssueVO toIssueVo(ValidationIssue issue) {
        ValidationIssueVO vo = new ValidationIssueVO();
        vo.setCode(issue.getCode().name());
        vo.setMessage(issue.getMessage());
        vo.setLevel(issue.getLevel().name());
        vo.setRefKey(issue.getRefKey());
        return vo;
    }

    /**
     * 流程模型 → 规范化 JSON 文本（{@code {type,nodes[],flows[]}}）。
     *
     * @param model 流程模型
     * @return 规范化 JSON 文本
     */
    private static String toJson(ProcessModel model) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put(FIELD_TYPE, model.getDefinitionType() == null
                ? ProcessModelParser.TYPE_STATE_MACHINE : model.getDefinitionType());
        ArrayNode nodes = root.putArray(FIELD_NODES);
        for (ProcessModelNode node : model.getNodes()) {
            ObjectNode item = nodes.addObject();
            item.put("key", node.getKey());
            item.put(FIELD_TYPE, node.getType() == null ? null : node.getType().name());
            putIfPresent(item, "name", node.getName());
            putIfPresent(item, "assignee", node.getAssignee());
            putIfPresent(item, "formId", node.getFormId());
            putIfPresent(item, "formCode", node.getFormCode());
            if (node.getAssignees() != null && !node.getAssignees().isEmpty()) {
                ArrayNode assignees = item.putArray("assignees");
                node.getAssignees().forEach(assignees::add);
            }
            putIfPresent(item, "counterSignPolicy", node.getCounterSignPolicy());
            putIfPresentInt(item, "x", node.getX());
            putIfPresentInt(item, "y", node.getY());
        }
        ArrayNode flows = root.putArray(FIELD_FLOWS);
        for (ProcessModelFlow flow : model.getFlows()) {
            ObjectNode item = flows.addObject();
            putIfPresent(item, "key", flow.getKey());
            putIfPresent(item, "from", flow.getFrom());
            putIfPresent(item, "to", flow.getTo());
            putIfPresent(item, "condition", flow.getCondition());
            if (Boolean.TRUE.equals(flow.getIsDefault())) {
                item.put("isDefault", true);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "流程定义序列化失败");
        }
    }

    /**
     * 非空时写入文本字段。
     *
     * @param node 目标 JSON 对象
     * @param field 字段名
     * @param value 字段值
     */
    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    /**
     * 非空时写入整型字段。
     *
     * @param node 目标 JSON 对象
     * @param field 字段名
     * @param value 字段值，可为 {@code null}
     */
    private static void putIfPresentInt(ObjectNode node, String field, Integer value) {
        if (value != null) {
            node.put(field, value.intValue());
        }
    }
}
