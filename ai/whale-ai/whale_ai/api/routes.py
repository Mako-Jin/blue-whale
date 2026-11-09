"""HTTP 路由：健康检查、工具清单与智能体流式问答。"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from whale_ai import __version__
from whale_ai.api.schemas import ChatRequest, ConfirmRequest, HealthResponse
from whale_ai.config import get_llm_settings, get_settings
from whale_ai.graph import run_agent
from whale_ai.tools import ToolError, get_agent_tool_client, list_tools

logger = logging.getLogger(__name__)

router = APIRouter()

SSE_HEADERS = {
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
}

Event = tuple[str, dict[str, Any]]


def _sse(event: str, payload: dict[str, Any]) -> str:
    """编码一条 SSE 事件帧。"""
    data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\ndata: {data}\n\n"


@router.get("/health", response_model=HealthResponse, summary="健康检查")
async def health() -> HealthResponse:
    """返回服务存活、图状态与模型配置情况（不回显密钥）。"""
    return HealthResponse(
        status="ok",
        service="whale-ai",
        version=__version__,
        engine="langgraph",
        graph="qa-v1",
        llm_configured=get_llm_settings().configured,
    )


@router.get("/v1/tools", summary="只读工具清单")
async def tools() -> dict[str, Any]:
    """返回 L0 只读工具契约（名称、风险级、权限码与 JSON Schema）。"""
    return {"tools": list_tools()}


@router.post("/v1/agent/stream", summary="流式问答")
async def agent_stream(
    request: ChatRequest,
    user_id: str | None = Header(default=None, alias="X-User-Id"),
    permission_codes: str | None = Header(default=None, alias="X-Permission-Codes"),
) -> StreamingResponse:
    """SSE 流式问答。

    事件序列：``meta → tool* → delta* → done``；异常时下发 ``error`` 并结束。
    ``done.citations`` 已通过引用校验，且与正文 ``[S1]`` 标记一致。
    """
    return StreamingResponse(
        _stream(request, user_id, permission_codes),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )


async def _stream(
    request: ChatRequest,
    user_id: str | None,
    permission_codes: str | None,
) -> AsyncIterator[str]:
    """在线程中执行图，把节点事件按到达顺序转为 SSE 帧。"""
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue[Event] = asyncio.Queue()

    def emit(event: str, payload: dict[str, Any]) -> None:
        loop.call_soon_threadsafe(queue.put_nowait, (event, payload))

    def produce() -> None:
        started = time.monotonic()
        try:
            assistant = (
                request.assistant.model_dump(exclude_none=True) if request.assistant else None
            )
            state = run_agent(
                request.question,
                request.conversation_id,
                request.scope,
                user_id=user_id,
                permission_codes=permission_codes,
                emit=emit,
                assistant=assistant,
            )
            answer = state.get("answer") or ""
            conversation_id = state.get("conversation_id")
            emit(
                "done",
                {
                    "conversationId": None if conversation_id is None else str(conversation_id),
                    "tokens": max(1, len(answer) // 2),
                    "degraded": bool(state.get("degraded", True)),
                    "elapsedMillis": int((time.monotonic() - started) * 1000),
                    "citations": state.get("citations", []),
                },
            )
        except Exception as exc:  # noqa: BLE001 - 流式端点必须把异常转成 error 帧
            logger.exception("智能体问答执行失败")
            emit("error", {"message": f"问答执行失败：{exc}"})

    loop.run_in_executor(None, produce)
    deadline = get_settings().total_budget_seconds + 10
    while True:
        try:
            event, payload = await asyncio.wait_for(queue.get(), timeout=deadline)
        except TimeoutError:
            yield _sse("error", {"message": "问答超时，请稍后重试"})
            break
        yield _sse(event, payload)
        if event in ("done", "error"):
            break


@router.post("/v1/agent/confirm", summary="写操作确认")
async def agent_confirm(
    request: ConfirmRequest,
    user_id: str | None = Header(default=None, alias="X-User-Id"),
    permission_codes: str | None = Header(default=None, alias="X-Permission-Codes"),
) -> Any:
    """代理到 whale-agent 的一次性确认令牌接口。

    Python 侧不持有确认状态：``approved=true`` 转发 confirm，``false`` 转发 reject；
    身份与权限码原样透传，越权与过期由 Java 侧统一判定。
    """
    try:
        return get_agent_tool_client().confirm(
            request.confirmation_id, request.approved, user_id, permission_codes
        )
    except ToolError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
