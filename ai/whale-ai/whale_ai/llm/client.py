"""OpenAI 兼容模型客户端：仅暴露流式增量，避免引入额外 SDK。"""

from __future__ import annotations

import json
from collections.abc import Iterator

import httpx

from whale_ai.config import get_llm_settings


class LlmError(RuntimeError):
    """模型调用失败（网络、状态码或响应结构异常）。"""


class LlmClient:
    """按 OpenAI 兼容协议直连模型并逐段返回文本增量。"""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float,
        enabled: bool = True,
        max_tokens: int = 1024,
        temperature: float = 0.2,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout_seconds = timeout_seconds
        self._enabled = enabled
        self._max_tokens = max_tokens
        self._temperature = temperature
        self._transport = transport

    @property
    def available(self) -> bool:
        """是否已启用且配置密钥（否则走检索摘要降级）。"""
        return self._enabled and bool(self._api_key)

    def stream(self, messages: list[dict[str, str]]) -> Iterator[str]:
        """流式生成回答。

        Args:
            messages: OpenAI 协议消息列表（system/user）。

        Yields:
            文本增量。

        Raises:
            LlmError: HTTP 非 200 或网络失败。
        """
        payload = {
            "model": self._model,
            "messages": messages,
            "stream": True,
            "max_tokens": self._max_tokens,
            "temperature": self._temperature,
        }
        headers = {"Authorization": f"Bearer {self._api_key}", "Content-Type": "application/json"}
        with httpx.Client(timeout=self._timeout_seconds, transport=self._transport) as client:
            try:
                with client.stream(
                    "POST", f"{self._base_url}/chat/completions", json=payload, headers=headers
                ) as response:
                    if response.status_code != 200:
                        raise LlmError(f"模型返回 HTTP {response.status_code}")
                    for line in response.iter_lines():
                        delta = parse_line(line)
                        if delta:
                            yield delta
            except httpx.HTTPError as exc:
                raise LlmError(f"模型请求失败：{exc}") from exc


def parse_line(line: str) -> str | None:
    """解析一行 SSE：返回文本增量，忽略空行、注释与 ``[DONE]``。"""
    if not line or line.startswith(":") or not line.startswith("data:"):
        return None
    data = line[len("data:") :].strip()
    if data == "[DONE]":
        return None
    try:
        chunk = json.loads(data)
    except json.JSONDecodeError:
        return None
    choices = chunk.get("choices") or []
    if not choices:
        return None
    content = (choices[0].get("delta") or {}).get("content")
    return content if isinstance(content, str) and content else None


def get_llm_client() -> LlmClient:
    """按 ``WHALE_AGENT_LLM_*`` 配置创建客户端。"""
    settings = get_llm_settings()
    return LlmClient(
        base_url=settings.base_url,
        api_key=settings.api_key,
        model=settings.model,
        timeout_seconds=settings.timeout_millis / 1000,
        enabled=settings.enabled,
        max_tokens=settings.max_tokens,
        temperature=settings.temperature,
    )
