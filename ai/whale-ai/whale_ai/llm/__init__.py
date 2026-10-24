"""模型调用包：OpenAI 兼容流式客户端与提示词组装。"""

from whale_ai.llm.client import LlmClient, LlmError, get_llm_client
from whale_ai.llm.prompts import build_messages

__all__ = ["LlmClient", "LlmError", "build_messages", "get_llm_client"]
