"""whale-agent 只读工具 HTTP 客户端。

工具由 Java 侧执行（规划 §6.3），Python 只负责透传用户身份与权限码，
并从统一响应体 ``Result`` 中解包 ``data``。
"""

from __future__ import annotations

from typing import Any

import httpx

from whale_ai.config import get_settings

TOOL_PATH_PREFIX = "/agent/tools/"


class ToolError(RuntimeError):
    """工具调用失败（网络、鉴权或业务错误）。"""


class AgentToolClient:
    """调用 whale-agent 工具接口的轻量客户端。"""

    def __init__(
        self,
        base_url: str,
        timeout_seconds: float = 20.0,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds
        self._transport = transport

    def invoke(
        self,
        endpoint: str,
        payload: dict[str, Any],
        user_id: str | None,
        permission_codes: str | None,
    ) -> Any:
        """调用工具接口并解包统一响应。

        Args:
            endpoint: 工具接口路径（不含前缀），如 ``knowledge-search``。
            payload: 工具入参。
            user_id: 调用用户ID（网关注入口径）。
            permission_codes: 逗号分隔的权限码。

        Returns:
            ``Result.data`` 内容。

        Raises:
            ToolError: 网络失败、非 200 或业务码非 0。
        """
        headers = {"Content-Type": "application/json"}
        if user_id:
            headers["X-User-Id"] = str(user_id)
        if permission_codes:
            headers["X-Permission-Codes"] = permission_codes
        url = f"{self._base_url}{TOOL_PATH_PREFIX}{endpoint}"
        try:
            with httpx.Client(timeout=self._timeout_seconds, transport=self._transport) as client:
                response = client.post(url, json=payload, headers=headers)
        except httpx.HTTPError as exc:
            raise ToolError(f"工具服务不可达：{exc}") from exc
        if response.status_code != httpx.codes.OK:
            raise ToolError(f"工具调用失败（HTTP {response.status_code}）")
        try:
            body = response.json()
        except ValueError as exc:
            raise ToolError("工具服务返回非 JSON 响应") from exc
        if body.get("code") != 0:
            raise ToolError(str(body.get("message") or "工具调用失败"))
        return body.get("data")


def get_agent_tool_client() -> AgentToolClient:
    """按配置构造工具客户端（进程内复用）。"""
    settings = get_settings()
    return AgentToolClient(settings.agent_base_url, settings.tool_timeout_seconds)
