package com.bluewhale.workflow.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProcessModelValidator} 校验报告产出单元测试（内容非法不抛异常）。
 *
 * @author BlueWhale
 */
class ProcessModelValidatorTest {

    /** 合法 legacy 定义 */
    private static final String LEGACY_VALID =
            "{\"nodes\":[{\"key\":\"n1\",\"assignee\":\"admin\"},{\"key\":\"n2\",\"assignee\":\"user\"}]}";

    /** 合法规范化定义（含网关默认分支与条件警告） */
    private static final String NORMALIZED_VALID =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"assignee\":\"user\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":["
                    + "{\"key\":\"f1\",\"from\":\"start\",\"to\":\"gw\"},"
                    + "{\"key\":\"f2\",\"from\":\"gw\",\"to\":\"t1\",\"condition\":\"amount>100\"},"
                    + "{\"key\":\"f3\",\"from\":\"gw\",\"to\":\"t2\",\"isDefault\":true},"
                    + "{\"key\":\"f4\",\"from\":\"t1\",\"to\":\"end\"},"
                    + "{\"key\":\"f5\",\"from\":\"t2\",\"to\":\"end\"}]}";

    /**
     * 判定报告错误集是否含指定校验码。
     *
     * @param report 校验报告
     * @param code 校验码
     * @return 含该码返回 {@code true}
     */
    private static boolean hasError(ValidationReport report, ValidationCode code) {
        return report.getErrors().stream().anyMatch(issue -> issue.getCode() == code);
    }

    @Test
    @DisplayName("空定义：mode=LEGACY 且报 MISSING_NODES")
    void shouldRejectBlank() {
        ValidationReport report = ProcessModelValidator.validate("   ");

        assertFalse(report.isValid());
        assertEquals(ValidationReport.MODE_LEGACY, report.getMode());
        assertTrue(hasError(report, ValidationCode.MISSING_NODES));
    }

    @Test
    @DisplayName("非 JSON 文本：报 MISSING_NODES 且不抛异常")
    void shouldRejectMalformed() {
        ValidationReport report = ProcessModelValidator.validate("not-a-json");

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.MISSING_NODES));
    }

    @Test
    @DisplayName("JSON 非对象：报 MISSING_NODES")
    void shouldRejectNonObject() {
        ValidationReport report = ProcessModelValidator.validate("[1,2,3]");

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.MISSING_NODES));
    }

    @Test
    @DisplayName("合法 legacy：valid=true 且 mode=LEGACY（跳过图级校验）")
    void shouldPassLegacy() {
        ValidationReport report = ProcessModelValidator.validate(LEGACY_VALID);

        assertTrue(report.isValid());
        assertEquals(ValidationReport.MODE_LEGACY, report.getMode());
    }

    @Test
    @DisplayName("合法规范化：valid=true 且无警告")
    void shouldPassNormalizedWithCondition() {
        ValidationReport report = ProcessModelValidator.validate(NORMALIZED_VALID);

        assertTrue(report.isValid());
        assertEquals(ValidationReport.MODE_NORMALIZED, report.getMode());
        assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("未知定义类型：报 UNKNOWN_DEFINITION_TYPE 且 mode=NORMALIZED")
    void shouldRejectUnknownType() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"bpmn\",\"nodes\":[{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}]}");

        assertFalse(report.isValid());
        assertEquals(ValidationReport.MODE_NORMALIZED, report.getMode());
        assertTrue(hasError(report, ValidationCode.UNKNOWN_DEFINITION_TYPE));
    }

    @Test
    @DisplayName("规范化缺 nodes：报 MISSING_NODES")
    void shouldRejectNormalizedMissingNodes() {
        ValidationReport report = ProcessModelValidator.validate("{\"type\":\"stateMachine\"}");

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.MISSING_NODES));
    }

    @Test
    @DisplayName("节点 key 重复：报 NODE_KEY_DUPLICATE")
    void shouldRejectDuplicateNodeKey() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"a\",\"type\":\"START_EVENT\"},{\"key\":\"a\",\"type\":\"END_EVENT\"}]}");

        assertTrue(hasError(report, ValidationCode.NODE_KEY_DUPLICATE));
    }

    @Test
    @DisplayName("流转 key 重复：报 FLOW_KEY_DUPLICATE")
    void shouldRejectDuplicateFlowKey() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                        + "{\"key\":\"f1\",\"from\":\"t1\",\"to\":\"end\"}]}");

        assertTrue(hasError(report, ValidationCode.FLOW_KEY_DUPLICATE));
    }

    @Test
    @DisplayName("流转引用缺失节点：报 FLOW_REFERENCE_MISSING")
    void shouldRejectFlowReferenceMissing() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"ghost\"}]}");

        assertTrue(hasError(report, ValidationCode.FLOW_REFERENCE_MISSING));
    }

    @Test
    @DisplayName("流转自环：报 FLOW_SELF_LOOP")
    void shouldRejectSelfLoop() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"start\"}]}");

        assertTrue(hasError(report, ValidationCode.FLOW_SELF_LOOP));
    }

    @Test
    @DisplayName("网关无出流：报 GATEWAY_NO_OUTGOING")
    void shouldRejectGatewayNoOutgoing() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"gw\"}]}");

        assertTrue(hasError(report, ValidationCode.GATEWAY_NO_OUTGOING));
    }

    @Test
    @DisplayName("网关多分支缺默认流转：报 GATEWAY_MULTI_BRANCH_NO_DEFAULT")
    void shouldRejectGatewayMultiBranchNoDefault() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"gw\",\"type\":\"EXCLUSIVE_GATEWAY\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"assignee\":\"b\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"gw\"},"
                        + "{\"key\":\"f2\",\"from\":\"gw\",\"to\":\"t1\"},{\"key\":\"f3\",\"from\":\"gw\",\"to\":\"t2\"},"
                        + "{\"key\":\"f4\",\"from\":\"t1\",\"to\":\"end\"},{\"key\":\"f5\",\"from\":\"t2\",\"to\":\"end\"}]}");

        assertTrue(hasError(report, ValidationCode.GATEWAY_MULTI_BRANCH_NO_DEFAULT));
    }

    @Test
    @DisplayName("缺少结束事件：报 END_EVENT_REQUIRED")
    void shouldRejectMissingEndEvent() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"}]}");

        assertTrue(hasError(report, ValidationCode.END_EVENT_REQUIRED));
    }

    @Test
    @DisplayName("开始事件数量不为 1：报 START_EVENT_COUNT")
    void shouldRejectStartEventCount() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"t1\",\"to\":\"end\"}]}");

        assertTrue(hasError(report, ValidationCode.START_EVENT_COUNT));
    }

    @Test
    @DisplayName("悬空节点：报 DEAD_END")
    void shouldRejectDeadEnd() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"}]}");

        assertTrue(hasError(report, ValidationCode.DEAD_END));
    }

    @Test
    @DisplayName("不可达节点：报 UNREACHABLE_NODE")
    void shouldRejectUnreachableNode() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"ghost\",\"type\":\"USER_TASK\",\"assignee\":\"b\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                        + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}");

        assertTrue(hasError(report, ValidationCode.UNREACHABLE_NODE));
    }

    @Test
    @DisplayName("成环：报 CYCLE_DETECTED")
    void shouldRejectCycle() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"},"
                        + "{\"key\":\"t2\",\"type\":\"USER_TASK\",\"assignee\":\"b\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                        + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"t2\"},{\"key\":\"f3\",\"from\":\"t2\",\"to\":\"t1\"},"
                        + "{\"key\":\"f4\",\"from\":\"t2\",\"to\":\"end\"}]}");

        assertTrue(hasError(report, ValidationCode.CYCLE_DETECTED));
    }

    @Test
    @DisplayName("节点数超上限：报 TOO_MANY_NODES")
    void shouldRejectTooManyNodes() {
        StringBuilder json = new StringBuilder("{\"type\":\"stateMachine\",\"nodes\":[");
        for (int i = 0; i <= ProcessModelParser.MAX_NODES; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"key\":\"k").append(i).append("\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}");
        }
        json.append("]}");

        ValidationReport report = ProcessModelValidator.validate(json.toString());

        assertTrue(hasError(report, ValidationCode.TOO_MANY_NODES));
    }

    @Test
    @DisplayName("validateModel：BPMN 模型合法返回 valid")
    void shouldValidateModel() {
        ProcessModel model = ProcessModelParser.parse(NORMALIZED_VALID);

        ValidationReport report = ProcessModelValidator.validateModel(model, ValidationReport.MODE_BPMN);

        assertTrue(report.isValid());
        assertEquals(ValidationReport.MODE_BPMN, report.getMode());
    }

    @Test
    @DisplayName("validateModel：空模型报 MISSING_NODES")
    void shouldRejectEmptyModel() {
        ValidationReport report = ProcessModelValidator.validateModel(new ProcessModel(), ValidationReport.MODE_BPMN);

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.MISSING_NODES));
    }

    /**
     * 构造「开始 → 审批（可选表单绑定）→ 结束」的规范化定义 JSON。
     *
     * @param formFragment 审批节点上的表单绑定片段，可为空串
     * @return 规范化定义 JSON
     */
    private static String userTaskFormJson(String formFragment) {
        return "{\"type\":\"stateMachine\",\"nodes\":["
                + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"admin\"" + formFragment + "},"
                + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";
    }

    @Test
    @DisplayName("审批节点表单绑定成对且编码合法：valid=true")
    void shouldPassUserTaskFormBinding() {
        ValidationReport report = ProcessModelValidator.validate(userTaskFormJson(
                ",\"formId\":\"2112355156699869186\",\"formCode\":\"bw_d29_form\""));

        assertTrue(report.isValid());
        assertFalse(hasError(report, ValidationCode.USER_TASK_FORM_CODE_INVALID));
        assertFalse(hasError(report, ValidationCode.USER_TASK_FORM_INCOMPLETE));
    }

    @Test
    @DisplayName("审批节点 formCode 非法字符：报 USER_TASK_FORM_CODE_INVALID")
    void shouldRejectInvalidFormCode() {
        ValidationReport report = ProcessModelValidator.validate(userTaskFormJson(
                ",\"formId\":\"211\",\"formCode\":\"bad form\""));

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.USER_TASK_FORM_CODE_INVALID));
    }

    @Test
    @DisplayName("审批节点仅 formId 非空：报 USER_TASK_FORM_INCOMPLETE")
    void shouldRejectFormBindingOnlyFormId() {
        ValidationReport report = ProcessModelValidator.validate(userTaskFormJson(",\"formId\":\"211\""));

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.USER_TASK_FORM_INCOMPLETE));
    }

    @Test
    @DisplayName("审批节点仅 formCode 非空：报 USER_TASK_FORM_INCOMPLETE")
    void shouldRejectFormBindingOnlyFormCode() {
        ValidationReport report = ProcessModelValidator.validate(userTaskFormJson(",\"formCode\":\"bw_form\""));

        assertFalse(report.isValid());
        assertTrue(hasError(report, ValidationCode.USER_TASK_FORM_INCOMPLETE));
    }

    @Test
    @DisplayName("非审批节点携带表单字段：不报错（仅被忽略）")
    void shouldIgnoreFormFieldsOnNonUserTask() {
        ValidationReport report = ProcessModelValidator.validate(
                "{\"type\":\"stateMachine\",\"nodes\":["
                        + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"formId\":\"211\",\"formCode\":\"bad form\"},"
                        + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"admin\"},"
                        + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                        + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                        + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}");

        assertTrue(report.isValid());
        assertFalse(hasError(report, ValidationCode.USER_TASK_FORM_CODE_INVALID));
        assertFalse(hasError(report, ValidationCode.USER_TASK_FORM_INCOMPLETE));
    }

    @Test
    @DisplayName("validateModel：节点 key 重复报 NODE_KEY_DUPLICATE")
    void shouldRejectDuplicateModelNodeKeys() {
        ProcessModel model = new ProcessModel();
        ProcessModelNode first = new ProcessModelNode();
        first.setKey("t1");
        first.setType(ProcessNodeType.USER_TASK);
        ProcessModelNode duplicate = new ProcessModelNode();
        duplicate.setKey("t1");
        duplicate.setType(ProcessNodeType.USER_TASK);
        model.getNodes().add(first);
        model.getNodes().add(duplicate);

        ValidationReport report = ProcessModelValidator.validateModel(model, ValidationReport.MODE_BPMN);

        assertTrue(hasError(report, ValidationCode.NODE_KEY_DUPLICATE));
    }

    @Test
    @DisplayName("validateModel：流转 key 缺失报 FLOW_KEY_MISSING")
    void shouldRejectModelFlowWithoutKey() {
        ProcessModel model = new ProcessModel();
        ProcessModelNode start = new ProcessModelNode();
        start.setKey("start");
        start.setType(ProcessNodeType.START_EVENT);
        ProcessModelNode end = new ProcessModelNode();
        end.setKey("end");
        end.setType(ProcessNodeType.END_EVENT);
        model.getNodes().add(start);
        model.getNodes().add(end);
        ProcessModelFlow flow = new ProcessModelFlow();
        flow.setFrom("start");
        flow.setTo("end");
        model.getFlows().add(flow);

        ValidationReport report = ProcessModelValidator.validateModel(model, ValidationReport.MODE_BPMN);

        assertTrue(hasError(report, ValidationCode.FLOW_KEY_MISSING));
    }
}
