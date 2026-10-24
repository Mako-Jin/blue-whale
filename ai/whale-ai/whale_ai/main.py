"""whale-ai 应用工厂与启动入口。"""

from __future__ import annotations

import logging

from fastapi import FastAPI

from whale_ai import __version__
from whale_ai.api.routes import router
from whale_ai.config import get_settings


def _configure_logging(level: str) -> None:
    """统一日志格式，级别由 ``WHALE_AI_LOG_LEVEL`` 控制。"""
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )


def create_app() -> FastAPI:
    """构建 FastAPI 应用（不注册网关、不注册 Nacos）。"""
    settings = get_settings()
    _configure_logging(settings.log_level)
    application = FastAPI(
        title="whale-ai",
        version=__version__,
        description="蓝鲸平台 LangChain/LangGraph 智能体服务（仅内网）",
    )
    application.include_router(router)
    return application


app = create_app()


def main() -> None:
    """本地启动：``python -m whale_ai.main``。"""
    import uvicorn

    settings = get_settings()
    uvicorn.run("whale_ai.main:app", host=settings.host, port=settings.port, log_level=settings.log_level.lower())


if __name__ == "__main__":
    main()
