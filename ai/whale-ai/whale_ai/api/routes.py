"""HTTP 路由：健康检查与智能体流式问答。"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from whale_ai import __version__
from whale_ai.api.schemas import ChatRequest, ConfirmRequest, HealthResponse
from whale_ai.config import get_llm_settings
from whale_ai.graph import PLACEHOLDER_ANSWER, run_agent
from whale_ai.tools import list_tools

logger = logging.getLogger(__name__)

router = APIRouter()

SSE_HEADERS = {
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
}


def _sse(event: str, payload: dict[str, object]) -> str:
    """编码一条 SSE 事件帧。"""
    data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\ndata: {data}\n\n"


@router.get("/health", response_model=HealthResponse, summary="健康检查")
async def health() -> HealthResponse:
    """返回服务存活、图编译状态与模型配置情况（不回显密钥）。"""
    return HealthResponse(
        status="ok",
        service="whale-ai",
        version=__version__,
        engine="langgraph",
        graph="skeleton",
        llm_configured=get_llm_settings().configured,
    )


@router.get("/v1/tools", summary="只读工具清单")
async def tools() -> dict[str, object]:
    """返回 L0 只读工具契约（名称、风险级、权限码与 JSON Schema）。"""
    return {"tools": list_tools()}


@router.post("/v1/agent/stream", summary="流式问答（骨架占位）")
async def agent_stream(request: ChatRequest) -> StreamingResponse:
    """SSE 占位实现。

    事件序列与 whale-agent 保持一致：``meta → delta → done``；异常时下发 ``error``。
    骨架阶段只运行 ``intent → answer`` 图，不调用模型与工具。
    """
    return StreamingResponse(_stream(request), media_type="text/event-stream", headers=SSE_HEADERS)


async def _stream(request: ChatRequest) -> AsyncIterator[str]:
    """执行骨架图并逐帧下发。"""
    started = time.monotonic()
    try:
        state = await asyncio.to_thread(
            run_agent,
            request.question,
            request.conversation_id,
            request.scope,
            None,
        )
        conversation_id = state.get("conversation_id")
        yield _sse(
            "meta",
            {
                "conversationId": str(conversation_id) if conversation_id is not None else None,
                "intent": state.get("intent", "SKELETON"),
                "degraded": bool(state.get("degraded", False)),
                "citations": state.get("citations", []),
            },
        )
        answer = state.get("answer") or PLACEHOLDER_ANSWER
        yield _sse("delta", {"text": answer})
        yield _sse(
            "done",
            {
                "conversationId": str(conversation_id) if conversation_id is not None else None,
                "tokens": max(1, len(answer) // 4),
                "degraded": bool(state.get("degraded", False)),
                "elapsedMillis": int((time.monotonic() - started) * 1000),
                "citations": state.get("citations", []),
            },
        )
    except Exception as exc:  # noqa: BLE001 - 流式端点必须把异常转成 error 帧
        logger.exception("骨架问答执行失败")
        yield _sse("error", {"message": f"问答执行失败：{exc}"})


@router.post("/v1/agent/confirm", summary="写操作确认（未实现）")
async def agent_confirm(request: ConfirmRequest) -> None:
    """写操作确认占位：T12 接入确认协议后实现。"""
    raise HTTPException(status_code=501, detail=f"确认协议尚未实现：{request.confirmation_id}")
