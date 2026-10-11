"""TCP bridge to a running MCP-for-Blender addon socket (default 127.0.0.1:9876).

WorkBuddy 的 blender 连接器尚未被「信任」时，用它直连插件，效果等同 MCP。

协议（读 addon 源码确认）：明文 JSON，无长度前缀 / 无换行分隔，
靠 `json.loads(buffer)` 成功即判定收完。
    发送 {"type": "execute_code", "params": {"code": "..."}}
    收到 {"status": "success", "result": {"executed": true, "result": "<stdout>"}}

用法：先启动 Blender 并在 BlenderMCP 面板点 Start MCP Server，然后
    python bridge.py build_wall.py
"""
import json
import os
import socket
import sys

HOST = os.environ.get("BLENDER_HOST", "127.0.0.1")
PORT = int(os.environ.get("BLENDER_PORT", "9876"))


def _recv_json(sock, timeout):
    sock.settimeout(timeout)
    buf = b""
    while True:
        try:
            chunk = sock.recv(65536)
        except socket.timeout:
            raise TimeoutError(
                "Blender 未在 %ss 内响应（已收到 %d 字节）" % (timeout, len(buf))
            )
        if not chunk:
            raise ConnectionError("Blender 关闭了连接（已收到 %d 字节）" % len(buf))
        buf += chunk
        try:
            return json.loads(buf.decode("utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue


def send(cmd, timeout=600):
    sock = socket.create_connection((HOST, PORT), timeout=15)
    try:
        sock.sendall(json.dumps(cmd).encode("utf-8"))
        return _recv_json(sock, timeout)
    finally:
        try:
            sock.close()
        except Exception:
            pass


def run_code(code, timeout=600):
    return send({"type": "execute_code", "params": {"code": code}}, timeout=timeout)


def run_file(path, timeout=900):
    with open(path, "r", encoding="utf-8") as fh:
        return run_code(fh.read(), timeout=timeout)


if __name__ == "__main__":
    print(json.dumps(run_file(sys.argv[1]), ensure_ascii=False, indent=2))
