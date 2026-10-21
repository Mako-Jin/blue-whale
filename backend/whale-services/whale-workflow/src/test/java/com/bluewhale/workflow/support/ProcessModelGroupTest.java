package com.bluewhale.workflow.support;

import com.bluewhale.workflow.support.bpmn.BpmnXmlParser;
import com.bluewhale.workflow.support.bpmn.BpmnXmlWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 画布分组 / 泳道：JSON 解析、校验码与 BPMN 往返单元测试。
 *
 * @author BlueWhale
 */
class ProcessModelGroupTest {

    /** 含分组的规范化定义（start/t1 归属 g1） */
    private static final String GROUPED_JSON = "{\"type\":\"stateMachine\","
            + "\"groups\":[{\"key\":\"g1\",\"name\":\"财务\"}],"
            + "\"nodes\":["
            + "{\"key\":\"start\",\"type\":\"START_EVENT\",\"x\":80,\"y\":60,\"groupKey\":\"g1\"},"
            + "{\"key\":\"t1\",\"type\":\"USER_TASK\",\"assignee\":\"admin\",\"x\":220,\"y\":60,\"groupKey\":\"g1\"},"
            + "{\"key\":\"end\",\"type\":\"END_EVENT\",\"x\":440,\"y\":60}],"
            + "\"flows\":[{\"key\":\"f1\",\"from\":\"start\",\"to\":\"t1\"},"
            + "{\"key\":\"f2\",\"from\":\"t1\",\"to\":\"end\"}]}";

    @Test
    @DisplayName("parse：读取 groups 与节点 groupKey")
    void shouldParseGroups() {
        ProcessModel model = ProcessModelParser.parse(GROUPED_JSON);

        assertEquals(1, model.getGroups().size());
        assertNotNull(model.group("g1"));
        assertEquals("财务", model.group("g1").getName());
        assertEquals("g1", model.node("start").getGroupKey());
        assertEquals("g1", model.node("t1").getGroupKey());
        assertNull(model.node("end").getGroupKey());
    }

    @Test
    @DisplayName("validate：分组 key 重复报 GROUP_KEY_DUPLICATE")
    void shouldRejectDuplicateGroupKey() {
        String json = GROUPED_JSON.replace("\"groups\":[{\"key\":\"g1\",\"name\":\"财务\"}]",
                "\"groups\":[{\"key\":\"g1\"},{\"key\":\"g1\"}]");

        ValidationReport report = ProcessModelValidator.validate(json);

        assertTrue(hasCode(report, ValidationCode.GROUP_KEY_DUPLICATE));
    }

    @Test
    @DisplayName("validate：分组 key 非法报 GROUP_KEY_INVALID")
    void shouldRejectInvalidGroupKey() {
        String json = GROUPED_JSON.replace("\"groups\":[{\"key\":\"g1\",\"name\":\"财务\"}]",
                "\"groups\":[{\"key\":\"g 1\"}]");

        ValidationReport report = ProcessModelValidator.validate(json);

        assertTrue(hasCode(report, ValidationCode.GROUP_KEY_INVALID));
    }

    @Test
    @DisplayName("validate：节点引用未知分组报 NODE_GROUP_UNKNOWN")
    void shouldRejectUnknownGroupReference() {
        String json = GROUPED_JSON.replace("\"groups\":[{\"key\":\"g1\",\"name\":\"财务\"}]",
                "\"groups\":[]");

        ValidationReport report = ProcessModelValidator.validate(json);

        assertTrue(hasCode(report, ValidationCode.NODE_GROUP_UNKNOWN));
        assertFalse(report.isValid());
    }

    @Test
    @DisplayName("BPMN：分组导出为 laneSet/lane 后可回读")
    void shouldRoundTripLanes() {
        ProcessModel model = ProcessModelParser.parse(GROUPED_JSON);

        String xml = BpmnXmlWriter.write(model, "p1", "分组流程");
        ProcessModel restored = BpmnXmlParser.parse(xml);

        assertTrue(xml.contains("<laneSet id=\"LaneSet_p1\">"));
        assertTrue(xml.contains("<lane id=\"g1\" name=\"财务\">"));
        assertTrue(xml.contains("<flowNodeRef>t1</flowNodeRef>"));
        assertEquals(1, restored.getGroups().size());
        assertEquals("财务", restored.group("g1").getName());
        assertEquals("g1", restored.node("start").getGroupKey());
        assertEquals("g1", restored.node("t1").getGroupKey());
    }

    /**
     * 判断校验报告是否包含指定校验码。
     *
     * @param report 校验报告
     * @param code 校验码
     * @return 命中返回 {@code true}
     */
    private static boolean hasCode(ValidationReport report, ValidationCode code) {
        return report.getErrors().stream().anyMatch(issue -> issue.getCode() == code);
    }
}
