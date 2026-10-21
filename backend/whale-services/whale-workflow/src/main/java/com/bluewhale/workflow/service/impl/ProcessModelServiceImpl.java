package com.bluewhale.workflow.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.dto.BpmnImportRequest;
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
import com.bluewhale.workflow.support.ValidationIssue;
import com.bluewhale.workflow.support.ValidationReport;
import com.bluewhale.workflow.support.bpmn.BpmnXmlParser;
import com.bluewhale.workflow.support.bpmn.BpmnXmlWriter;
import com.bluewhale.workflow.vo.ProcessDefinitionValidateVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ValidationIssueVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

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
