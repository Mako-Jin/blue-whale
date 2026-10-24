"""只读工具客户端与注册表单测。"""

import json

import httpx
import pytest

from whale_ai.tools import AgentToolClient, ToolContext, ToolError, execute_tool, list_tools


def _client(handler) -> AgentToolClient:
    return AgentToolClient("http://agent:8090", timeout_seconds=5, transport=httpx.MockTransport(handler))


def test_knowledge_search_unpacks_result_and_forwards_identity() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = request.headers
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"code": 0, "message": "成功", "data": {"count": 1, "items": []}})

    data = execute_tool(
        "knowledge_search",
        {"query": "端口分配", "top_k": 5},
        ToolContext(user_id="7", permission_codes="sys:agent:chat"),
        client=_client(handler),
    )

    assert data == {"count": 1, "items": []}
    body = captured["body"]
    assert isinstance(body, dict)
    assert body["query"] == "端口分配"
    assert body["topK"] == 5
    headers = captured["headers"]
    assert isinstance(headers, httpx.Headers)
    assert headers["X-User-Id"] == "7"
    assert headers["X-Permission-Codes"] == "sys:agent:chat"


def test_doc_read_maps_line_range() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"code": 0, "data": {"content": "x"}})

    execute_tool(
        "doc_read",
        {"path": "docs/service-catalog.md", "start_line": 3, "end_line": 9},
        ToolContext(),
        client=_client(handler),
    )

    body = captured["body"]
    assert isinstance(body, dict)
    assert body["path"] == "docs/service-catalog.md"
    assert body["startLine"] == 3
    assert body["endLine"] == 9


def test_business_error_raises_tool_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        del request
        return httpx.Response(200, json={"code": 403, "message": "无权限"})

    with pytest.raises(ToolError, match="无权限"):
        execute_tool("fact_get", {"type": "PORT"}, ToolContext(), client=_client(handler))


def test_unknown_tool_raises_tool_error() -> None:
    with pytest.raises(ToolError, match="未知工具"):
        execute_tool("no_such_tool", {}, ToolContext())


def test_data_query_maps_sql_and_forwards_data_permission() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = request.headers
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"code": 0, "data": {"count": 1, "rows": []}})

    execute_tool(
        "data_query",
        {"sql": "select service_name from v_ai_agent_fact_summary"},
        ToolContext(user_id="7", permission_codes="sys:ai:data:read"),
        client=_client(handler),
    )

    body = captured["body"]
    assert isinstance(body, dict)
    assert body == {"sql": "select service_name from v_ai_agent_fact_summary"}
    headers = captured["headers"]
    assert isinstance(headers, httpx.Headers)
    assert headers["X-Permission-Codes"] == "sys:ai:data:read"


def test_data_query_rejects_missing_or_oversized_sql() -> None:
    with pytest.raises(ToolError, match="缺少必填参数"):
        execute_tool("data_query", {"sql": "   "}, ToolContext())

    with pytest.raises(ToolError, match="SQL 过长"):
        execute_tool("data_query", {"sql": "select " + "x" * 4000}, ToolContext())


def test_list_tools_exposes_l0_and_l1_catalog() -> None:
    tools = list_tools()

    assert {tool["name"] for tool in tools} == {
        "knowledge_search",
        "fact_get",
        "doc_read",
        "data_query",
    }
    risk_by_name = {tool["name"]: tool["riskLevel"] for tool in tools}
    assert risk_by_name["data_query"] == "L1"
    assert all(level == "L0" for name, level in risk_by_name.items() if name != "data_query")
    permission_by_name = {tool["name"]: tool["permission"] for tool in tools}
    assert permission_by_name["data_query"] == "sys:ai:data:read"
    assert all(
        code == "sys:agent:chat" for name, code in permission_by_name.items() if name != "data_query"
    )
