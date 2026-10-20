package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProcessModelParser} 解析、模式判定与失败定位单元测试。
 *
 * @author BlueWhale
 */
class ProcessModelParserTest {

    /** legacy 两节点定义 */
    private static final String LEGACY_TWO_NODE =
            "{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"n2\",\"name\":\"二级审批\",\"assignee\":\"user\"}]}";

    /** 规范化三节点定义（开始事件 / 审批 / 结束事件） */
    private static final String NORMALIZED_MODEL =
            "{\"type\":\"stateMachine\",\"nodes\":["
                    + "{\"key\":\"start\",\"type\":\"START_EVENT\"},"
                    + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"name\":\"审批\",\"assignee\":\"admin\"},"
                    + "{\"key\":\"end\",\"type\":\"END_EVENT\"}],"
                    + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
                    + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";

    @Test
    @DisplayName("legacy：两节点归一化为顺序审批并补顺序流")
    void shouldParseLegacyTwoNode() {
        ProcessModel model = ProcessModelParser.parse(LEGACY_TWO_NODE);

        assertTrue(model.isLegacy());
        assertNull(model.getDefinitionType());
        assertEquals(2, model.getNodes().size());
        assertEquals("n1", model.getNodes().get(0).getKey());
        assertEquals(ProcessNodeType.USER_TASK, model.getNodes().get(0).getType());
        assertEquals("一级审批", model.getNodes().get(0).getName());
        assertEquals(1, model.getFlows().size());
        assertEquals("f1", model.getFlows().get(0).getKey());
        assertEquals("n1", model.getFlows().get(0).getFrom());
        assertEquals("n2", model.getFlows().get(0).getTo());
        assertEquals("n1", model.entryKey());
        assertEquals("n2", model.node("n2").getKey());
        assertEquals(1, model.indexOf("n2"));
        assertEquals("n2", model.nodeAt(1).getKey());
    }

    @Test
    @DisplayName("legacy：缺省 key 自动补 n{i}")
    void shouldSynthesizeKeyForLegacy() {
        ProcessModel model = ProcessModelParser.parse("{\"nodes\":[{\"name\":\"一级\",\"assignee\":\"admin\"}]}");

        assertEquals("n1", model.getNodes().get(0).getKey());
    }

    @Test
    @DisplayName("legacy：nodes 为空沿用「流程定义缺少有效审批节点」且 code=400")
    void shouldRejectLegacyEmptyNodes() {
        BizException ex = assertThrows(BizException.class, () -> ProcessModelParser.parse("{\"nodes\":[]}"));

        assertEquals(400, ex.getCode());
        assertEquals("流程定义缺少有效审批节点", ex.getMessage());
    }

    @Test
    @DisplayName("legacy：非 JSON 文本沿用既有文案")
    void shouldRejectLegacyMalformed() {
        BizException ex = assertThrows(BizException.class, () -> ProcessModelParser.parse("not-a-json"));

        assertEquals(400, ex.getCode());
        assertEquals("流程定义缺少有效审批节点", ex.getMessage());
    }

    @Test
    @DisplayName("normalized：stateMachine 定义解析为节点与流转")
    void shouldParseNormalizedModel() {
        ProcessModel model = ProcessModelParser.parse(NORMALIZED_MODEL);

        assertFalse(model.isLegacy());
        assertEquals(ProcessModelParser.TYPE_STATE_MACHINE, model.getDefinitionType());
        assertEquals(3, model.getNodes().size());
        assertEquals(ProcessNodeType.START_EVENT, model.getNodes().get(0).getType());
        assertEquals(ProcessNodeType.END_EVENT, model.getNodes().get(2).getType());
        assertEquals(2, model.getFlows().size());
        assertEquals("start", model.entryKey());
        assertEquals(1, model.outgoing("start").size());
        assertEquals(1, model.outgoing("t1").size());
    }

    @Test
    @DisplayName("normalized：type 缺失但存在 flows 视为规范化")
    void shouldTreatFlowsWithoutTypeAsNormalized() {
        ProcessModel model = ProcessModelParser.parse(
                "{\"nodes\":[{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}],\"flows\":[]}");

        assertFalse(model.isLegacy());
        assertEquals(ProcessModelParser.TYPE_STATE_MACHINE, model.getDefinitionType());
        assertEquals(1, model.getNodes().size());
    }

    @Test
    @DisplayName("normalized：未知定义类型抛 UNKNOWN_DEFINITION_TYPE")
    void shouldRejectUnknownDefinitionType() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> ProcessModelParser.parse(
                "{\"type\":\"bpmn\",\"nodes\":[{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}]}"));

        assertEquals(400, ex.getCode());
        assertEquals(ValidationCode.UNKNOWN_DEFINITION_TYPE, ex.getValidationCode());
    }

    @Test
    @DisplayName("normalized：nodes 缺失抛 MISSING_NODES")
    void shouldRejectNormalizedMissingNodes() {
        ProcessModelException ex = assertThrows(ProcessModelException.class,
                () -> ProcessModelParser.parse("{\"type\":\"stateMachine\"}"));

        assertEquals(ValidationCode.MISSING_NODES, ex.getValidationCode());
        assertEquals("流程定义缺少有效审批节点", ex.getMessage());
    }

    @Test
    @DisplayName("normalized：节点缺 key 抛 NODE_KEY_MISSING")
    void shouldRejectNodeKeyMissing() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":[{\"type\":\"USER_TASK\",\"assignee\":\"a\"}]}"));

        assertEquals(ValidationCode.NODE_KEY_MISSING, ex.getValidationCode());
    }

    @Test
    @DisplayName("normalized：节点 key 含非法字符抛 NODE_KEY_INVALID")
    void shouldRejectNodeKeyInvalid() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":[{\"key\":\"bad key\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}]}"));

        assertEquals(ValidationCode.NODE_KEY_INVALID, ex.getValidationCode());
        assertEquals("bad key", ex.getRefKey());
    }

    @Test
    @DisplayName("normalized：未知节点类型抛 UNKNOWN_NODE_TYPE")
    void shouldRejectUnknownNodeType() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":[{\"key\":\"t1\",\"type\":\"serviceTask\"}]}"));

        assertEquals(ValidationCode.UNKNOWN_NODE_TYPE, ex.getValidationCode());
        assertEquals("t1", ex.getRefKey());
    }

    @Test
    @DisplayName("normalized：审批节点缺 assignee 抛 USER_TASK_ASSIGNEE_REQUIRED")
    void shouldRejectUserTaskWithoutAssignee() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":[{\"key\":\"t1\",\"type\":\"USER_TASK\"}]}"));

        assertEquals(ValidationCode.USER_TASK_ASSIGNEE_REQUIRED, ex.getValidationCode());
        assertEquals("流程定义缺少有效审批节点", ex.getMessage());
    }

    @Test
    @DisplayName("normalized：节点数超过上限抛 TOO_MANY_NODES")
    void shouldRejectTooManyNodes() {
        StringBuilder json = new StringBuilder("{\"type\":\"stateMachine\",\"nodes\":[");
        for (int i = 0; i <= ProcessModelParser.MAX_NODES; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"key\":\"k").append(i).append("\",\"type\":\"USER_TASK\",\"assignee\":\"a\"}");
        }
        json.append("]}");

        ProcessModelException ex = assertThrows(ProcessModelException.class,
                () -> ProcessModelParser.parse(json.toString()));

        assertEquals(ValidationCode.TOO_MANY_NODES, ex.getValidationCode());
    }

    @Test
    @DisplayName("节点类型解析：大小写与连字符不敏感，未知返回 null")
    void shouldParseNodeTypeLoosely() {
        assertEquals(ProcessNodeType.START_EVENT, ProcessNodeType.from("start_event"));
        assertEquals(ProcessNodeType.EXCLUSIVE_GATEWAY, ProcessNodeType.from("Exclusive-Gateway"));
        assertNull(ProcessNodeType.from("unknown"));
        assertNull(ProcessNodeType.from(null));
    }

    @Test
    @DisplayName("normalized：formId/formCode/x/y 四字段被读入")
    void shouldReadFormAndCoordinates() {
        ProcessModel model = ProcessModelParser.parse(
                "{\"type\":\"stateMachine\",\"nodes\":[{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"a\","
                        + "\"formId\":\"2112355156699869186\",\"formCode\":\"bw_d29_form\",\"x\":320,\"y\":180}]}");

        ProcessModelNode node = model.getNodes().get(0);
        assertEquals("2112355156699869186", node.getFormId());
        assertEquals("bw_d29_form", node.getFormCode());
        assertEquals(320, node.getX().intValue());
        assertEquals(180, node.getY().intValue());
    }

    @Test
    @DisplayName("normalized：缺失新字段时为 null")
    void shouldLeaveFormAndCoordinatesNullWhenAbsent() {
        ProcessModelNode node = ProcessModelParser.parse(NORMALIZED_MODEL).node("t1");

        assertNull(node.getFormId());
        assertNull(node.getFormCode());
        assertNull(node.getX());
        assertNull(node.getY());
    }

    @Test
    @DisplayName("legacy：归一化不带 formId/formCode/x/y（保持 null）")
    void shouldNotCarryFormFieldsForLegacy() {
        ProcessModelNode node = ProcessModelParser.parse(LEGACY_TWO_NODE).getNodes().get(0);

        assertNull(node.getFormId());
        assertNull(node.getFormCode());
        assertNull(node.getX());
        assertNull(node.getY());
    }
}
