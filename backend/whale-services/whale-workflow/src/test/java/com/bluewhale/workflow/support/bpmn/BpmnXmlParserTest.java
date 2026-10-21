package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ValidationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BpmnXmlParser} 映射与不支持元素拦截单元测试。
 *
 * @author BlueWhale
 */
class BpmnXmlParserTest {

    /** 基础 BPMN：开始 → 审批 → 结束 */
    private static final String BASIC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
            + "<process id=\"p1\" name=\"报销\">"
            + "<startEvent id=\"start\"/>"
            + "<userTask id=\"t1\" name=\"审批\" assignee=\"admin\"/>"
            + "<endEvent id=\"end\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"end\"/>"
            + "</process></definitions>";

    @Test
    @DisplayName("parse：基础 BPMN 映射为 3 节点 2 流转")
    void shouldParseBasic() {
        ProcessModel model = BpmnXmlParser.parse(BASIC);

        assertEquals(ProcessModelParser.TYPE_STATE_MACHINE, model.getDefinitionType());
        assertEquals(3, model.getNodes().size());
        assertEquals(ProcessNodeType.START_EVENT, model.getNodes().get(0).getType());
        assertEquals(ProcessNodeType.USER_TASK, model.getNodes().get(1).getType());
        assertEquals("admin", model.getNodes().get(1).getAssignee());
        assertEquals(ProcessNodeType.END_EVENT, model.getNodes().get(2).getType());
        assertEquals(2, model.getFlows().size());
        assertEquals("start", model.getFlows().get(0).getFrom());
        assertEquals("start", model.entryKey());
    }

    @Test
    @DisplayName("parse：任意前缀的 assignee 属性可识别")
    void shouldParsePrefixedAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><userTask id=\"t1\" flowable:assignee=\"admin\"/></process></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals("admin", model.getNodes().get(0).getAssignee());
    }

    @Test
    @DisplayName("parse：网关 default 属性映射为默认流转")
    void shouldParseGatewayDefault() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\">"
                + "<startEvent id=\"start\"/><exclusiveGateway id=\"gw\" default=\"f3\"/>"
                + "<userTask id=\"t1\" assignee=\"a\"/><userTask id=\"t2\" assignee=\"b\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"gw\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"gw\" targetRef=\"t1\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"gw\" targetRef=\"t2\"/>"
                + "<sequenceFlow id=\"f4\" sourceRef=\"t1\" targetRef=\"end\"/>"
                + "<sequenceFlow id=\"f5\" sourceRef=\"t2\" targetRef=\"end\"/>"
                + "</process></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals(ProcessNodeType.EXCLUSIVE_GATEWAY, model.node("gw").getType());
        assertTrue(Boolean.TRUE.equals(model.outgoing("gw").get(1).getIsDefault()));
        assertFalse(Boolean.TRUE.equals(model.outgoing("gw").get(0).getIsDefault()));
    }

    @Test
    @DisplayName("parse：conditionExpression 读为流转条件")
    void shouldParseCondition() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\">"
                + "<conditionExpression>amount&gt;100</conditionExpression></sequenceFlow>"
                + "</process></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals("amount>100", model.getFlows().get(0).getCondition());
    }

    @Test
    @DisplayName("parse：documentation 等忽略元素不影响解析")
    void shouldIgnoreDocumentation() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><documentation>说明</documentation>"
                + "<startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/>"
                + "</process></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals(2, model.getNodes().size());
        assertEquals(1, model.getFlows().size());
    }

    @Test
    @DisplayName("parse：不支持元素抛 BPMN_UNSUPPORTED_ELEMENT 并点名")
    void shouldRejectUnsupportedElement() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><serviceTask id=\"s1\"/></process></definitions>";

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlParser.parse(xml));

        assertEquals(ValidationCode.BPMN_UNSUPPORTED_ELEMENT, ex.getValidationCode());
        assertEquals("serviceTask", ex.getRefKey());
    }

    @Test
    @DisplayName("parse：根元素非法抛 BPMN_ROOT_INVALID")
    void shouldRejectInvalidRoot() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlParser.parse("<foo/>"));

        assertEquals(ValidationCode.BPMN_ROOT_INVALID, ex.getValidationCode());
    }

    @Test
    @DisplayName("parse：process 数量不为 1 抛 BPMN_PROCESS_COUNT")
    void shouldRejectProcessCount() {
        String none = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";
        String two = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"p1\"/><process id=\"p2\"/></definitions>";

        assertEquals(ValidationCode.BPMN_PROCESS_COUNT,
                assertThrows(ProcessModelException.class, () -> BpmnXmlParser.parse(none)).getValidationCode());
        assertEquals(ValidationCode.BPMN_PROCESS_COUNT,
                assertThrows(ProcessModelException.class, () -> BpmnXmlParser.parse(two)).getValidationCode());
    }

    @Test
    @DisplayName("parse：节点缺 id 抛 NODE_KEY_MISSING")
    void shouldRejectNodeWithoutId() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"p1\"><userTask assignee=\"a\"/></process></definitions>";

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlParser.parse(xml));

        assertEquals(ValidationCode.NODE_KEY_MISSING, ex.getValidationCode());
    }

    @Test
    @DisplayName("parse：直接以 process 为根亦可解析")
    void shouldParseProcessRoot() {
        String xml = "<process xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"p1\">"
                + "<startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/></process>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals(2, model.getNodes().size());
        assertNull(model.node("missing"));
    }

    @Test
    @DisplayName("parse：BPMN-DI 的 BPMNShape/dc:Bounds 回读为节点 x/y")
    void shouldParseDiagramCoordinates() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/></process>"
                + "<bpmndi:BPMNDiagram id=\"d1\"><bpmndi:BPMNPlane id=\"pl1\" bpmnElement=\"p1\">"
                + "<bpmndi:BPMNShape id=\"s1\" bpmnElement=\"start\"><dc:Bounds x=\"100\" y=\"80\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>"
                + "<bpmndi:BPMNShape id=\"s2\" bpmnElement=\"t1\"><dc:Bounds x=\"240.6\" y=\"99.4\" width=\"120\" height=\"60\"/></bpmndi:BPMNShape>"
                + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertEquals(Integer.valueOf(100), model.node("start").getX());
        assertEquals(Integer.valueOf(80), model.node("start").getY());
        assertEquals(Integer.valueOf(241), model.node("t1").getX());
        assertEquals(Integer.valueOf(99), model.node("t1").getY());
    }

    @Test
    @DisplayName("parse：DI 形状缺失/引用未知节点/坐标非法时静默忽略")
    void shouldIgnoreInvalidDiagramShapes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/></process>"
                + "<bpmndi:BPMNDiagram id=\"d1\"><bpmndi:BPMNPlane id=\"pl1\" bpmnElement=\"p1\">"
                + "<bpmndi:BPMNShape id=\"s1\" bpmnElement=\"missing\"><dc:Bounds x=\"10\" y=\"20\"/></bpmndi:BPMNShape>"
                + "<bpmndi:BPMNShape id=\"s2\" bpmnElement=\"t1\"/>"
                + "<bpmndi:BPMNShape id=\"s3\" bpmnElement=\"start\"><dc:Bounds x=\"abc\" y=\"20\"/></bpmndi:BPMNShape>"
                + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></definitions>";

        ProcessModel model = BpmnXmlParser.parse(xml);

        assertNull(model.node("t1").getX());
        assertNull(model.node("t1").getY());
        assertNull(model.node("start").getX());
        assertEquals(Integer.valueOf(20), model.node("start").getY());
    }

    @Test
    @DisplayName("parse：userTask 的 plain formKey/formId 映射为 formCode/formId")
    void shouldParsePlainFormAttributes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/>"
                + "<userTask id=\"t1\" assignee=\"a\" formKey=\"bw_d29_form\" formId=\"2112355156699869186\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/></process></definitions>";

        ProcessModelNode node = BpmnXmlParser.parse(xml).node("t1");

        assertEquals("bw_d29_form", node.getFormCode());
        assertEquals("2112355156699869186", node.getFormId());
    }

    @Test
    @DisplayName("parse：任意前缀的 formKey/formId 属性可识别")
    void shouldParsePrefixedFormAttributes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><userTask id=\"t1\" assignee=\"a\""
                + " flowable:formKey=\"bw_form\" flowable:formId=\"211\"/></process></definitions>";

        ProcessModelNode node = BpmnXmlParser.parse(xml).node("t1");

        assertEquals("bw_form", node.getFormCode());
        assertEquals("211", node.getFormId());
    }

    @Test
    @DisplayName("parse：userTask 无表单属性时 formCode/formId 为 null")
    void shouldLeaveFormAttributesNullWhenAbsent() {
        ProcessModelNode node = BpmnXmlParser.parse(BASIC).node("t1");

        assertNull(node.getFormCode());
        assertNull(node.getFormId());
    }
}
