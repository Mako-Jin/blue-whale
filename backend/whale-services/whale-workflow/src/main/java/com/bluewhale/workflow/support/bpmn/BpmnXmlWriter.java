package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessNodeType;

/**
 * BPMN 2.0 XML 写出器：把规范化 {@link ProcessModel} 序列化为 BPMN XML。
 *
 * <p>与 {@link BpmnXmlParser} 对称：{@code assignee} 统一写 plain 属性；
 * 网关默认流转写 {@code default="flowId"}（偏差点见冻结契约 §3.4）。
 * BPMN-DI / {@code documentation} / {@code extensionElements} 不输出。</p>
 *
 * @author BlueWhale
 */
public final class BpmnXmlWriter {

    /** BPMN 2.0 模型命名空间 */
    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    /** targetNamespace（BlueWhale 私有） */
    private static final String TARGET_NAMESPACE = "http://bluewhale.io/workflow";

    /** 工具类不允许实例化 */
    private BpmnXmlWriter() {
    }

    /**
     * 序列化为 BPMN XML 文本。
     *
     * @param model 流程模型
     * @param processId process id（取流程编码）
     * @param processName process name（取流程名称）
     * @return BPMN XML 文本
     */
    public static String write(ProcessModel model, String processId, String processName) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"").append(BPMN_NAMESPACE)
                .append("\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" targetNamespace=\"").append(TARGET_NAMESPACE).append("\">\n");
        xml.append("  <process id=\"").append(escape(processId))
                .append("\" name=\"").append(escape(processName)).append("\" isExecutable=\"false\">\n");
        for (ProcessModelNode node : model.getNodes()) {
            appendNode(xml, node, defaultFlowId(model, node));
        }
        for (ProcessModelFlow flow : model.getFlows()) {
            appendFlow(xml, flow);
        }
        xml.append("  </process>\n");
        xml.append("</definitions>\n");
        return xml.toString();
    }

    /**
     * 写出单个节点元素（网关节点追加 {@code default="flowId"}）。
     *
     * @param xml 输出缓冲
     * @param node 流程模型节点
     * @param defaultFlowId 该节点的默认流转键，可为 {@code null}
     */
    private static void appendNode(StringBuilder xml, ProcessModelNode node, String defaultFlowId) {
        xml.append("    <").append(elementName(node.getType()))
                .append(" id=\"").append(escape(node.getKey())).append('"');
        if (node.getName() != null && !node.getName().isBlank()) {
            xml.append(" name=\"").append(escape(node.getName())).append('"');
        }
        if (node.isUserTask() && node.getAssignee() != null) {
            xml.append(" assignee=\"").append(escape(node.getAssignee())).append('"');
        }
        if (node.isUserTask() && node.getFormCode() != null && !node.getFormCode().isBlank()) {
            xml.append(" formKey=\"").append(escape(node.getFormCode())).append('"');
        }
        if (node.isUserTask() && node.getFormId() != null && !node.getFormId().isBlank()) {
            xml.append(" formId=\"").append(escape(node.getFormId())).append('"');
        }
        if (defaultFlowId != null) {
            xml.append(" default=\"").append(escape(defaultFlowId)).append('"');
        }
        xml.append("/>\n");
    }

    /**
     * 查找某节点的默认出流键。
     *
     * @param model 流程模型
     * @param node 节点
     * @return 默认出流键，无则 {@code null}
     */
    private static String defaultFlowId(ProcessModel model, ProcessModelNode node) {
        if (node.getType() != ProcessNodeType.EXCLUSIVE_GATEWAY) {
            return null;
        }
        for (ProcessModelFlow flow : model.outgoing(node.getKey())) {
            if (Boolean.TRUE.equals(flow.getIsDefault())) {
                return flow.getKey();
            }
        }
        return null;
    }

    /**
     * 写出单个流转元素（含条件表达式子元素）。
     *
     * @param xml 输出缓冲
     * @param flow 流程模型流转
     */
    private static void appendFlow(StringBuilder xml, ProcessModelFlow flow) {
        xml.append("    <sequenceFlow id=\"").append(escape(flow.getKey()))
                .append("\" sourceRef=\"").append(escape(flow.getFrom()))
                .append("\" targetRef=\"").append(escape(flow.getTo())).append('"');
        if (flow.getCondition() == null || flow.getCondition().isBlank()) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n      <conditionExpression xsi:type=\"tFormalExpression\">")
                .append(escape(flow.getCondition()))
                .append("</conditionExpression>\n    </sequenceFlow>\n");
    }

    /**
     * 节点类型 → BPMN 元素名。
     *
     * @param type 节点类型
     * @return BPMN 元素名
     */
    private static String elementName(ProcessNodeType type) {
        return switch (type) {
            case START_EVENT -> "startEvent";
            case END_EVENT -> "endEvent";
            case EXCLUSIVE_GATEWAY -> "exclusiveGateway";
            case PARALLEL_GATEWAY -> "parallelGateway";
            case USER_TASK, COUNTER_SIGN -> "userTask";
        };
    }

    /**
     * XML 转义属性值/文本（{@code & < > " '}）。
     *
     * @param value 原始文本，可为 {@code null}
     * @return 转义后文本（{@code null} 返回空串）
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
