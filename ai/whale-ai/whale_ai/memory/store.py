"""会话记忆：最近 10 轮 + 摘要，Redis key ``whale:ai:memory:<conversationId>``。

- ``RedisMemoryStore`` 供多实例部署使用，JSON 单键 + TTL（默认 7 天）；
- ``InMemoryStore`` 供单测与 redis 依赖缺失时降级；
- 读写失败不阻塞问答，只记日志（记忆属于增强能力）。
"""

from __future__ import annotations

import copy
import json
import logging
from typing import Any, Protocol

from whale_ai.config import get_settings
from whale_ai.memory.summary import clip, merge_summary, strip_markers

logger = logging.getLogger(__name__)

MEMORY_KEY_PREFIX = "whale:ai:memory:"
MAX_QUESTION_CHARS = 300
MAX_ANSWER_CHARS = 800

Record = dict[str, Any]


class MemoryStore(Protocol):
    """记忆存储契约：按会话读取/覆盖整条记录。"""

    def load(self, conversation_id: int | str) -> Record:
        """读取会话记忆；不存在时返回空记录。"""
        ...

    def save(self, conversation_id: int | str, record: Record) -> None:
        """覆盖写入会话记忆并刷新 TTL。"""
        ...


class InMemoryStore:
    """进程内记忆：单测替身，也用于 redis 依赖缺失时的降级。"""

    def __init__(self) -> None:
        self._data: dict[str, Record] = {}

    def load(self, conversation_id: int | str) -> Record:
        """返回副本，避免调用方修改内部状态。"""
        record = self._data.get(_key(conversation_id))
        return copy.deepcopy(record) if record else _empty_record()

    def save(self, conversation_id: int | str, record: Record) -> None:
        """保存副本。"""
        self._data[_key(conversation_id)] = copy.deepcopy(record)


class RedisMemoryStore:
    """Redis 记忆：``SETEX key <ttl> <json>``，单键承载摘要与轮次。"""

    def __init__(self, url: str, ttl_seconds: int, client: Any = None) -> None:
        self._url = url
        self._ttl = ttl_seconds
        self._client = client

    @property
    def client(self) -> Any:
        """惰性创建客户端，未安装 redis 时不影响纯逻辑模块导入。"""
        if self._client is None:
            import redis  # 惰性导入：只有真正启用 Redis 记忆才需要该依赖

            self._client = redis.Redis.from_url(self._url, decode_responses=True)
        return self._client

    def load(self, conversation_id: int | str) -> Record:
        """读取并解析 JSON，空值按空记录处理。"""
        raw = self.client.get(_key(conversation_id))
        if not raw:
            return _empty_record()
        return _normalize(json.loads(raw))

    def save(self, conversation_id: int | str, record: Record) -> None:
        """写入并重置 TTL。"""
        payload = json.dumps(record, ensure_ascii=False)
        self.client.setex(_key(conversation_id), self._ttl, payload)


_STORE: MemoryStore | None = None


def get_memory_store() -> MemoryStore:
    """返回进程内单例记忆存储；无 redis 依赖时降级为内存实现。"""
    global _STORE
    if _STORE is not None:
        return _STORE
    settings = get_settings()
    try:
        import redis  # noqa: F401
    except ImportError:
        logger.warning("未安装 redis 依赖，会话记忆降级为进程内存储")
        _STORE = InMemoryStore()
    else:
        _STORE = RedisMemoryStore(settings.redis_url, settings.memory_ttl_seconds)
    return _STORE


def load_memory(
    store: MemoryStore,
    conversation_id: int | str | None,
    max_turns: int,
) -> Record:
    """读取可注入提示词的历史（读取失败返回空记录）。"""
    if conversation_id is None:
        return _empty_record()
    try:
        return _normalize(store.load(conversation_id), max_turns)
    except Exception as exc:  # noqa: BLE001 - 记忆读取失败不应中断问答
        logger.warning("读取会话记忆失败：%s", exc)
        return _empty_record()


def append_turn(
    store: MemoryStore,
    conversation_id: int | str | None,
    question: str,
    answer: str,
    max_turns: int,
    summary_chars: int,
) -> bool:
    """追加一轮问答，超出窗口的轮次并入摘要。

    Args:
        store: 记忆存储。
        conversation_id: 会话ID，空值表示不记忆。
        question: 用户问题。
        answer: 最终回答（写入前去引用标记）。
        max_turns: 保留的最近轮次上限。
        summary_chars: 摘要总长上限。

    Returns:
        是否写入成功（失败只记日志，不影响问答结果）。
    """
    if conversation_id is None:
        return False
    try:
        record = _normalize(store.load(conversation_id), max_turns)
        turns = record["turns"]
        turns.append(
            {
                "question": clip(question, MAX_QUESTION_CHARS),
                "answer": clip(strip_markers(answer), MAX_ANSWER_CHARS),
            }
        )
        summary = record["summary"]
        if len(turns) > max_turns:
            dropped = turns[: len(turns) - max_turns]
            turns = turns[-max_turns:]
            summary = merge_summary(summary, dropped, summary_chars)
        store.save(conversation_id, {"summary": summary, "turns": turns})
        return True
    except Exception as exc:  # noqa: BLE001 - 记忆写入失败不应中断问答
        logger.warning("写入会话记忆失败：%s", exc)
        return False


def _key(conversation_id: int | str) -> str:
    """拼接 Redis 键。"""
    return f"{MEMORY_KEY_PREFIX}{conversation_id}"


def _empty_record() -> Record:
    """空记忆结构。"""
    return {"summary": "", "turns": []}


def _normalize(record: Record | None, max_turns: int | None = None) -> Record:
    """规范记录结构，并裁剪到窗口上限。"""
    summary = str((record or {}).get("summary") or "")
    turns = [item for item in (record or {}).get("turns") or [] if isinstance(item, dict)]
    if max_turns is not None and max_turns > 0 and len(turns) > max_turns:
        turns = turns[-max_turns:]
    return {"summary": summary, "turns": turns}
