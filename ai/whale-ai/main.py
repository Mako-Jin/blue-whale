"""兼容服务目录约定的 ``uvicorn main:app`` 入口。"""

from whale_ai.main import app

__all__ = ["app"]
