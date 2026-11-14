"""权限中心管理端接口冒烟：验证 auth 新增 7 个功能的管理端查询接口可用。

覆盖：品牌配置 / 用户组 / 权限策略 / 角色约束 / 会话与设备 / 登录历史 / OAuth 客户端 /
身份档案（脱敏视图）/ `/auth/v1/me` 是否带上全部新权限码。

用法：
    python scripts/auth-admin-pages-smoke.py
凭据（按优先级）：
    QA_USERNAME + QA_PASSWORD 环境变量
    QA_CRED_FILE 指向的文本文件（第 1 行用户名、第 2 行口令）
    deploy/backend/.env 的 QA_ADMIN_USER / QA_ADMIN_PASSWORD
只输出状态码与字段名，不打印口令与敏感值。
"""
import json
import os
import sys
import urllib.error
import urllib.request

ENV_FILE = os.environ.get(
    "QA_ENV_FILE",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "deploy", "backend", ".env"),
)
BASE = os.environ.get("QA_BASE", "http://127.0.0.1:8080")


def load_env():
    """读 .env（KEY=VALUE），文件不存在时返回空字典。"""
    values = {}
    if not os.path.exists(ENV_FILE):
        return values
    with open(ENV_FILE, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            values[key.strip()] = val.strip()
    return values


def credentials():
    """按优先级取测试账号凭据。"""
    env = load_env()
    user = os.environ.get("QA_USERNAME") or env.get("QA_ADMIN_USER", "qa_admin")
    password = os.environ.get("QA_PASSWORD") or env.get("QA_ADMIN_PASSWORD", "")
    cred_file = os.environ.get("QA_CRED_FILE")
    if cred_file and os.path.exists(cred_file):
        with open(cred_file, "r", encoding="utf-8") as fh:
            lines = [line.strip() for line in fh.read().splitlines() if line.strip()]
        if len(lines) >= 2:
            user, password = lines[0], lines[1]
    return user, password


def call(method, path, token=None, body=None):
    """发 JSON 请求，返回 (http 状态码, 解析后的响应体)。"""
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as err:
        raw = err.read()
        try:
            return err.code, json.loads(raw)
        except json.JSONDecodeError:
            return err.code, {"raw": raw[:200].decode(errors="replace")}


CHECKS = [
    ("品牌配置", "/brand/v1/page?current=1&size=5"),
    ("用户组", "/user-group/v1/page?current=1&size=5"),
    ("权限策略", "/perm-strategy/v1/page?current=1&size=5"),
    ("角色约束", "/role-constraint/v1/page?current=1&size=5"),
    ("会话与设备", "/session/v1/page?current=1&size=5"),
    ("登录历史", "/login-history/v1/page?current=1&size=5"),
    ("OAuth 客户端", "/oauth-client/v1/page?current=1&size=5"),
]

WANTED_CODES = [
    "sys:brand:list", "sys:user:profile:view", "sys:user:group:list",
    "sys:perm:strategy:list", "sys:role:constraint:list", "sys:session:list",
    "sys:login:history:list", "sys:oauth:client:list", "sys:file:list",
]


def main():
    user, password = credentials()
    if not password:
        print("SKIP: 未取到测试账号凭据（QA_USERNAME/QA_PASSWORD 或 .env 的 QA_ADMIN_*）")
        return 0

    status, payload = call("POST", "/auth/v1/login", body={"username": user, "password": password})
    token = (payload.get("data") or {}).get("accessToken") if status == 200 else None
    if not token:
        print(f"FAIL login http={status} code={payload.get('code')} message={payload.get('message')}")
        return 1
    print("OK   login")

    failed = 0
    for name, path in CHECKS:
        status, payload = call("GET", path, token=token)
        data = payload.get("data") or {}
        if status != 200 or payload.get("code") != 0:
            print(f"FAIL {name} http={status} code={payload.get('code')} message={payload.get('message')}")
            failed += 1
        elif "records" not in data or "total" not in data:
            print(f"FAIL {name} 缺分页字段 keys={sorted(data)[:8]}")
            failed += 1
        else:
            print(f"OK   {name} total={data.get('total')}")

    # 身份档案：取一个真实用户，读脱敏视图（未建档为 404 属正常）
    status, payload = call("GET", "/user/v1/page?current=1&size=1", token=token)
    records = ((payload.get("data") or {}).get("records") or []) if status == 200 else []
    if not records:
        print("FAIL 身份档案：取不到用户 ID")
        failed += 1
    else:
        user_id = records[0].get("id")
        status, payload = call("GET", f"/user-profile/v1/{user_id}/view", token=token)
        if status == 200 and payload.get("code") == 0:
            keys = sorted(payload.get("data") or {})
            print(f"OK   身份档案 userId={user_id} keys={keys[:8]}")
        elif (status == 200 and payload.get("code") == 404) or status == 404:
            print(f"OK   身份档案 userId={user_id} 未建档（前端按空表单处理）")
        else:
            print(f"FAIL 身份档案 http={status} code={payload.get('code')} message={payload.get('message')}")
            failed += 1

    status, payload = call("GET", "/auth/v1/me", token=token)
    me = (payload.get("data") or {}) if status == 200 else {}
    codes = me.get("permissions") or me.get("permissionCodes") or []
    missing = [code for code in WANTED_CODES if code not in codes]
    if missing:
        print(f"FAIL /auth/v1/me 缺权限码 {missing}（共 {len(codes)}）")
        failed += 1
    else:
        print(f"OK   /auth/v1/me 含全部新权限码（共 {len(codes)}）")

    total = len(CHECKS) + 2
    print(f"\n===== 汇总：{total - failed}/{total} 通过 =====")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
