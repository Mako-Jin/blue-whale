"""HTTP 请求/响应模型。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class ChatRequest(BaseModel):
    """智能体问答请求（与 whale-agent 现有约定对齐，接受 camelCase）。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    conversation_id: int | None = Field(default=None, alias="conversationId")
    question: str = Field(min_length=1, max_length=2000)
    scope: str | None = None


class ConfirmRequest(BaseModel):
    """写操作确认请求（T12 实现，当前仅占位）。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    confirmation_id: str = Field(alias="confirmationId")
    approved: bool = True


class HealthResponse(BaseModel):
    """健康检查响应。"""

    status: str
    service: str
    version: str
    engine: str
    graph: str
    llm_configured: bool
