"""只读工具客户端与注册表单测。"""

import json

import httpx
import pytest
from fastapi.testclient import TestClient

from whale_ai.api import routes
from whale_ai.main import app
from whale_ai.tools import AgentToolClient, ToolContext, ToolError, execute_tool, list_tools

client = TestClient(app)


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


def test_platform_op_maps_operation_and_params() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = request.headers
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"code": 0, "data": {"confirmationId": "c-1", "status": "PENDING"}})

    data = execute_tool(
        "platform_op",
        {"operation": "form.definition.create", "params": {"code": "demo", "name": "示例表单"}},
        ToolContext(user_id="7", permission_codes="sys:agent:chat,sys:ai:ops:form"),
        client=_client(handler),
    )

    assert data == {"confirmationId": "c-1", "status": "PENDING"}
    body = captured["body"]
    assert isinstance(body, dict)
    assert body == {
        "operation": "form.definition.create",
        "params": {"code": "demo", "name": "示例表单"},
    }
    headers = captured["headers"]
    assert isinstance(headers, httpx.Headers)
    assert headers["X-Permission-Codes"] == "sys:agent:chat,sys:ai:ops:form"


def test_platform_op_rejects_missing_operation_or_invalid_params() -> None:
    with pytest.raises(ToolError, match="缺少必填参数"):
        execute_tool("platform_op", {}, ToolContext())

    with pytest.raises(ToolError, match="必须是对象"):
        execute_tool("platform_op", {"operation": "form.definition.create", "params": "x"}, ToolContext())


def test_confirm_proxies_to_agent_with_identity(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    class StubClient:
        def confirm(self, confirmation_id, approved, user_id, permission_codes):
            captured.update(
                confirmation_id=confirmation_id,
                approved=approved,
                user_id=user_id,
                permission_codes=permission_codes,
            )
            return {"confirmationId": confirmation_id, "status": "SUCCESS"}

    monkeypatch.setattr(routes, "get_agent_tool_client", lambda: StubClient())

    response = client.post(
        "/v1/agent/confirm",
        json={"confirmationId": "c-1", "approved": True},
        headers={"X-User-Id": "7", "X-Permission-Codes": "sys:ai:ops:form"},
    )

    assert response.status_code == 200
    assert response.json() == {"confirmationId": "c-1", "status": "SUCCESS"}
    assert captured == {
        "confirmation_id": "c-1",
        "approved": True,
        "user_id": "7",
        "permission_codes": "sys:ai:ops:form",
    }


def test_confirm_returns_502_when_agent_rejects(monkeypatch: pytest.MonkeyPatch) -> None:
    class FailingClient:
        def confirm(self, confirmation_id, approved, user_id, permission_codes):
            del confirmation_id, approved, user_id, permission_codes
            raise ToolError("确认令牌已使用或已失效")

    monkeypatch.setattr(routes, "get_agent_tool_client", lambda: FailingClient())

    response = client.post("/v1/agent/confirm", json={"confirmationId": "c-1", "approved": True})

    assert response.status_code == 502
    assert "已使用" in response.json()["detail"]


def test_list_tools_exposes_l0_and_l1_catalog() -> None:
    tools = list_tools()

    assert {tool["name"] for tool in tools} == {
        "knowledge_search",
        "fact_get",
        "doc_read",
        "data_query",
        "platform_op",
        "iot_device_state",
        "iot_device_history",
        "twin_scene_overview",
    }
    risk_by_name = {tool["name"]: tool["riskLevel"] for tool in tools}
    assert risk_by_name["data_query"] == "L1"
    assert risk_by_name["platform_op"] == "L2"
    assert all(
        level == "L0"
        for name, level in risk_by_name.items()
        if name not in ("data_query", "platform_op", "iot_device_state",
                        "iot_device_history", "twin_scene_overview")
    )
    permission_by_name = {tool["name"]: tool["permission"] for tool in tools}
    assert permission_by_name["data_query"] == "sys:ai:data:read"
    assert permission_by_name["platform_op"] == "sys:ai:ops:*"
    assert all(
        code == "sys:agent:chat"
        for name, code in permission_by_name.items()
        if name not in ("data_query", "platform_op", "iot_device_state",
                        "iot_device_history", "twin_scene_overview")
    )



def test_iot_device_state_maps_device_fields() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"code": 0, "data": {"online": 1, "reported": {"temperature": 42}}})

    data = execute_tool(
        "iot_device_state",
        {"product_code": "plant-demo", "device_name": "boiler-01"},
        ToolContext(user_id="7", permission_codes="sys:ai:data:read"),
        client=_client(handler),
    )

    assert data == {"online": 1, "reported": {"temperature": 42}}
    assert captured["url"].endswith("/agent/tools/iot-device-state")
    body = captured["body"]
    assert isinstance(body, dict)
    assert body["productCode"] == "plant-demo"
    assert body["deviceName"] == "boiler-01"


def test_iot_device_history_maps_filters() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"code": 0, "data": []})

    execute_tool(
        "iot_device_history",
        {"device_id": 9001, "identifier": "temperature", "from": "2026-11-10T00:00:00", "limit": 50},
        ToolContext(),
        client=_client(handler),
    )

    body = captured["body"]
    assert isinstance(body, dict)
    assert body["deviceId"] == 9001
    assert body["identifier"] == "temperature"
    assert body["from"] == "2026-11-10T00:00:00"
    assert body["limit"] == 50


def test_iot_device_requires_locator() -> None:
    with pytest.raises(ToolError, match="device_id 或 product_code"):
        execute_tool("iot_device_state", {}, ToolContext())


def test_twin_scene_overview_maps_scene_code() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = json.loads(request.content) if request.content else {}
        return httpx.Response(200, json={"code": 0, "data": {"scene": {}, "entities": []}})

    data = execute_tool(
        "twin_scene_overview",
        {"scene_code": "plant-demo"},
        ToolContext(),
        client=_client(handler),
    )

    assert data == {"scene": {}, "entities": []}
    assert captured["url"].endswith("/agent/tools/twin-scene-overview")
    body = captured["body"]
    assert isinstance(body, dict)
    assert body["sceneCode"] == "plant-demo"


def test_twin_scene_requires_locator() -> None:
    with pytest.raises(ToolError, match="scene_id 或 scene_code"):
        execute_tool("twin_scene_overview", {}, ToolContext())


def test_platform_tools_registered_as_l1() -> None:
    descriptions = {item["name"]: item for item in list_tools()}
    for name in ("iot_device_state", "iot_device_history", "twin_scene_overview"):
        item = descriptions[name]
        assert item["riskLevel"] == "L1"
        assert item["permission"] == "sys:ai:data:read"
