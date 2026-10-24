"""只读工具注册表：名称、风险级、权限码、JSON Schema 与参数映射。

规划 §6.3 规定三个 L0 只读工具全部由 Java 侧执行；本模块只维护契约，
执行统一走 :class:`~whale_ai.tools.client.AgentToolClient`。
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from whale_ai.tools.client import AgentToolClient, ToolError, get_agent_tool_client

TOOL_PERMISSION = "sys:agent:chat"
TOOL_RISK_LEVEL = "L0"


@dataclass(frozen=True)
class ToolContext:
    """一次工具调用的调用方身份。"""

    user_id: str | None = None
    permission_codes: str | None = None


@dataclass(frozen=True)
class ToolDefinition:
    """工具契约。"""

    name: str
    description: str
    endpoint: str
    input_schema: dict[str, Any]
    build_payload: Callable[[dict[str, Any]], dict[str, Any]]

    def describe(self) -> dict[str, Any]:
        """转为 ``GET /v1/tools`` 的公开描述（不含端点与实现细节）。"""
        return {
            "name": self.name,
            "description": self.description,
            "riskLevel": TOOL_RISK_LEVEL,
            "permission": TOOL_PERMISSION,
            "inputSchema": self.input_schema,
        }


def _require_text(arguments: dict[str, Any], key: str) -> str:
    value = arguments.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ToolError(f"缺少必填参数: {key}")
    return value.strip()


def _optional_int(arguments: dict[str, Any], key: str) -> int | None:
    value = arguments.get(key)
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise ToolError(f"参数不是整数: {key}") from exc


def _knowledge_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    payload: dict[str, Any] = {"query": _require_text(arguments, "query")}
    top_k = _optional_int(arguments, "top_k")
    if top_k is not None:
        payload["topK"] = top_k
    kinds = arguments.get("kinds")
    if kinds:
        payload["kinds"] = [str(item) for item in kinds] if isinstance(kinds, list) else [str(kinds)]
    return payload


def _fact_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for source, target in (("type", "type"), ("key", "key")):
        value = arguments.get(source)
        if value is not None and str(value).strip():
            payload[target] = str(value).strip()
    limit = _optional_int(arguments, "limit")
    if limit is not None:
        payload["limit"] = limit
    return payload


def _doc_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    payload: dict[str, Any] = {"path": _require_text(arguments, "path")}
    start_line = _optional_int(arguments, "start_line")
    end_line = _optional_int(arguments, "end_line")
    if start_line is not None:
        payload["startLine"] = start_line
    if end_line is not None:
        payload["endLine"] = end_line
    return payload


TOOLS: dict[str, ToolDefinition] = {
    "knowledge_search": ToolDefinition(
        name="knowledge_search",
        description="语义检索平台代码与文档，返回路径、行号与片段。",
        endpoint="knowledge-search",
        input_schema={
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "检索文本"},
                "top_k": {"type": "integer", "minimum": 1, "maximum": 50},
                "kinds": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "知识类型：code/doc/sql/config",
                },
            },
            "required": ["query"],
        },
        build_payload=_knowledge_payload,
    ),
    "fact_get": ToolDefinition(
        name="fact_get",
        description="按类型/键精确查询结构化事实（服务、路由、端口、依赖、表字段、提交、端点）。",
        endpoint="fact-get",
        input_schema={
            "type": "object",
            "properties": {
                "type": {"type": "string"},
                "key": {"type": "string"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 200},
            },
        },
        build_payload=_fact_payload,
    ),
    "doc_read": ToolDefinition(
        name="doc_read",
        description="读取仓库内文本文件片段（禁止构建产物、依赖目录与密钥文件）。",
        endpoint="doc-read",
        input_schema={
            "type": "object",
            "properties": {
                "path": {"type": "string", "description": "仓库相对路径"},
                "start_line": {"type": "integer", "minimum": 1},
                "end_line": {"type": "integer", "minimum": 1},
            },
            "required": ["path"],
        },
        build_payload=_doc_payload,
    ),
}


def list_tools() -> list[dict[str, Any]]:
    """返回全部工具描述。"""
    return [definition.describe() for definition in TOOLS.values()]


def execute_tool(
    name: str,
    arguments: dict[str, Any] | None,
    context: ToolContext,
    client: AgentToolClient | None = None,
) -> Any:
    """执行指定工具。

    Args:
        name: 工具名（见 :data:`TOOLS`）。
        arguments: 工具入参（snake_case）。
        context: 调用方身份与权限码。
        client: 可注入的客户端（测试用），默认按配置创建。

    Returns:
        工具返回的 ``data``。

    Raises:
        ToolError: 工具不存在、参数非法或调用失败。
    """
    definition = TOOLS.get(name)
    if definition is None:
        raise ToolError(f"未知工具: {name}")
    payload = definition.build_payload(arguments or {})
    tool_client = client or get_agent_tool_client()
    return tool_client.invoke(definition.endpoint, payload, context.user_id, context.permission_codes)
