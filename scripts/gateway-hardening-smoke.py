"""网关整改第三批（P2-7 / P2-14）实机回归：actuator 内网收口 + 白名单精简后的放行矩阵。

两个断言域：
1. `/actuator/**` 来源收口：回环/内网放行，公网直连或经代理转发（X-Forwarded-For 首段为公网）一律 403；
   非 actuator 路径不受影响。
2. 白名单精简后「本地仅登录闭环 + 外部回调」：被迁到远程名单（auth 库 sys_gateway_whitelist）的放行项
   仍必须免 JWT（判据：不是网关 `{"code":401,"message":"未认证"}`），受保护路径仍必须 401。

用法：
    python scripts/gateway-hardening-smoke.py
    GW_BASE=http://127.0.0.1:8080 python scripts/gateway-hardening-smoke.py

注意：放行类用例只断言「未被网关鉴权层拦下」，下游服务未起时返回 503/404 也算通过；
      受保护类用例必须以 `未认证` 失败，两者判据互斥，才能区分「放行」与「误判」。
"""
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("GW_BASE", "http://127.0.0.1:8080").rstrip("/")
TIMEOUT = float(os.environ.get("GW_TIMEOUT", "15"))

GATEWAY_UNAUTHENTICATED = "未认证"
ACTUATOR_DENIED = "管理端点仅限内网访问"
GOVERNANCE_DENIED = "路由治理端点拒绝访问"

results = []


def call(method, path, headers=None, body=None):
    """发起一次请求，返回 (status, 解析后的 JSON 或原文片段, 响应头)。"""
    hdrs = {"Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            raw = resp.read()
            return resp.status, parse(raw), dict(resp.headers)
    except urllib.error.HTTPError as err:
        raw = err.read()
        return err.code, parse(raw), dict(err.headers or {})
    except urllib.error.URLError as err:
        return 0, {"raw": "连接失败: %s" % err.reason}, {}


def parse(raw):
    """尽量按 JSON 解析，失败时返回截断原文。"""
    text = raw.decode("utf-8", errors="replace")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {"raw": text[:200]}


def record(name, ok, detail):
    """记录一条断言结果并即时打印。"""
    results.append((name, ok, detail))
    print(("PASS  " if ok else "FAIL  ") + name + "  -> " + detail)


def record_skip(name, reason):
    """记录一条跳过用例：网关未加载该路由时鉴权链不参与，避免把 404 误记成「放行」。"""
    results.append((name, None, reason))
    print("SKIP  " + name + "  -> " + reason)


def message_of(body):
    """取业务响应里的提示信息（网关 Result 结构为 {code,message,...}）。"""
    if isinstance(body, dict):
        for key in ("message", "msg", "error_description"):
            value = body.get(key)
            if isinstance(value, str):
                return value
        if "raw" in body:
            return body["raw"]
    return ""


def code_of(body):
    """取业务响应里的 code 字段（缺省返回 None）。"""
    if isinstance(body, dict):
        value = body.get("code")
        if isinstance(value, int):
            return value
    return None


def expect_actuator(name, xff, want_status, want_message=None):
    """actuator 用例：来源由 X-Forwarded-For 模拟，直连地址恒为回环。"""
    headers = {}
    if xff is not None:
        headers["X-Forwarded-For"] = xff
    status, body, _ = call("GET", "/actuator/health", headers)
    ok = status == want_status
    if ok and want_message:
        ok = message_of(body) == want_message
    detail = "status=%s message=%s" % (status, message_of(body) or "-")
    record("%s [XFF=%s]" % (name, xff or "无"), ok, detail)


def expect_whitelisted(name, method, path, body=None, headers=None):
    """白名单用例：只要不是网关鉴权层写的 401 未认证，即视为放行到后续链。

    两类 404 需要区分（网关自己会区分，见 GatewayExceptionHandler）：
    - `路由不存在` = 路由命中但下游无实例（NotFoundException），鉴权链已走完 → 放行成立；
    - `下游资源不存在` = 没有路由，请求落到静态资源处理器，鉴权链未参与 → 记 SKIP，
      不能当成「放行」的证据（白名单条目本身也不会被引用）。
    """
    status, resp, _ = call(method, path, headers, body)
    message = message_of(resp)
    if status == 404 and message == "下游资源不存在":
        record_skip(name, "网关无该路由（静态资源 404），鉴权链未参与")
        return
    blocked = status == 401 and message == GATEWAY_UNAUTHENTICATED
    note = "（下游无实例，路由已命中）" if status == 404 and message == "路由不存在" else ""
    record(name, not blocked, "status=%s message=%s%s" % (status, message or "-", note))


def expect_protected(name, method, path, body=None):
    """受保护用例：必须被网关鉴权层拦下（401 未认证）。"""
    status, resp, _ = call(method, path, None, body)
    ok = status == 401 and message_of(resp) == GATEWAY_UNAUTHENTICATED
    record(name, ok, "status=%s message=%s" % (status, message_of(resp) or "-"))


def main():
    """执行全部用例并按失败数返回退出码。"""
    print("== 网关地址: %s ==" % BASE)

    print("\n-- 1. /actuator/** 来源收口（P2-14）--")
    expect_actuator("回环直连放行", None, 200)
    expect_actuator("内网 XFF 放行", "192.168.1.10", 200)
    expect_actuator("IPv6 ULA XFF 放行", "fd00::1", 200)
    expect_actuator("公网 XFF 拒绝", "8.8.8.8", 403, ACTUATOR_DENIED)
    expect_actuator("多级代理首段公网拒绝", "8.8.8.8, 10.1.1.1", 403, ACTUATOR_DENIED)
    expect_actuator("IPv6 公网 XFF 拒绝", "2001:4860:4860::8888", 403, ACTUATOR_DENIED)
    expect_actuator("非 IP 字面量 XFF 拒绝", "evil.example.com", 403, ACTUATOR_DENIED)

    status, body, _ = call("GET", "/actuator/routeGovernance")
    record("治理端点内网无令牌 403（治理令牌兜底）",
           status == 403 and message_of(body) == GOVERNANCE_DENIED,
           "status=%s message=%s" % (status, message_of(body) or "-"))

    status, body, _ = call("GET", "/actuator/routeGovernance", {"X-Forwarded-For": "8.8.8.8"})
    record("治理端点公网 XFF 先被内网过滤器拦下",
           status == 403 and message_of(body) == ACTUATOR_DENIED,
           "status=%s message=%s" % (status, message_of(body) or "-"))

    status, body, _ = call("GET", "/erp/v1/actuator-probe", {"X-Forwarded-For": "8.8.8.8"})
    record("业务路径带公网 XFF 不受收口影响（仍走 JWT）",
           status == 401 and message_of(body) == GATEWAY_UNAUTHENTICATED,
           "status=%s message=%s" % (status, message_of(body) or "-"))

    print("\n-- 2. 白名单放行（原本地项已迁至远程名单，P2-7）--")
    expect_whitelisted("登录页品牌解析 /brand/v1/resolve", "GET", "/brand/v1/resolve")
    expect_whitelisted("登录 /auth/v1/login", "POST", "/auth/v1/login",
                       {"username": "probe_not_exist", "password": "probe"})
    expect_whitelisted("续期 /auth/v1/refresh", "POST", "/auth/v1/refresh",
                       {"refreshToken": "probe"})
    expect_whitelisted("OAuth2 JWKS /oauth2/jwks", "GET", "/oauth2/jwks")
    expect_whitelisted("OAuth2 发现文档 /.well-known", "GET",
                       "/.well-known/oauth-authorization-server")
    expect_whitelisted("接口文档 /v3/api-docs", "GET", "/v3/api-docs")
    expect_whitelisted("接口文档首页 /doc.html", "GET", "/doc.html")
    expect_whitelisted("站点图标 /favicon.ico", "GET", "/favicon.ico")
    expect_whitelisted("前端上报 /audit/log/frontend/batch", "POST",
                       "/audit/log/frontend/batch", [])
    expect_whitelisted("行为上报 /audit/log/behavior/batch", "POST",
                       "/audit/log/behavior/batch", [])
    expect_whitelisted("三方登录授权起始 /social/v1/*/authorize", "GET",
                       "/social/v1/wechat/authorize")
    expect_whitelisted("三方登录回调 /social/v1/*/callback", "GET",
                       "/social/v1/wechat/callback?code=probe&state=probe")
    expect_whitelisted("三方登录换令牌 /social/v1/login", "POST", "/social/v1/login",
                       {"code": "probe"})
    expect_whitelisted("签章回调 /contract/sign/callback/**", "POST",
                       "/contract/sign/callback/probe", {})
    expect_whitelisted("WebIM 握手 /im/ws", "GET", "/im/ws?ticket=probe")
    expect_whitelisted("3D 世界频道 /im/world", "GET", "/im/world?ticket=probe")
    expect_whitelisted("文档协作 /doc/ws", "GET", "/doc/ws?ticket=probe")
    expect_whitelisted("音频流 /music/stream/**", "GET", "/music/stream/probe")
    expect_whitelisted("视频流 /video/stream/**", "GET", "/video/stream/probe")

    print("\n-- 3. 受保护路径仍须 JWT --")
    expect_protected("当前用户 /auth/v1/me", "GET", "/auth/v1/me")
    expect_protected("业务接口 /erp/v1/order/list", "GET", "/erp/v1/order/list")
    expect_protected("白名单同前缀查询端点 /audit/log/page", "GET", "/audit/log/page")

    failed = [name for name, ok, _ in results if ok is False]
    skipped = [name for name, ok, _ in results if ok is None]
    passed = len(results) - len(failed) - len(skipped)
    print("\n== 合计 %d 项：通过 %d，跳过 %d，失败 %d ==" % (len(results), passed, len(skipped), len(failed)))
    for name in skipped:
        print("  跳过: " + name)
    for name in failed:
        print("  失败: " + name)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
