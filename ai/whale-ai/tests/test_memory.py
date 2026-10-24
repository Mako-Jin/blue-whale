"""会话记忆单测：窗口裁剪、摘要合并、错误兜底与 Redis 键/TTL。"""

from __future__ import annotations

import json
from typing import Any

from whale_ai.memory import InMemoryStore, append_turn, load_memory
from whale_ai.memory.store import MEMORY_KEY_PREFIX, RedisMemoryStore
from whale_ai.memory.summary import first_sentence, merge_summary, strip_markers


def test_append_turn_keeps_last_window_and_builds_summary() -> None:
    store = InMemoryStore()
    for index in range(12):
        assert append_turn(store, 9, f"问题{index}", f"[S1] 回答{index}。补充说明", 10, 500)

    record = store.load(9)
    assert len(record["turns"]) == 10
    assert record["turns"][0]["question"] == "问题2"
    assert record["turns"][-1]["question"] == "问题11"
    assert "问题0" in record["summary"]
    assert "问题1" in record["summary"]
    assert "[S1]" not in record["summary"]


def test_summary_respects_max_chars() -> None:
    merged = merge_summary("旧摘要", [{"question": "长" * 100, "answer": "答" * 100}], 40)

    assert len(merged) <= 40


def test_load_memory_defaults_for_missing_conversation() -> None:
    store = InMemoryStore()

    assert load_memory(store, None, 10) == {"summary": "", "turns": []}
    assert load_memory(store, 123, 10) == {"summary": "", "turns": []}


class _BrokenStore:
    """模拟 Redis 不可用。"""

    def load(self, conversation_id: int) -> dict[str, Any]:
        del conversation_id
        raise RuntimeError("redis down")

    def save(self, conversation_id: int, record: dict[str, Any]) -> None:
        del conversation_id, record
        raise RuntimeError("redis down")


def test_memory_errors_do_not_raise() -> None:
    store = _BrokenStore()

    assert load_memory(store, 1, 10) == {"summary": "", "turns": []}
    assert append_turn(store, 1, "q", "a", 10, 100) is False


def test_redis_store_uses_prefixed_key_and_ttl() -> None:
    calls: list[tuple[str, str, int | None]] = []

    class _FakeRedis:
        payload = ""

        def get(self, key: str) -> None:
            calls.append(("get", key, None))
            return None

        def setex(self, key: str, ttl: int, payload: str) -> None:
            calls.append(("setex", key, ttl))
            self.payload = payload

    fake = _FakeRedis()
    store = RedisMemoryStore("redis://x/0", 604800, client=fake)

    assert store.load(7) == {"summary": "", "turns": []}
    store.save(7, {"summary": "", "turns": []})
    assert calls == [
        ("get", f"{MEMORY_KEY_PREFIX}7", None),
        ("setex", f"{MEMORY_KEY_PREFIX}7", 604800),
    ]
    assert json.loads(fake.payload) == {"summary": "", "turns": []}


def test_strip_markers_and_first_sentence() -> None:
    assert strip_markers("网关 [S1] 见 [S2]") == "网关 见"
    assert first_sentence("第一句。第二句") == "第一句。"
    assert first_sentence("") == ""
