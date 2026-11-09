"""HTTP 请求/响应模型。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class ModelParams(BaseModel):
    """助手级模型参数。

    仅允许覆盖模型名与采样参数；``base_url`` / ``api_key`` 一律取全局配置，
    助手不得自带密钥（安全口径见 docs/plans/wave6.01-ai-assistant.md §5.3）。
    """

    model_config = ConfigDict(extra="forbid")

    model: str | None = Field(default=None, max_length=128)
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_tokens: int | None = Field(default=None, ge=1, le=32768)


class AssistantConfig(BaseModel):
    """助手配置（Wave 6.01）。

    由 ``whale-assistant``（8087）解析 ``asst_definition`` 后随请求下发，
    Python 侧保持无状态、不读业务库；缺省时等同默认平台助手。
    """

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    assistant_id: str | None = Field(default=None, max_length=64, alias="assistantId")
    system_prompt: str | None = Field(default=None, max_length=8000, alias="systemPrompt")
    model_params: ModelParams | None = Field(default=None, alias="modelParams")
    # 工具白名单：只能收敛注册表内工具，白名单为空/缺省 = 全部放行
    tool_whitelist: list[str] | None = Field(default=None, alias="toolWhitelist")


class ChatRequest(BaseModel):
    """智能体问答请求（与 whale-agent 现有约定对齐，接受 camelCase）。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    conversation_id: int | None = Field(default=None, alias="conversationId")
    question: str = Field(min_length=1, max_length=2000)
    scope: str | None = None
    assistant: AssistantConfig | None = None


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
