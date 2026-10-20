package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BpmnXmlWriter} 序列化与往返一致性单元测试。
 *
 * @author BlueWhale
 */
class BpmnXmlWriterTest {

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
    @DisplayName("write：输出 definitions/process/sequenceFlow 与 assignee")
    void shouldWriteBasic() {
        ProcessModel model = BpmnXmlParser.parse(BASIC);

        String xml = BpmnXmlWriter.write(model, "p1", "报销");

        assertTrue(xml.contains("<definitions"));
        assertTrue(xml.contains("<process id=\"p1\""));
        assertTrue(xml.contains("<userTask id=\"t1\""));
        assertTrue(xml.contains("assignee=\"admin\""));
        assertTrue(xml.contains("<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\"/>"));
        assertTrue(xml.contains("<endEvent id=\"end\"/>"));
    }

    @Test
    @DisplayName("write：属性值特殊字符被正确转义")
    void shouldEscapeAttribute() {
        ProcessModel model = new ProcessModel();
        ProcessModelNode node = new ProcessModelNode();
        node.setKey("t1");
        node.setType(ProcessNodeType.USER_TASK);
        node.setName("a&b<c");
        node.setAssignee("admin");
        model.getNodes().add(node);
        model.reindex();

        String xml = BpmnXmlWriter.write(model, "p1", "示例\"x\"");

        assertTrue(xml.contains("name=\"a&amp;b&lt;c\""));
        assertTrue(xml.contains("name=\"示例&quot;x&quot;\""));
    }

    @Test
    @DisplayName("write：网关默认流转写出 default 属性")
    void shouldWriteGatewayDefault() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><exclusiveGateway id=\"gw\" default=\"f3\"/>"
                + "<userTask id=\"t1\" assignee=\"a\"/><userTask id=\"t2\" assignee=\"b\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"gw\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"gw\" targetRef=\"t1\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"gw\" targetRef=\"t2\"/>"
                + "<sequenceFlow id=\"f4\" sourceRef=\"t1\" targetRef=\"end\"/>"
                + "<sequenceFlow id=\"f5\" sourceRef=\"t2\" targetRef=\"end\"/>"
                + "</process></definitions>";
        ProcessModel model = BpmnXmlParser.parse(xml);

        String out = BpmnXmlWriter.write(model, "p1", "示例");

        assertTrue(out.contains("<exclusiveGateway id=\"gw\" default=\"f3\"/>"));
    }

    @Test
    @DisplayName("write + parse：往返节点与流转一致")
    void shouldRoundTrip() {
        ProcessModel source = BpmnXmlParser.parse(BASIC);

        String xml = BpmnXmlWriter.write(source, "p1", "报销");
        ProcessModel reparsed = BpmnXmlParser.parse(xml);

        assertEquals(source.getNodes().size(), reparsed.getNodes().size());
        assertEquals(source.getFlows().size(), reparsed.getFlows().size());
        assertEquals("start", reparsed.getNodes().get(0).getKey());
        assertEquals("admin", reparsed.node("t1").getAssignee());
        assertEquals("start", reparsed.getFlows().get(0).getFrom());
    }

    @Test
    @DisplayName("write：流转含条件时写出 conditionExpression")
    void shouldWriteCondition() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"http://bw\">"
                + "<process id=\"p1\"><startEvent id=\"start\"/><userTask id=\"t1\" assignee=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"t1\">"
                + "<conditionExpression>amount&gt;100</conditionExpression></sequenceFlow>"
                + "</process></definitions>";
        ProcessModel model = BpmnXmlParser.parse(xml);

        String out = BpmnXmlWriter.write(model, "p1", "示例");

        assertTrue(out.contains("<conditionExpression"));
        assertTrue(out.contains("amount&gt;100"));
        assertEquals("amount>100", BpmnXmlParser.parse(out).getFlows().get(0).getCondition());
    }
}
