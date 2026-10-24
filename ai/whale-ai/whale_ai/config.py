"""运行配置：服务参数与模型参数分源读取。

- 服务参数使用 ``WHALE_AI_`` 前缀；
- 模型参数沿用 ``WHALE_AGENT_LLM_`` 前缀，避免 Python 侧另立密钥；
- 两者均从环境变量或 ``.env`` 读取，缺失时使用安全默认值。
"""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """服务级配置。"""

    model_config = SettingsConfigDict(
        env_prefix="WHALE_AI_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "127.0.0.1"
    port: int = 8092
    log_level: str = "INFO"
    redis_url: str = "redis://127.0.0.1:6379/0"
    memory_ttl_seconds: int = 604800
    max_tool_steps: int = 6
    tool_timeout_seconds: int = 20
    total_budget_seconds: int = 120
    agent_base_url: str = "http://127.0.0.1:8090"
    # 仓库根目录：配置后按路径存在性校验引用（与 whale-agent CitationValidator 对齐），留空只做结构校验
    repo_root: str = ""
    # 单次知识检索条数
    search_top_k: int = 8
    # 结构化问题的事实条数上限
    fact_limit: int = 8


class LlmSettings(BaseSettings):
    """OpenAI 兼容模型配置，变量名与 whale-agent 保持一致。"""

    model_config = SettingsConfigDict(
        env_prefix="WHALE_AGENT_LLM_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    enabled: bool = True
    base_url: str = "https://api.openai.com/v1"
    api_key: str = ""
    model: str = "gpt-4o-mini"
    timeout_millis: int = 60000
    max_tokens: int = 1024
    temperature: float = 0.2

    @property
    def configured(self) -> bool:
        """是否已配置可用密钥（未配置时骨架不做真实模型调用）。"""
        return self.enabled and bool(self.api_key)


@lru_cache
def get_settings() -> Settings:
    """返回进程内单例服务配置。"""
    return Settings()


@lru_cache
def get_llm_settings() -> LlmSettings:
    """返回进程内单例模型配置。"""
    return LlmSettings()
