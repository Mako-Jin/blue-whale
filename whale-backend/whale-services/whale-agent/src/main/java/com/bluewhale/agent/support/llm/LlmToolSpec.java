package com.bluewhale.agent.support.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 可提供给模型的工具定义（OpenAI {@code tools[].function}）。
 *
 * @param name        工具名
 * @param description 说明
 * @param parameters  参数 JSON Schema
 * @author BlueWhale
 */
public record LlmToolSpec(String name, String description, JsonNode parameters) {
}
