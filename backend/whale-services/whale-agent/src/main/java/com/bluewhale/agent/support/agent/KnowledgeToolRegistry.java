package com.bluewhale.agent.support.agent;

import com.bluewhale.agent.support.llm.LlmToolSpec;
import com.bluewhale.agent.support.mcp.McpToolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自主编排工具注册表：把 MCP 只读工具适配为模型可调用的 function 定义。
 *
 * <p>工具输出会被压缩到有限长度再回填模型，避免一次 {@code list_services} 或
 * 大文件读取撑爆上下文；同时把有价值的结果转换为 {@link ToolEvidence}，
 * 供问答侧生成与既有证据面板一致的引用编号。</p>
 *
 * @author BlueWhale
 */
@Component
public class KnowledgeToolRegistry {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单条证据片段上限 */
    private static final int EVIDENCE_SNIPPET_LIMIT = 800;

    /** 回填模型的单次工具文本上限 */
    private static final int MODEL_TEXT_LIMIT = 6000;

    /** 文件读取回填上限 */
    private static final int READ_TEXT_LIMIT = 4000;

    private final McpToolService toolService;

    private final List<AgentTool> tools;

    /**
     * 构建工具集（检索 / 事实 / 读文件 / 服务清单）。
     *
     * @param toolService MCP 工具实现
     */
    public KnowledgeToolRegistry(McpToolService toolService) {
        this.toolService = toolService;
        this.tools = List.of(
                new AgentTool("search_knowledge",
                        "语义检索平台代码、文档、DDL 与配置，返回仓库相对路径、行号区间与片段。",
                        searchSchema(), this::search),
                new AgentTool("get_fact",
                        "按类型与键精确查询结构化事实（SERVICE/ROUTE/PORT/DEPENDENCY/TABLE/COLUMN/COMMIT/ENDPOINT）。",
                        factSchema(), this::getFact),
                new AgentTool("read_file",
                        "读取仓库内文本文件的指定行区间，用于查看检索命中的具体代码或配置；单次最多 400 行。",
                        readSchema(), this::readFile),
                new AgentTool("list_services",
                        "列出平台全部服务与其端口、职责，用于确认服务名与端口。",
                        emptySchema(), args -> listServices()));
    }

    /**
     * 模型侧工具定义。
     *
     * @return 工具定义列表
     */
    public List<LlmToolSpec> specs() {
        List<LlmToolSpec> specs = new ArrayList<>(tools.size());
        for (AgentTool tool : tools) {
            specs.add(new LlmToolSpec(tool.name(), tool.description(), tool.parameters()));
        }
        return specs;
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名
     * @return 工具；不存在返回 {@code null}
     */
    public AgentTool find(String name) {
        for (AgentTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 检索工具。
     *
     * @param arguments 参数
     * @return 执行结果
     */
    private ToolExecution search(JsonNode arguments) {
        Map<String, Object> result = toolService.searchKnowledge(toArgs(arguments));
        ObjectNode text = MAPPER.createObjectNode();
        text.put("query", String.valueOf(result.get("query")));
        Object items = result.get("items");
        List<ToolEvidence> evidence = new ArrayList<>();
        ArrayNode array = text.putArray("items");
        if (items instanceof List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> item)) {
                    continue;
                }
                String path = text(item.get("path"));
                String title = text(item.get("title"));
                Integer startLine = intValue(item.get("startLine"));
                Integer endLine = intValue(item.get("endLine"));
                String snippet = truncate(text(item.get("snippet")), EVIDENCE_SNIPPET_LIMIT);
                Double score = doubleValue(item.get("score"));
                ObjectNode node = array.addObject();
                node.put("path", path);
                node.put("startLine", startLine);
                node.put("endLine", endLine);
                node.put("title", title);
                node.put("snippet", snippet);
                evidence.add(new ToolEvidence("chunk", path, title, startLine, endLine, snippet,
                        null, null, score));
            }
        }
        text.put("count", evidence.size());
        return new ToolExecution(truncate(text.toString(), MODEL_TEXT_LIMIT), evidence);
    }

    /**
     * 事实查询工具。
     *
     * @param arguments 参数
     * @return 执行结果
     */
    private ToolExecution getFact(JsonNode arguments) {
        Map<String, Object> result = toolService.getFact(toArgs(arguments));
        Object items = result.get("items");
        ObjectNode text = MAPPER.createObjectNode();
        ArrayNode array = text.putArray("items");
        List<ToolEvidence> evidence = new ArrayList<>();
        if (items instanceof List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> item)) {
                    continue;
                }
                String type = text(item.get("type"));
                String key = text(item.get("key"));
                String sourcePath = text(item.get("sourcePath"));
                String value = truncate(toJson(item.get("value")), EVIDENCE_SNIPPET_LIMIT);
                ObjectNode node = array.addObject();
                node.put("type", type);
                node.put("key", key);
                node.put("value", value);
                node.put("sourcePath", sourcePath);
                evidence.add(new ToolEvidence("fact", sourcePath, key, null, null, value,
                        type, key, null));
            }
        }
        text.put("count", evidence.size());
        return new ToolExecution(truncate(text.toString(), MODEL_TEXT_LIMIT), evidence);
    }

    /**
     * 文件读取工具。
     *
     * @param arguments 参数
     * @return 执行结果
     */
    private ToolExecution readFile(JsonNode arguments) {
        Map<String, Object> result = toolService.readFile(toArgs(arguments));
        String path = text(result.get("path"));
        Integer startLine = intValue(result.get("startLine"));
        Integer endLine = intValue(result.get("endLine"));
        String content = text(result.get("content"));
        ObjectNode text = MAPPER.createObjectNode();
        text.put("path", path);
        text.put("startLine", startLine);
        text.put("endLine", endLine);
        text.put("totalLines", intValue(result.get("totalLines")));
        text.put("truncated", Boolean.TRUE.equals(result.get("truncated")));
        text.put("content", truncate(content, READ_TEXT_LIMIT));
        ToolEvidence evidence = new ToolEvidence("chunk", path, path, startLine, endLine,
                truncate(content, EVIDENCE_SNIPPET_LIMIT), null, null, null);
        return new ToolExecution(truncate(text.toString(), MODEL_TEXT_LIMIT), List.of(evidence));
    }

    /**
     * 服务清单工具。
     *
     * @return 执行结果
     */
    private ToolExecution listServices() {
        Map<String, Object> result = toolService.listServices();
        StringBuilder builder = new StringBuilder("服务清单（名称 / 端口 / 职责）：\n");
        Object services = result.get("services");
        if (services instanceof List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> item) || !(item.get("value") instanceof Map<?, ?> value)) {
                    continue;
                }
                builder.append("- ").append(text(item.get("key")))
                        .append(" / ").append(text(value.get("port")))
                        .append(" / ").append(text(value.get("responsibility")))
                        .append('\n');
            }
        }
        return ToolExecution.textOnly(truncate(builder.toString(), MODEL_TEXT_LIMIT));
    }

    /**
     * JSON 参数转 Map。
     *
     * @param arguments 参数节点
     * @return 参数 Map
     */
    private static Map<String, Object> toArgs(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            return Map.of();
        }
        return MAPPER.convertValue(arguments, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 对象转 JSON 字符串。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private static String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 安全转字符串。
     *
     * @param value 值
     * @return 字符串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 安全转整数。
     *
     * @param value 值
     * @return 整数；无法转换返回 {@code null}
     */
    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * 安全转浮点。
     *
     * @param value 值
     * @return 浮点数；无法转换返回 {@code null}
     */
    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    /**
     * 截断文本。
     *
     * @param value 文本
     * @param limit 上限
     * @return 截断结果
     */
    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    /**
     * 检索工具参数 Schema。
     *
     * @return Schema
     */
    private static ObjectNode searchSchema() {
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("query", property("string", "自然语言或关键词"));
        properties.set("topK", property("integer", "返回条数，默认 8，上限 20"));
        properties.set("kinds", property("string", "知识类型过滤，逗号分隔：code,doc,sql,config"));
        return schema(properties, "query");
    }

    /**
     * 事实查询参数 Schema。
     *
     * @return Schema
     */
    private static ObjectNode factSchema() {
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("type", property("string", "事实类型，如 PORT / TABLE / DEPENDENCY"));
        properties.set("key", property("string", "事实键，如服务名或表名"));
        properties.set("limit", property("integer", "返回条数，默认 50，上限 200"));
        return schema(properties);
    }

    /**
     * 文件读取参数 Schema。
     *
     * @return Schema
     */
    private static ObjectNode readSchema() {
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("path", property("string", "仓库相对路径"));
        properties.set("startLine", property("integer", "起始行，默认 1"));
        properties.set("endLine", property("integer", "结束行，默认到文件末尾"));
        return schema(properties, "path");
    }

    /**
     * 无参工具 Schema。
     *
     * @return Schema
     */
    private static ObjectNode emptySchema() {
        return schema(MAPPER.createObjectNode());
    }

    /**
     * 构造参数 Schema。
     *
     * @param properties 属性节点
     * @param required   必填项
     * @return Schema
     */
    private static ObjectNode schema(ObjectNode properties, String... required) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "object");
        node.set("properties", properties);
        ArrayNode requiredArray = node.putArray("required");
        for (String item : required) {
            requiredArray.add(item);
        }
        return node;
    }

    /**
     * 构造单个属性。
     *
     * @param type        类型
     * @param description 说明
     * @return 属性节点
     */
    private static ObjectNode property(String type, String description) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        node.put("description", description);
        return node;
    }
}
