"""只读工具注册表：名称、风险级、权限码、JSON Schema 与参数映射。

规划 §6.3 规定工具全部由 Java 侧执行；本模块只维护契约，
执行统一走 :class:`~whale_ai.tools.client.AgentToolClient`。
L0 工具（检索/事实/文档）走 ``sys:agent:chat``，L1 数据查询单独走 ``sys:ai:data:read``。
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from whale_ai.tools.client import AgentToolClient, ToolError, get_agent_tool_client

TOOL_PERMISSION = "sys:agent:chat"
TOOL_RISK_LEVEL = "L0"
MAX_SQL_CHARS = 4000


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
    risk_level: str = field(default=TOOL_RISK_LEVEL)
    permission: str = field(default=TOOL_PERMISSION)

    def describe(self) -> dict[str, Any]:
        """转为 ``GET /v1/tools`` 的公开描述（不含端点与实现细节）。"""
        return {
            "name": self.name,
            "description": self.description,
            "riskLevel": self.risk_level,
            "permission": self.permission,
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


def _data_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    sql = _require_text(arguments, "sql")
    if len(sql) > MAX_SQL_CHARS:
        raise ToolError(f"SQL 过长（上限 {MAX_SQL_CHARS} 字符）")
    return {"sql": sql}


def _platform_op_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    operation = _require_text(arguments, "operation")
    params = arguments.get("params")
    if params is None:
        params = {}
    if not isinstance(params, dict):
        raise ToolError("参数 params 必须是对象")
    return {"operation": operation, "params": params}


def _iot_device_payload(arguments: dict[str, Any], *, history: bool = False) -> dict[str, Any]:
    """IoT 设备工具入参（deviceId 或 productCode+deviceName 二选一）。"""
    payload: dict[str, Any] = {}
    device_id = _optional_int(arguments, "device_id")
    if device_id is not None:
        payload["deviceId"] = device_id
    for source, target in (("product_code", "productCode"), ("device_name", "deviceName")):
        value = arguments.get(source)
        if value is not None and str(value).strip():
            payload[target] = str(value).strip()
    if not payload:
        raise ToolError("需提供 device_id 或 product_code+device_name")
    if history:
        identifier = arguments.get("identifier")
        if identifier is not None and str(identifier).strip():
            payload["identifier"] = str(identifier).strip()
        for source, target in (("from", "from"), ("to", "to")):
            value = arguments.get(source)
            if value is not None and str(value).strip():
                payload[target] = str(value).strip()
        limit = _optional_int(arguments, "limit")
        if limit is not None:
            payload["limit"] = limit
    return payload


def _twin_scene_payload(arguments: dict[str, Any]) -> dict[str, Any]:
    """孪生场景概要入参（scene_id 或 scene_code 二选一）。"""
    payload: dict[str, Any] = {}
    scene_id = _optional_int(arguments, "scene_id")
    if scene_id is not None:
        payload["sceneId"] = scene_id
    scene_code = arguments.get("scene_code")
    if scene_code is not None and str(scene_code).strip():
        payload["sceneCode"] = str(scene_code).strip()
    if not payload:
        raise ToolError("需提供 scene_id 或 scene_code")
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
    "data_query": ToolDefinition(
        name="data_query",
        description="在只读白名单视图上执行受限 SELECT（自动 LIMIT、脱敏、5s 超时）。",
        endpoint="data-query",
        input_schema={
            "type": "object",
            "properties": {"sql": {"type": "string", "description": "SELECT 语句"}},
            "required": ["sql"],
        },
        build_payload=_data_payload,
        risk_level="L1",
        permission="sys:ai:data:read",
    ),
    "platform_op": ToolDefinition(
        name="platform_op",
        description="发起平台写操作（建表单等）：仅生成一次性确认卡片，用户确认后由 Java 侧执行。",
        endpoint="platform-op",
        input_schema={
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "description": "操作名，当前仅 form.definition.create",
                },
                "params": {
                    "type": "object",
                    "description": "操作参数，如表单 code/name/remark",
                },
            },
            "required": ["operation"],
        },
        build_payload=_platform_op_payload,
        risk_level="L2",
        permission="sys:ai:ops:*",
    ),
    "iot_device_state": ToolDefinition(
        name="iot_device_state",
        description="查询 IoT 设备实时态（在线状态 + 影子最新上报值）。设备用 deviceId 或 productCode+deviceName 定位。",
        endpoint="iot-device-state",
        input_schema={
            "type": "object",
            "properties": {
                "device_id": {"type": "integer", "description": "设备ID（与三元组二选一）"},
                "product_code": {"type": "string", "description": "产品编码"},
                "device_name": {"type": "string", "description": "设备名"},
            },
        },
        build_payload=_iot_device_payload,
        risk_level="L1",
        permission="sys:ai:data:read",
    ),
    "iot_device_history": ToolDefinition(
        name="iot_device_history",
        description="查询 IoT 设备遥测历史（时间倒序），可按属性标识符与时间段过滤。",
        endpoint="iot-device-history",
        input_schema={
            "type": "object",
            "properties": {
                "device_id": {"type": "integer", "description": "设备ID（与三元组二选一）"},
                "product_code": {"type": "string", "description": "产品编码"},
                "device_name": {"type": "string", "description": "设备名"},
                "identifier": {"type": "string", "description": "属性标识符过滤（如 temperature）"},
                "from": {"type": "string", "description": "起始时间 ISO-8601"},
                "to": {"type": "string", "description": "结束时间 ISO-8601"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 500},
            },
        },
        build_payload=lambda arguments: _iot_device_payload(arguments, history=True),
        risk_level="L1",
        permission="sys:ai:data:read",
    ),
    "twin_scene_overview": ToolDefinition(
        name="twin_scene_overview",
        description="查询数字孪生场景概要：场景元数据、实体树、数据绑定与标注热点。",
        endpoint="twin-scene-overview",
        input_schema={
            "type": "object",
            "properties": {
                "scene_id": {"type": "integer", "description": "场景ID（与 scene_code 二选一）"},
                "scene_code": {"type": "string", "description": "场景编码（如 plant-demo）"},
            },
        },
        build_payload=_twin_scene_payload,
        risk_level="L1",
        permission="sys:ai:data:read",
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
