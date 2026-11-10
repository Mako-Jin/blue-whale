package com.bluewhale.agent.support.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * 自主编排可调用的只读工具（定义 + 执行器）。
 *
 * @param name        工具名
 * @param description 说明
 * @param parameters  参数 JSON Schema
 * @param handler     执行器（入参为工具参数 JSON）
 * @author BlueWhale
 */
public record AgentTool(String name, String description, ObjectNode parameters,
                        Function<JsonNode, ToolExecution> handler) {
}
