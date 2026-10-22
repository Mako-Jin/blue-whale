package com.bluewhale.agent.support.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 请求分发（轨 F 试点）。
 *
 * <p>实现核心方法：{@code initialize}、{@code notifications/initialized}、{@code ping}、
 * {@code tools/list}、{@code tools/call}。协议版本固定 {@code 2024-11-05}，
 * 传输层为单次 HTTP JSON 请求/响应，不启用 SSE 流式。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class McpRequestHandler {

    /** MCP 协议版本 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    /** 服务名 */
    private static final String SERVER_NAME = "bluewhale-agent-mcp";

    /** 服务版本 */
    private static final String SERVER_VERSION = "1.0.0";

    /** JSON-RPC 解析错误 */
    private static final int PARSE_ERROR = -32700;

    /** JSON-RPC 方法不存在 */
    private static final int METHOD_NOT_FOUND = -32601;

    /** JSON-RPC 参数错误 */
    private static final int INVALID_PARAMS = -32602;

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpToolService toolService;

    /**
     * 处理一条 JSON-RPC 消息。
     *
     * @param body 请求体
     * @return 响应 JSON；通知类消息返回 {@code null}
     */
    public String handle(String body) {
        JsonNode request;
        try {
            request = MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return error(null, PARSE_ERROR, "无法解析 JSON-RPC 请求");
        }
        if (request == null || !request.isObject()) {
            return error(null, PARSE_ERROR, "请求必须是 JSON 对象");
        }
        JsonNode id = request.get("id");
        String method = request.path("method").asText("");
        JsonNode params = request.get("params");
        try {
            switch (method) {
                case "initialize":
                    return result(id, initializeResult());
                case "notifications/initialized":
                case "notifications/cancelled":
                    return null;
                case "ping":
                    return result(id, Map.of());
                case "tools/list":
                    return result(id, Map.of("tools", tools()));
                case "tools/call":
                    return result(id, callTool(params));
                default:
                    return error(id, METHOD_NOT_FOUND, "未知方法: " + method);
            }
        } catch (IllegalArgumentException e) {
            return error(id, INVALID_PARAMS, e.getMessage());
        } catch (RuntimeException e) {
            return error(id, INVALID_PARAMS, "工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 构造 initialize 结果。
     *
     * @return 初始化结果
     */
    private Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);
        result.put("instructions", "BlueWhale 平台只读知识工具：先 search_knowledge 定位证据，"
                + "再用 read_file 查看具体行；结构化事实用 get_fact / list_services。禁止用本工具执行写操作。");
        return result;
    }

    /**
     * 工具清单。
     *
     * @return MCP 工具定义
     */
    private List<Map<String, Object>> tools() {
        return List.of(
                tool("search_knowledge", "语义检索平台代码与文档，返回路径、行号与片段",
                        Map.of("query", stringSchema("自然语言或关键词"),
                                "topK", intSchema("返回条数，默认 8，上限 20"),
                                "kinds", stringSchema("知识类型过滤，逗号分隔：code,doc,sql,config")),
                        List.of("query")),
                tool("get_fact", "按类型/键精确查询结构化事实（服务、路由、端口、依赖、表字段、提交、端点）",
                        Map.of("type", stringSchema("事实类型，如 PORT / TABLE / DEPENDENCY"),
                                "key", stringSchema("事实键，如服务名或表名"),
                                "limit", intSchema("返回条数，默认 50，上限 200")),
                        List.of()),
                tool("read_file", "读取仓库内文本文件片段（最多 400 行 / 2 万字符）",
                        Map.of("path", stringSchema("仓库相对路径"),
                                "startLine", intSchema("起始行，默认 1"),
                                "endLine", intSchema("结束行，默认到文件末尾")),
                        List.of("path")),
                tool("list_services", "列出平台服务与端口事实",
                        Map.of(), List.of()));
    }

    /**
     * 构造单个工具定义。
     *
     * @param name        工具名
     * @param description 说明
     * @param properties  参数 schema
     * @param required    必填参数
     * @return 工具定义
     */
    private Map<String, Object> tool(String name, String description,
                                     Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", schema);
        return tool;
    }

    /**
     * 字符串参数 schema。
     *
     * @param description 说明
     * @return schema
     */
    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    /**
     * 整数参数 schema。
     *
     * @param description 说明
     * @return schema
     */
    private Map<String, Object> intSchema(String description) {
        return Map.of("type", "integer", "description", description);
    }

    /**
     * 执行 tools/call。
     *
     * @param params JSON-RPC params
     * @return MCP 工具结果
     */
    private Map<String, Object> callTool(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw new IllegalArgumentException("tools/call 缺少 params");
        }
        String name = params.path("name").asText("");
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("tools/call 缺少工具名");
        }
        Map<String, Object> arguments = toMap(params.get("arguments"));
        try {
            Object payload = switch (name) {
                case "search_knowledge" -> toolService.searchKnowledge(arguments);
                case "get_fact" -> toolService.getFact(arguments);
                case "read_file" -> toolService.readFile(arguments);
                case "list_services" -> toolService.listServices();
                default -> throw new IllegalArgumentException("未知工具: " + name);
            };
            return textResult(payload, false);
        } catch (IllegalArgumentException e) {
            return textResult(Map.of("error", e.getMessage()), true);
        }
    }

    /**
     * 转为 Map 入参。
     *
     * @param node arguments 节点
     * @return 入参 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("arguments 必须是 JSON 对象");
        }
        return MAPPER.convertValue(node, Map.class);
    }

    /**
     * 构造文本内容结果。
     *
     * @param payload 业务结果
     * @param isError 是否错误
     * @return MCP 工具结果
     */
    private Map<String, Object> textResult(Object payload, boolean isError) {
        String text;
        try {
            text = MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            text = String.valueOf(payload);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("isError", isError);
        return result;
    }

    /**
     * 成功响应。
     *
     * @param id    请求 ID
     * @param value 结果
     * @return JSON
     */
    private String result(JsonNode id, Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", value);
        return write(response);
    }

    /**
     * 错误响应。
     *
     * @param id      请求 ID
     * @param code    错误码
     * @param message 错误说明
     * @return JSON
     */
    private String error(JsonNode id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return write(response);
    }

    /**
     * 序列化响应。
     *
     * @param response 响应对象
     * @return JSON
     */
    private String write(Map<String, Object> response) {
        ObjectNode node = MAPPER.valueToTree(response);
        return node.toString();
    }
}
