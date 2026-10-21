package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ProcessModelGroup;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ValidationCode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BPMN 2.0 XML 解析器：把 BPMN XML 映射为规范化 {@link ProcessModel}。
 *
 * <p>映射见冻结契约 §3.4：{@code startEvent/endEvent/userTask/exclusiveGateway}
 * 四种节点 + {@code sequenceFlow}；{@code assignee} 读任意前缀的同名属性；
 * 网关 {@code default} 属性映射为流转 {@code isDefault}；BPMN-DI 的
 * {@code BPMNShape/bpmnElement} + {@code dc:Bounds} 回读为节点画布坐标 {@code x/y}；
 * {@code documentation}、{@code extensionElements} 忽略；不支持元素抛
 * {@code BPMN_UNSUPPORTED_ELEMENT} 并点名元素。</p>
 *
 * @author BlueWhale
 */
public final class BpmnXmlParser {

    /** 元素名 → 节点类型映射 */
    private static final Map<String, ProcessNodeType> NODE_TYPES = new LinkedHashMap<>();

    /** 分组键合法字符集（与规范化 JSON 的 key 规则一致） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 忽略（非错误）的元素名：其子树整体跳过 */
    private static final Set<String> IGNORED = Set.of(
            "BPMNDiagram", "BPMNPlane", "BPMNShape", "BPMNEdge", "documentation", "extensionElements",
            "flowNodeRef");

    /** 不支持的元素名：导入即报错并点名 */
    private static final Set<String> UNSUPPORTED = Set.of(
            "serviceTask", "scriptTask", "manualTask", "inclusiveGateway",
            "eventBasedGateway", "subProcess", "callActivity", "boundaryEvent",
            "intermediateCatchEvent", "intermediateThrowEvent", "timerEventDefinition");

    static {
        NODE_TYPES.put("startEvent", ProcessNodeType.START_EVENT);
        NODE_TYPES.put("endEvent", ProcessNodeType.END_EVENT);
        NODE_TYPES.put("userTask", ProcessNodeType.USER_TASK);
        NODE_TYPES.put("exclusiveGateway", ProcessNodeType.EXCLUSIVE_GATEWAY);
        NODE_TYPES.put("parallelGateway", ProcessNodeType.PARALLEL_GATEWAY);
    }

    /** 工具类不允许实例化 */
    private BpmnXmlParser() {
    }

    /**
     * 解析 BPMN XML 为流程模型。
     *
     * @param xml BPMN XML 文本
     * @return 流程模型（规范化，{@link ProcessModel#getDefinitionType()} = stateMachine）
     * @throws ProcessModelException XML 非法 / 根或 process 数量不对 / 含不支持元素时抛出
     */
    public static ProcessModel parse(String xml) {
        Document document = BpmnXmlSupport.parse(xml);
        Element process = findProcess(document);
        assertSupported(process);
        ProcessModel model = readProcess(process);
        applyGatewayDefaults(process, model);
        model.setDefinitionType(ProcessModelParser.TYPE_STATE_MACHINE);
        model.reindex();
        readLanes(process, model);
        applyDiagramCoordinates(document.getDocumentElement(), model);
        return model;
    }

    /**
     * 定位唯一的 {@code process} 元素。
     *
     * @param document DOM 文档
     * @return process 元素
     * @throws ProcessModelException 根非法或 process 数量不为 1 时抛出
     */
    private static Element findProcess(Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new ProcessModelException(ValidationCode.BPMN_ROOT_INVALID, "BPMN 根元素缺失");
        }
        String rootName = localName(root);
        if ("process".equals(rootName)) {
            return root;
        }
        if (!"definitions".equals(rootName)) {
            throw new ProcessModelException(ValidationCode.BPMN_ROOT_INVALID, "BPMN 根元素应为 definitions，实际为 " + rootName);
        }
        List<Element> processes = new ArrayList<>();
        for (Element child : children(root)) {
            if ("process".equals(localName(child))) {
                processes.add(child);
            }
        }
        if (processes.size() != 1) {
            throw new ProcessModelException(ValidationCode.BPMN_PROCESS_COUNT, "BPMN process 数量应为 1，实际为 " + processes.size());
        }
        return processes.get(0);
    }

    /**
     * 递归断言无「不支持元素」（忽略 subtree 内部不检查）。
     *
     * @param parent 父元素
     * @throws ProcessModelException 命中不支持元素时抛出
     */
    private static void assertSupported(Element parent) {
        for (Element child : children(parent)) {
            String name = localName(child);
            if (IGNORED.contains(name)) {
                continue;
            }
            if (UNSUPPORTED.contains(name)) {
                throw new ProcessModelException(ValidationCode.BPMN_UNSUPPORTED_ELEMENT,
                        "不支持的 BPMN 元素：" + name, name);
            }
            assertSupported(child);
        }
    }

    /**
     * 读取 process 的直属子元素，映射为节点与流转。
     *
     * @param process process 元素
     * @return 流程模型
     */
    private static ProcessModel readProcess(Element process) {
        ProcessModel model = new ProcessModel();
        for (Element child : children(process)) {
            String name = localName(child);
            if (IGNORED.contains(name)) {
                continue;
            }
            if ("sequenceFlow".equals(name)) {
                model.getFlows().add(readFlow(child));
            } else if (NODE_TYPES.containsKey(name)) {
                model.getNodes().add(readNode(child, NODE_TYPES.get(name)));
            }
        }
        return model;
    }

    /**
     * 读取节点元素。
     *
     * @param element 节点元素
     * @param type 节点类型
     * @return 流程模型节点
     * @throws ProcessModelException 缺少 id 时抛出
     */
    private static ProcessModelNode readNode(Element element, ProcessNodeType type) {
        String key = attribute(element, "id");
        if (key == null || key.isBlank()) {
            throw new ProcessModelException(ValidationCode.NODE_KEY_MISSING, "BPMN 节点缺少 id", localName(element));
        }
        ProcessModelNode node = new ProcessModelNode();
        node.setKey(key.trim());
        node.setType(type);
        node.setName(attribute(element, "name"));
        node.setAssignee(attribute(element, "assignee"));
        if (type == ProcessNodeType.USER_TASK) {
            node.setFormCode(attribute(element, "formKey"));
            node.setFormId(attribute(element, "formId"));
        }
        return node;
    }

    /**
     * 读取 sequenceFlow 元素。
     *
     * @param element sequenceFlow 元素
     * @return 流程模型流转
     */
    private static ProcessModelFlow readFlow(Element element) {
        ProcessModelFlow flow = new ProcessModelFlow();
        flow.setKey(attribute(element, "id"));
        flow.setFrom(attribute(element, "sourceRef"));
        flow.setTo(attribute(element, "targetRef"));
        flow.setCondition(conditionText(element));
        flow.setIsDefault(false);
        return flow;
    }

    /**
     * 提取 {@code conditionExpression} 子元素的文本内容。
     *
     * @param flowElement sequenceFlow 元素
     * @return 条件文本，无则 {@code null}
     */
    private static String conditionText(Element flowElement) {
        for (Element child : children(flowElement)) {
            if ("conditionExpression".equals(localName(child))) {
                String text = child.getTextContent();
                return text == null || text.isBlank() ? null : text.trim();
            }
        }
        return null;
    }

    /**
     * 回读泳道：{@code laneSet/lane} → 分组，{@code flowNodeRef} → 节点归属。
     *
     * <p>泳道为可选图形语义：lane 缺少合法 id（不满足分组键规则）或键重复时跳过；
     * 节点已归属其他泳道时保持首个归属（不覆盖）。</p>
     *
     * @param process process 元素
     * @param model 流程模型（节点索引已构建）
     */
    private static void readLanes(Element process, ProcessModel model) {
        for (Element laneSet : children(process)) {
            if (!"laneSet".equals(localName(laneSet))) {
                continue;
            }
            for (Element lane : children(laneSet)) {
                if ("lane".equals(localName(lane))) {
                    readLane(lane, model);
                }
            }
        }
    }

    /**
     * 读取单个 {@code lane} 元素并回填成员节点归属。
     *
     * @param lane lane 元素
     * @param model 流程模型
     */
    private static void readLane(Element lane, ProcessModel model) {
        String key = attribute(lane, "id");
        if (key == null || !KEY_PATTERN.matcher(key.trim()).matches() || model.group(key.trim()) != null) {
            return;
        }
        ProcessModelGroup group = new ProcessModelGroup();
        group.setKey(key.trim());
        group.setName(attribute(lane, "name"));
        model.getGroups().add(group);
        for (Element ref : children(lane)) {
            if (!"flowNodeRef".equals(localName(ref))) {
                continue;
            }
            String text = ref.getTextContent();
            ProcessModelNode node = model.node(text == null ? null : text.trim());
            if (node != null && (node.getGroupKey() == null || node.getGroupKey().isBlank())) {
                node.setGroupKey(group.getKey());
            }
        }
    }

    /**
     * 应用网关 {@code default="flowId"} 属性：把对应流转标记为默认。
     *
     * @param process process 元素
     * @param model 流程模型
     */
    private static void applyGatewayDefaults(Element process, ProcessModel model) {
        for (Element child : children(process)) {
            if (!"exclusiveGateway".equals(localName(child))) {
                continue;
            }
            String defaultFlow = attribute(child, "default");
            if (defaultFlow == null || defaultFlow.isBlank()) {
                continue;
            }
            for (ProcessModelFlow flow : model.getFlows()) {
                if (defaultFlow.trim().equals(flow.getKey())) {
                    flow.setIsDefault(true);
                }
            }
        }
    }

    /**
     * 回读 BPMN-DI 画布坐标：{@code BPMNShape.bpmnElement} 对应节点，读首个 {@code dc:Bounds} 的 x/y。
     *
     * <p>图形信息为可选元数据：形状缺失、引用未知节点、坐标非数字均静默跳过。</p>
     *
     * @param root 文档根元素，可为 {@code null}
     * @param model 流程模型
     */
    private static void applyDiagramCoordinates(Element root, ProcessModel model) {
        if (root == null) {
            return;
        }
        List<Element> shapes = new ArrayList<>();
        collect(root, "BPMNShape", shapes);
        for (Element shape : shapes) {
            String elementRef = attribute(shape, "bpmnElement");
            if (elementRef == null || elementRef.isBlank()) {
                continue;
            }
            ProcessModelNode node = model.node(elementRef.trim());
            Element bounds = firstChild(shape, "Bounds");
            if (node == null || bounds == null) {
                continue;
            }
            Integer x = coordinate(bounds, "x");
            Integer y = coordinate(bounds, "y");
            if (x != null) {
                node.setX(x);
            }
            if (y != null) {
                node.setY(y);
            }
        }
    }

    /**
     * 递归收集指定本地名的后代元素。
     *
     * @param parent 父元素
     * @param name 目标本地名
     * @param result 结果收集列表
     */
    private static void collect(Element parent, String name, List<Element> result) {
        for (Element child : children(parent)) {
            if (name.equals(localName(child))) {
                result.add(child);
            }
            collect(child, name, result);
        }
    }

    /**
     * 取首个指定本地名的直属子元素。
     *
     * @param parent 父元素
     * @param name 目标本地名
     * @return 子元素，缺失返回 {@code null}
     */
    private static Element firstChild(Element parent, String name) {
        for (Element child : children(parent)) {
            if (name.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    /**
     * 读取坐标属性并四舍五入为整数。
     *
     * @param element 元素
     * @param name 属性名
     * @return 坐标值，缺失或非数字返回 {@code null}
     */
    private static Integer coordinate(Element element, String name) {
        String raw = attribute(element, name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取元素的直属子元素列表。
     *
     * @param parent 父元素
     * @return 子元素列表
     */
    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * 取元素本地名（忽略命名空间前缀）。
     *
     * @param element 元素
     * @return 本地名
     */
    private static String localName(Element element) {
        String name = element.getLocalName();
        if (name != null) {
            return name;
        }
        String nodeName = element.getNodeName();
        int index = nodeName.indexOf(':');
        return index < 0 ? nodeName : nodeName.substring(index + 1);
    }

    /**
     * 按本地名读取属性（兼容 plain / flowable: / camunda: 前缀）。
     *
     * @param element 元素
     * @param name 属性本地名
     * @return 属性值，缺失返回 {@code null}
     */
    private static String attribute(Element element, String name) {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrLocal = attr.getLocalName();
            if (attrLocal != null && name.equals(attrLocal)) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
}
