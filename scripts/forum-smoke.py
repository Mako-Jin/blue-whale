"""论坛服务（鲸议台）实机冒烟：直打 whale-forum，绕过网关注入可信身份头。

用法：
    # 常规全链路（本机直起 19011）
    python scripts/forum-smoke.py

    # 限流专项：需服务端按默认配置起（不回退间隔），单跑
    python scripts/forum-smoke.py --rate

报告落 `.workbuddy/tmp/forum-smoke-report.json`（`FORUM_SMOKE_REPORT` 可覆盖）。

口径：
- 业务成功 = HTTP 200 且 `code == 0`；负向用例断言业务码（30001/30020/30031/30041/30042/403 …）。
- 每条用例独立记录，前一步失败不中断；依赖缺失的后续用例记为 SKIP（detail 里注明）。
- ID 在本项目统一按字符串序列化（Long→String），断言前统一 `int()`。
- 脚本自带数据隔离：版块/标签/标题都带本次 `RUN` 后缀，可重复执行。
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime

BASE = os.environ.get("FORUM_SMOKE_BASE", "http://127.0.0.1:19011").rstrip("/")
FILE_BASE = os.environ.get("FORUM_SMOKE_FILE_BASE", "http://127.0.0.1:8085").rstrip("/")
REPORT = os.environ.get("FORUM_SMOKE_REPORT", ".workbuddy/tmp/forum-smoke-report.json")
RATE_REPORT = os.environ.get("FORUM_SMOKE_RATE_REPORT", ".workbuddy/tmp/forum-smoke-rate-report.json")
MODE = "rate" if "--rate" in sys.argv else "full"

RUN = datetime.now().strftime("%m%d%H%M%S")

FORUM_PERMS = [
    "sys:forum:browse",
    "sys:forum:thread:add",
    "sys:forum:thread:edit",
    "sys:forum:thread:remove",
    "sys:forum:post:add",
    "sys:forum:reaction:use",
    "sys:forum:manage:list",
    "sys:forum:manage:audit",
    "sys:forum:manage:dispose",
    "sys:forum:manage:board",
    "sys:forum:hot:refresh",
    "sys:forum:manage:ops",
]
MEMBER_PERMS = [
    "sys:forum:browse",
    "sys:forum:thread:add",
    "sys:forum:thread:edit",
    "sys:forum:thread:remove",
    "sys:forum:post:add",
    "sys:forum:reaction:use",
]

ADMIN_ID = "900000101"
MEMBER_ID = "900000301"
MEMBER2_ID = "900000302"


def identity(user_id, username, perms, tenant="1", roles="tenant_admin"):
    return {
        "X-User-Id": user_id,
        "X-Username": username,
        "X-Tenant-Id": tenant,
        "X-User-Roles": roles,
        "X-Permission-Codes": ",".join(perms),
    }


ADMIN = identity(ADMIN_ID, "qa_admin", FORUM_PERMS)
MEMBER = identity(MEMBER_ID, "smoke_member", MEMBER_PERMS, roles="member")
MEMBER2 = identity(MEMBER2_ID, "smoke_member2", MEMBER_PERMS, roles="member")
TENANT2 = identity(ADMIN_ID, "qa_admin", FORUM_PERMS, tenant="2", roles="tenant_admin")

RESULTS = []

# 独立身份池：每条写路径用「本轮专属新账号」，让冒烟不依赖服务端把限流回退成 0。
# 带 MANAGE_AUDIT 的身份可跳过「新用户前 3 帖必审」，用于需要立刻 PUBLISHED 的帖子。
_SEQ = [0]


def fresh(perms=None, tag="actor"):
    _SEQ[0] += 1
    uid = f"95{RUN}{_SEQ[0]:03d}"
    return identity(uid, f"smoke_{tag}_{_SEQ[0]}", perms or (MEMBER_PERMS + ["sys:forum:manage:audit"]),
                    roles="member")


def record(name, ok, detail=""):
    RESULTS.append({"name": name, "ok": bool(ok), "detail": str(detail)[:300]})
    print(f"  {'OK  ' if ok else 'FAIL'} {name} -> {str(detail)[:200]}")
    return ok


def skip(name, why):
    RESULTS.append({"name": name, "ok": True, "skipped": True, "detail": f"SKIP {why}"})
    print(f"  SKIP {name} -> {why}")


def section(title):
    print(f"\n=== {title} ===")


def http(method, path, body=None, params=None, who=None, base=None, raw=False,
         content=None, content_type=None, timeout=30):
    url = (base or BASE) + path
    if params:
        clean = {k: v for k, v in params.items() if v is not None}
        if clean:
            url += "?" + urllib.parse.urlencode(clean)
    data = None
    headers = dict(who or ADMIN)
    if content is not None:
        data = content
        if content_type:
            headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = resp.read()
            status = resp.status
            meta_headers = dict(resp.headers)
    except urllib.error.HTTPError as err:
        payload = err.read()
        status = err.code
        meta_headers = dict(err.headers or {})
    except Exception as err:  # 连接层失败
        return {"code": -1, "message": f"连接失败: {err}"}, {"status": -1, "headers": {}}
    if raw:
        return payload, {"status": status, "headers": meta_headers}
    try:
        parsed = json.loads(payload.decode("utf-8")) if payload else {}
    except json.JSONDecodeError:
        parsed = {"code": -2, "message": payload[:200].decode("utf-8", "replace")}
    return parsed, {"status": status, "headers": meta_headers}


def call(name, method, path, body=None, params=None, who=None, expect=0, raw=False,
         base=None, content=None, content_type=None):
    """调用并断言业务码；返回 data（失败返回 None）。"""
    payload, meta = http(method, path, body=body, params=params, who=who, base=base,
                         raw=raw, content=content, content_type=content_type)
    if raw:
        record(name, meta["status"] == expect,
               f"http={meta['status']} ct={meta['headers'].get('Content-Type', '')}")
        return payload
    code = payload.get("code")
    ok = meta["status"] == 200 and code == expect
    detail = f"http={meta['status']} code={code}"
    if not ok:
        detail += " " + str(payload.get("message") or payload)[:160]
    record(name, ok, detail)
    return payload.get("data")


def call_retry(name, method, path, body=None, params=None, who=None, expect=0,
               attempts=4, raw=False, base=None, content=None, content_type=None):
    """限流（30042）重试：配置把间隔回退为 0 后，DB 判定仍按秒粒度，
    同一秒内的连发会被拒。这是限流的正常语义，冒烟里按「稍后重试」处理。"""
    data = None
    for index in range(attempts):
        payload, meta = http(method, path, body=body, params=params, who=who, base=base,
                             raw=raw, content=content, content_type=content_type)
        code = payload.get("code") if isinstance(payload, dict) else None
        if code == 30042 and expect != 30042 and index < attempts - 1:
            time.sleep(1.05)
            continue
        ok = meta["status"] == 200 and code == expect
        detail = f"http={meta['status']} code={code}"
        if not ok:
            detail += " " + str((payload or {}).get("message") or payload)[:160]
        record(name, ok, detail)
        data = (payload or {}).get("data") if isinstance(payload, dict) else None
        break
    return data


def int_of(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def multipart(field, filename, content, content_type="application/octet-stream"):
    boundary = "----bw" + uuid.uuid4().hex
    body = b""
    body += f"--{boundary}\r\n".encode()
    body += f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'.encode()
    body += f"Content-Type: {content_type}\r\n\r\n".encode()
    body += content
    body += f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


# 1x1 透明 PNG：材料预览只放行 image/*，必须真图片
PNG_BYTES = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000a49444154789c6360000002000100ffff03000006000557bfabd40000000049454e44ae426082"
)


def upload_file(name, who, filename, content, content_type):
    # 上传人必须是当前论坛用户（论坛按 uploaderId 校验材料归属），
    # 但 file 服务自身的上传权限走平台超管旁路，不污染论坛侧的权限负向用例。
    uploader = dict(who)
    uploader["X-Permission-Codes"] = "super_admin"
    body, ctype = multipart("file", filename, content, content_type)
    data = call(name, "POST", "/file/v1/upload", who=uploader, base=FILE_BASE,
                content=body, content_type=ctype)
    return (data or {}).get("id")


def main():
    state = {}
    if MODE == "rate":
        rate_suite(state)
        return finish()

    suite_core(state)
    suite_post(state)
    suite_interaction(state)
    suite_hot_search(state)
    suite_moderation(state)
    suite_appeal(state)
    suite_profile_follow(state)
    suite_reconcile_negative(state)
    return finish()


# ---------------------------------------------------------------- 基线/版块/发帖
def suite_core(state):
    section("A 基线：健康、元数据、契约")

    payload, _ = http("GET", "/actuator/health")
    record("health", payload.get("status") == "UP", payload.get("status"))

    data = call("meta", "GET", "/forum/v1/meta", who=MEMBER)
    if data:
        record("meta.service", data.get("service") == "whale-forum", data.get("service"))
        record("meta.types", "NORMAL" in (data.get("threadTypes") or []),
               data.get("threadTypes"))
        record("meta.limits", (data.get("limits") or {}).get("maxTitleLength", 0) > 0,
               (data.get("limits") or {}).get("maxTitleLength"))

    payload, meta = http("GET", "/v3/api-docs", raw=True)
    try:
        docs = json.loads(payload.decode("utf-8"))
        paths = len(docs.get("paths") or {})
    except Exception:
        paths = 0
    record("api-docs", meta["status"] == 200 and paths >= 45, f"http={meta['status']} paths={paths}")
    for probe in ("/forum/v1/my/feed", "/forum/v1/appeal/material/{fileId}/preview"):
        record(f"api-docs.{probe.split('/')[-1]}", probe in (docs.get("paths") or {}), probe)

    section("B 版块：创建 / 判重 / 改 / 可见性")
    boards = call("board.page", "GET", "/forum/v1/board/page", who=MEMBER)
    record("board.page.list", isinstance(boards, list), f"n={len(boards or [])}")

    board = call("board.create", "POST", "/forum/v1/board", who=ADMIN, body={
        "code": f"smoke-{RUN}", "name": f"冒烟版块{RUN}", "description": "论坛实机冒烟",
        "visibility": "ALL", "auditMode": 0, "postPolicy": "OPEN", "sortNo": 99,
    })
    state["board"] = int_of((board or {}).get("id"))
    record("board.create.id", state["board"] is not None, state["board"])

    call("board.create.duplicate", "POST", "/forum/v1/board", who=ADMIN, expect=30070, body={
        "code": f"smoke-{RUN}", "name": "重复编码",
    })
    call("board.create.badcode", "POST", "/forum/v1/board", who=ADMIN, expect=400, body={
        "code": "BAD CODE", "name": "非法编码",
    })
    call("board.create.forbidden", "POST", "/forum/v1/board", who=MEMBER, expect=403, body={
        "code": f"smoke-x-{RUN}", "name": "无权限建版",
    })

    if state["board"]:
        updated = call("board.update", "PUT", f"/forum/v1/board/{state['board']}", who=ADMIN, body={
            "code": f"smoke-{RUN}", "name": f"冒烟版块{RUN}-改", "auditMode": 0,
            "postPolicy": "OPEN", "visibility": "ALL", "sortNo": 99,
        })
        record("board.update.name", (updated or {}).get("name", "").endswith("-改"),
               (updated or {}).get("name"))

    audit_board = call("board.create.auditMode", "POST", "/forum/v1/board", who=ADMIN, body={
        "code": f"smoke-audit-{RUN}", "name": f"先审版块{RUN}", "auditMode": 1,
        "postPolicy": "OPEN", "visibility": "ALL",
    })
    state["audit_board"] = int_of((audit_board or {}).get("id"))

    section("C 发帖：标签 / 浏览去重 / 检索口径")
    if not state.get("board"):
        skip("thread.*", "版块创建失败")
        return
    author = fresh()
    state["author"] = author
    thread = call_retry("thread.create", "POST", "/forum/v1/thread", who=author, body={
        "boardId": state["board"], "threadType": "NORMAL",
        "title": f"冒烟主帖 {RUN}",
        "content": f"<p>论坛冒烟正文 {RUN} 关键字 鲸议台全文检索</p>",
        "tags": f"验收,冒烟{RUN}",
    })
    state["thread"] = int_of((thread or {}).get("id"))
    if not state["thread"]:
        skip("thread.detail", "发帖失败")
        return
    record("thread.create.status", (thread or {}).get("status") == "PUBLISHED",
           (thread or {}).get("status"))
    record("thread.create.nextFloor", int_of((thread or {}).get("nextFloor")) == 2,
           (thread or {}).get("nextFloor"))
    record("thread.create.tags", f"冒烟{RUN}" in ((thread or {}).get("tags") or ""),
           (thread or {}).get("tags"))

    detail = call("thread.detail", "GET", f"/forum/v1/thread/{state['thread']}", who=author)
    record("thread.detail.status", (detail or {}).get("status") == "PUBLISHED",
           (detail or {}).get("status"))
    views = int_of((detail or {}).get("viewCount")) or 0
    record("thread.detail.viewCount", views >= 1, views)

    again = call("thread.detail.dedupe", "GET", f"/forum/v1/thread/{state['thread']}", who=author)
    record("thread.view.dedupe", int_of((again or {}).get("viewCount")) == views,
           f"{views} -> {(again or {}).get('viewCount')}")

    other = call("thread.detail.otherUser", "GET", f"/forum/v1/thread/{state['thread']}",
                 who=MEMBER)
    record("thread.view.other", int_of((other or {}).get("viewCount")) == views + 1,
           f"{views} -> {(other or {}).get('viewCount')}")

    page = call("thread.page.board", "GET", "/forum/v1/thread/page", who=MEMBER,
                params={"boardId": state["board"], "size": 50})
    ids = [int_of(r.get("id")) for r in (page or {}).get("records") or []]
    record("thread.page.contains", state["thread"] in ids, f"total={(page or {}).get('total')}")

    by_tag = call("thread.page.tag", "GET", "/forum/v1/thread/page", who=MEMBER,
                  params={"boardId": state["board"], "tag": f"冒烟{RUN}", "size": 20})
    record("thread.page.tag.hit", state["thread"] in
           [int_of(r.get("id")) for r in (by_tag or {}).get("records") or []],
           f"total={(by_tag or {}).get('total')}")

    tags = call("tag.list", "GET", "/forum/v1/tag/list", who=MEMBER, params={"limit": 50})
    matched = [t for t in (tags or []) if t.get("tag") == f"冒烟{RUN}"]
    record("tag.list.aggregate", bool(matched) and int_of(matched[0].get("threadCount")) >= 1,
           matched[:1])

    edited = call("thread.update", "PUT", f"/forum/v1/thread/{state['thread']}", who=author, body={
        "title": f"冒烟主帖 {RUN} 已编辑",
        "tags": f"验收,改后{RUN}",
        "reason": "冒烟：改标题与标签",
    })
    record("thread.update.tags", f"改后{RUN}" in ((edited or {}).get("tags") or ""),
           (edited or {}).get("tags"))
    old_tag = call("thread.page.oldTagGone", "GET", "/forum/v1/thread/page", who=MEMBER,
                   params={"boardId": state["board"], "tag": f"冒烟{RUN}", "size": 10})
    record("thread.update.oldTagDetached",
           state["thread"] not in [int_of(r.get("id")) for r in (old_tag or {}).get("records") or []],
           f"total={(old_tag or {}).get('total')}")

    section("C2 敏感词：BLOCK / REPLACE / PENDING")
    call("sensitive.add.block", "POST", "/forum/v1/admin/sensitive", who=ADMIN, body={
        "word": f"违禁{RUN}", "action": "BLOCK", "category": "smoke"})
    call("sensitive.add.replace", "POST", "/forum/v1/admin/sensitive", who=ADMIN, body={
        "word": f"替换{RUN}", "action": "REPLACE", "category": "smoke"})
    call("sensitive.add.pending", "POST", "/forum/v1/admin/sensitive", who=ADMIN, body={
        "word": f"待审{RUN}", "action": "PENDING", "category": "smoke"})
    call("sensitive.reload", "POST", "/forum/v1/admin/sensitive/reload", who=ADMIN)
    call("sensitive.page", "GET", "/forum/v1/admin/sensitive/page", who=ADMIN,
         params={"keyword": f"R{RUN[-4:]}", "size": 50})

    call_retry("sensitive.block.rejected", "POST", "/forum/v1/thread", who=fresh(), expect=30031, body={
        "boardId": state["board"], "title": f"含违禁{RUN}词", "content": "<p>正文</p>"})

    replaced = call_retry("sensitive.replace.masked", "POST", "/forum/v1/thread", who=fresh(), body={
        "boardId": state["board"], "title": f"含替换{RUN}词", "content": f"<p>正文替换{RUN}词</p>"})
    state["thread_replace"] = int_of((replaced or {}).get("id"))
    body_text = (replaced or {}).get("content") or ""
    record("sensitive.replace.content", "*" in body_text, body_text[:80])

    pending = call_retry("sensitive.pending.status", "POST", "/forum/v1/thread", who=fresh(), body={
        "boardId": state["board"], "title": f"含待审{RUN}词", "content": "<p>正文</p>"})
    state["thread_pending"] = int_of((pending or {}).get("id"))
    record("sensitive.pending.isPending", (pending or {}).get("status") == "PENDING",
           (pending or {}).get("status"))


# --------------------------------------------------------------------- 回帖
def suite_post(state):
    section("D 回帖：楼层 / 排序 / 引用 / 并发分配")
    if not state.get("thread"):
        skip("post.*", "主帖缺失")
        return
    tid = state["thread"]
    replier = fresh(MEMBER_PERMS, "replier")
    state["replier"] = replier
    first = call_retry("post.create", "POST", f"/forum/v1/thread/{tid}/post", who=replier, body={
        "content": f"<p>第一层回复 {RUN} @qa_admin</p>"})
    state["post2"] = int_of((first or {}).get("id"))
    record("post.floor.2", int_of((first or {}).get("floor")) == 2,
           (first or {}).get("floor"))

    second = call_retry("post.create.other", "POST", f"/forum/v1/thread/{tid}/post",
                        who=fresh(MEMBER_PERMS, "replier"), body={
        "content": "<p>第二层回复</p>"})
    record("post.floor.3", int_of((second or {}).get("floor")) == 3,
           (second or {}).get("floor"))

    if state.get("post2"):
        quoted = call_retry("post.quote", "POST", f"/forum/v1/thread/{tid}/post",
                            who=fresh(MEMBER_PERMS, "replier"), body={
            "content": "<p>引用回复</p>", "replyToPostId": state["post2"]})
        state["quoted"] = int_of((quoted or {}).get("id"))
        # 发帖响应不保证回填 quoted（前端靠列表重载拿），引用块以列表口径为准
        quote_page = call("post.page.quote", "GET", f"/forum/v1/thread/{tid}/post/page", who=ADMIN,
                          params={"current": 1, "size": 50, "order": "asc"})
        quote_row = next((p for p in (quote_page or {}).get("records") or []
                          if int_of(p.get("id")) == state.get("quoted")), {})
        record("post.quote.floor", int_of((quote_row.get("quoted") or {}).get("floor")) == 2,
               (quote_row.get("quoted") or {}).get("floor"))

    asc = call("post.page.asc", "GET", f"/forum/v1/thread/{tid}/post/page", who=MEMBER,
               params={"current": 1, "size": 50, "order": "asc"})
    asc_floors = [int_of(p.get("floor")) for p in (asc or {}).get("records") or []]
    record("post.page.asc.order", asc_floors == sorted(asc_floors) and asc_floors[:2] == [2, 3],
           asc_floors)

    desc = call("post.page.desc", "GET", f"/forum/v1/thread/{tid}/post/page", who=MEMBER,
                params={"current": 1, "size": 50, "order": "desc"})
    desc_floors = [int_of(p.get("floor")) for p in (desc or {}).get("records") or []]
    record("post.page.desc.order", desc_floors == sorted(desc_floors, reverse=True),
           desc_floors[:4])

    before_concurrent = call("thread.detail.beforeConcurrent", "GET", f"/forum/v1/thread/{tid}",
                             who=ADMIN)
    start_floor = int_of((before_concurrent or {}).get("nextFloor")) or 4
    # 并发楼层分配：M1 验收口径「50 并发无重号」，这里跑 32 个并发（限流已回退）
    concurrency = 32
    floors, errors = [], []
    lock = threading.Lock()

    def worker(idx):
        # 每个并发请求用独立身份：楼层分配要验的是唯一键/原子分配，
        # 同一个人的「1 秒 1 条」限流会把并发压成串行（30042），混淆结论。
        worker_id = identity(str(900000400 + idx), f"smoke_cc{idx}", MEMBER_PERMS, roles="member")
        payload, meta = http("POST", f"/forum/v1/thread/{tid}/post", who=worker_id,
                             body={"content": f"<p>并发楼层 {idx}</p>"})
        with lock:
            if meta.get("status") == 200 and payload.get("code") == 0:
                floors.append(int_of((payload.get("data") or {}).get("floor")))
            else:
                errors.append(f"{payload.get('code')}:{payload.get('message')}")

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    expected = list(range(start_floor, start_floor + concurrency))
    record("post.concurrent.success", not errors and len(floors) == concurrency,
           f"ok={len(floors)} err={errors[:2]}")
    record("post.concurrent.uniqueFloors", sorted(floors) == expected,
           f"min={min(floors) if floors else None} max={max(floors) if floors else None} n={len(set(floors))}")

    detail = call("thread.detail.afterPosts", "GET", f"/forum/v1/thread/{tid}", who=ADMIN)
    record("thread.replyCount", int_of((detail or {}).get("replyCount")) == concurrency + 3,
           (detail or {}).get("replyCount"))
    record("thread.nextFloor", int_of((detail or {}).get("nextFloor")) == start_floor + concurrency,
           (detail or {}).get("nextFloor"))

    mine = call("my.posts", "GET", "/forum/v1/my/posts", who=state["replier"], params={"size": 50})
    record("my.posts.total", int_of((mine or {}).get("total")) >= 1, (mine or {}).get("total"))
    mine_threads = call("my.threads", "GET", "/forum/v1/my/threads", who=state["author"],
                        params={"size": 50})
    record("my.threads.total", int_of((mine_threads or {}).get("total")) >= 1,
           (mine_threads or {}).get("total"))


# ------------------------------------------------------------------- 互动
def suite_interaction(state):
    section("E 互动：投花 / 藏帖 / 我的收藏")
    tid = state.get("thread")
    if not tid:
        skip("reaction.*", "主帖缺失")
        return
    liked = call("reaction.like", "PUT", f"/forum/v1/reaction/thread/{tid}", who=MEMBER,
                 params={"type": "LIKE"})
    record("reaction.like.count", int_of((liked or {}).get("likeCount")) == 1,
           (liked or {}).get("likeCount"))
    record("reaction.like.state", (liked or {}).get("liked") is True, (liked or {}).get("liked"))

    again = call("reaction.like.idempotent", "PUT", f"/forum/v1/reaction/thread/{tid}", who=MEMBER,
                 params={"type": "LIKE"})
    record("reaction.like.idempotent.count", int_of((again or {}).get("likeCount")) == 1,
           (again or {}).get("likeCount"))

    call("reaction.like.badType", "PUT", f"/forum/v1/reaction/thread/{tid}", who=MEMBER,
         expect=400, params={"type": "FLOWER"})

    collected = call("reaction.collect", "PUT", f"/forum/v1/reaction/thread/{tid}", who=MEMBER,
                     params={"type": "COLLECT"})
    record("reaction.collect.count", int_of((collected or {}).get("collectCount")) == 1,
           (collected or {}).get("collectCount"))

    mine = call("my.collections", "GET", "/forum/v1/my/collections", who=MEMBER, params={"size": 50})
    record("my.collections.contains", tid in
           [int_of(r.get("id")) for r in (mine or {}).get("records") or []],
           f"total={(mine or {}).get('total')}")

    cancelled = call("reaction.like.cancel", "DELETE", f"/forum/v1/reaction/thread/{tid}",
                     who=MEMBER, params={"type": "LIKE"})
    record("reaction.like.cancel.count", int_of((cancelled or {}).get("likeCount")) == 0,
           (cancelled or {}).get("likeCount"))
    again_like = call("reaction.like.afterCancel", "PUT", f"/forum/v1/reaction/thread/{tid}",
                      who=MEMBER, params={"type": "LIKE"})
    record("reaction.like.again", int_of((again_like or {}).get("likeCount")) == 1,
           (again_like or {}).get("likeCount"))


# --------------------------------------------------------------- 热榜 / 检索
def suite_hot_search(state):
    section("F 热榜：双档 / 刷新幂等 / 权限")
    hot24 = call("hot.24h", "GET", "/forum/v1/hot", who=MEMBER, params={"window": "24h", "limit": 10})
    record("hot.24h.list", isinstance(hot24, list), f"n={len(hot24 or [])}")
    hot7 = call("hot.7d", "GET", "/forum/v1/hot", who=MEMBER, params={"window": "7d", "limit": 10})
    record("hot.7d.list", isinstance(hot7, list), f"n={len(hot7 or [])}")

    call("hot.refresh", "POST", "/forum/v1/hot/refresh", who=ADMIN)
    first = call("hot.24h.afterRefresh", "GET", "/forum/v1/hot", who=MEMBER,
                 params={"window": "24h", "limit": 10})
    call("hot.refresh.again", "POST", "/forum/v1/hot/refresh", who=ADMIN)
    second = call("hot.24h.afterRefresh2", "GET", "/forum/v1/hot", who=MEMBER,
                  params={"window": "24h", "limit": 10})
    record("hot.refresh.stable", len(first or []) == len(second or []),
           f"{len(first or [])} -> {len(second or [])}")
    call("hot.refresh.forbidden", "POST", "/forum/v1/hot/refresh", who=MEMBER, expect=403)

    section("G 检索：ngram / 单字回退 / 重建索引 / 权限")
    status = call("search.status", "GET", "/forum/v1/search/status", who=MEMBER)
    record("search.status.engine", str((status or {}).get("engine", "")).startswith("mysql"),
           (status or {}).get("engine"))
    record("search.status.ngram", int_of((status or {}).get("ngramTokenSize")) == 2,
           (status or {}).get("ngramTokenSize"))

    hit = call("search.keyword", "GET", "/forum/v1/search", who=MEMBER,
               params={"keyword": "鲸议台", "size": 20})
    record("search.keyword.hit", int_of((hit or {}).get("total")) >= 1, (hit or {}).get("total"))

    single = call("search.singleChar", "GET", "/forum/v1/search", who=MEMBER,
                  params={"keyword": "鲸", "size": 20})
    record("search.singleChar.fallback", isinstance(single, dict), (single or {}).get("total"))

    call("search.reindex", "POST", "/forum/v1/search/reindex", who=ADMIN, params={"limit": 200})
    call("search.reindex.forbidden", "POST", "/forum/v1/search/reindex", who=MEMBER, expect=403)


# ------------------------------------------------------------------- 治理
def suite_moderation(state):
    section("H 治理：审核台 / 举报 / 处置 / 精华 / 禁言")
    tid = state.get("thread")
    if not tid:
        skip("moderation.*", "主帖缺失")
        return

    if state.get("thread_pending"):
        audit = call("audit.page", "GET", "/forum/v1/admin/audit/page", who=ADMIN,
                     params={"targetType": "THREAD", "size": 50})
        record("audit.page.contains", state["thread_pending"] in
               [int_of(r.get("targetId")) for r in (audit or {}).get("records") or []],
               f"total={(audit or {}).get('total')}")
        call("audit.reject.noremark", "POST", "/forum/v1/admin/audit/reject", who=ADMIN,
             expect=30020, body={"targetType": "THREAD", "targetId": state["thread_pending"]})
        call("audit.pass", "POST", "/forum/v1/admin/audit/pass", who=ADMIN, body={
            "targetType": "THREAD", "targetId": state["thread_pending"], "remark": "冒烟通过"})
        passed = call("audit.pass.visible", "GET", f"/forum/v1/thread/{state['thread_pending']}",
                      who=ADMIN)
        record("audit.pass.status", (passed or {}).get("status") == "PUBLISHED",
               (passed or {}).get("status"))

    # 先审版块：不开审核开关的新用户帖必须进待审
    if state.get("audit_board"):
        newbie = call_retry("audit.newUser.pending", "POST", "/forum/v1/thread",
                            who=fresh(MEMBER_PERMS, "newbie"), body={
            "boardId": state["audit_board"], "title": f"先审版块新帖 {RUN}",
            "content": "<p>先审版块正文</p>"})
        state["thread_audit_board"] = int_of((newbie or {}).get("id"))
        record("audit.boardMode.pending", (newbie or {}).get("status") == "PENDING",
               (newbie or {}).get("status"))

    report = call("report.submit", "POST", "/forum/v1/report", who=MEMBER2, body={
        "targetType": "THREAD", "targetId": tid, "reportType": "SPAM",
        "description": f"冒烟举报 {RUN}"})
    state["report"] = int_of(report)
    record("report.submit.id", state["report"] is not None, report)

    dup = call("report.duplicate", "POST", "/forum/v1/report", who=MEMBER2, body={
        "targetType": "THREAD", "targetId": tid, "reportType": "SPAM", "description": "重复举报"})
    record("report.duplicate.sameId", int_of(dup) == state["report"], f"{state['report']} -> {dup}")

    reports = call("report.page", "GET", "/forum/v1/admin/report/page", who=ADMIN,
                   params={"status": "PENDING", "size": 50})
    record("report.page.contains", state["report"] in
           [int_of(r.get("id")) for r in (reports or {}).get("records") or []],
           f"total={(reports or {}).get('total')}")

    if state["report"]:
        call("report.handle", "POST", f"/forum/v1/admin/report/{state['report']}/handle",
             who=ADMIN, body={"status": "RESOLVED", "remark": "冒烟结案", "action": "NONE"})
        call("report.handle.again", "POST", f"/forum/v1/admin/report/{state['report']}/handle",
             who=ADMIN, expect=30062, body={"status": "RESOLVED", "action": "NONE"})

    # 精华：授权 + 经验加成 200（作者视角）
    before = call("exp.beforeEssence", "GET", "/forum/v1/exp/me", who=state["author"])
    call("admin.essence", "POST", f"/forum/v1/admin/thread/{tid}/essence", who=ADMIN,
         body={"reason": "冒烟加精"})
    after = call("exp.afterEssence", "GET", "/forum/v1/exp/me", who=state["author"])
    delta = (int_of((after or {}).get("total")) or 0) - (int_of((before or {}).get("total")) or 0)
    record("admin.essence.expDelta", delta >= 200, f"delta={delta}")
    call("admin.unessence", "POST", f"/forum/v1/admin/thread/{tid}/unessence", who=ADMIN,
         body={"reason": "冒烟取消精华"})

    # 楼层处置
    if state.get("post2"):
        call("admin.remove.post", "POST", f"/forum/v1/admin/post/{state['post2']}/remove",
             who=ADMIN, body={"reason": "冒烟删层"})
        posts = call("post.page.afterRemove", "GET", f"/forum/v1/thread/{tid}/post/page",
                     who=ADMIN, params={"size": 50})
        visible_ids = [int_of(p.get("id")) for p in (posts or {}).get("records") or []]
        record("admin.remove.post.hidden", state["post2"] not in visible_ids,
               f"total={(posts or {}).get('total')}")

    # 禁言：被禁言者不能发帖/回帖
    ban_target = MEMBER
    call("ban.mute", "POST", f"/forum/v1/admin/user/{MEMBER_ID}/ban", who=ADMIN, body={
        "reason": f"冒烟禁言 {RUN}", "banType": "MUTE", "days": 1})
    ban_state = call("ban.active", "GET", f"/forum/v1/admin/user/{MEMBER_ID}/ban", who=ADMIN)
    record("ban.active.type", (ban_state or {}).get("banType") == "MUTE",
           (ban_state or {}).get("banType") if isinstance(ban_state, dict) else ban_state)
    call_retry("ban.muted.post", "POST", f"/forum/v1/thread/{tid}/post", who=ban_target, expect=30041,
         body={"content": "<p>禁言期间回帖</p>"})

    # 删帖走治理权限，作者申诉链路在申诉小节验证
    state["ban"] = True


# ------------------------------------------------------------------- 申诉
def suite_appeal(state):
    section("I 申诉：材料上传 / 判重 / 复核解禁 / 预览")
    if not state.get("ban"):
        skip("appeal.*", "禁言未生效")
        return

    png_id = upload_file("file.upload.image", MEMBER, f"appeal-{RUN}.png", PNG_BYTES, "image/png")
    txt_id = upload_file("file.upload.text", MEMBER, f"appeal-{RUN}.txt", b"evidence\n", "text/plain")
    state["file_png"] = int_of(png_id)
    state["file_txt"] = int_of(txt_id)
    if not state["file_png"]:
        skip("appeal.material", "文件上传失败")

    appeal = call("appeal.submit", "POST", "/forum/v1/appeal", who=MEMBER, body={
        "targetType": "BAN", "targetId": int(MEMBER_ID),
        "reason": f"冒烟申诉 {RUN}",
        "materialFileIds": [state["file_png"], state["file_txt"]] if state["file_png"] else [],
    })
    state["appeal"] = int_of((appeal or {}).get("id"))
    materials = (appeal or {}).get("materials") or []
    record("appeal.material.count", len(materials) == 2, len(materials))
    if materials:
        record("appeal.material.meta", bool(materials[0].get("fileName"))
               and bool(materials[0].get("contentType")),
               f"{materials[0].get('fileName')} / {materials[0].get('contentType')}")

    call("appeal.duplicate", "POST", "/forum/v1/appeal", who=MEMBER, expect=400, body={
        "targetType": "BAN", "targetId": int(MEMBER_ID), "reason": "重复申诉"})

    mine = call("my.appeals", "GET", "/forum/v1/my/appeals", who=MEMBER, params={"size": 20})
    record("my.appeals.contains", state["appeal"] in
           [int_of(r.get("id")) for r in (mine or {}).get("records") or []],
           f"total={(mine or {}).get('total')}")

    admin_page = call("appeal.page.pending", "GET", "/forum/v1/admin/appeal/page", who=ADMIN,
                      params={"status": "PENDING", "size": 50})
    record("appeal.page.contains", state["appeal"] in
           [int_of(r.get("id")) for r in (admin_page or {}).get("records") or []],
           f"total={(admin_page or {}).get('total')}")

    if state.get("file_png"):
        call("appeal.preview.self", "GET", f"/forum/v1/appeal/material/{state['file_png']}/preview",
             who=MEMBER, raw=True, expect=200)
        call("appeal.preview.stranger",
             "GET", f"/forum/v1/appeal/material/{state['file_png']}/preview",
             who=MEMBER2, expect=403)
        call("appeal.preview.admin",
             "GET", f"/forum/v1/appeal/material/{state['file_png']}/preview", who=ADMIN, raw=True,
             expect=200)
    if state.get("file_txt"):
        call("appeal.preview.nonImage",
             "GET", f"/forum/v1/appeal/material/{state['file_txt']}/preview", who=MEMBER, expect=400)

    if state["appeal"]:
        handled = call("appeal.handle.approve", "POST",
                       f"/forum/v1/admin/appeal/{state['appeal']}/handle", who=ADMIN,
                       body={"approved": True, "remark": "冒烟复核通过"})
        record("appeal.handle.status", (handled or {}).get("status") == "APPROVED",
               (handled or {}).get("status"))
        call("appeal.handle.again", "POST", f"/forum/v1/admin/appeal/{state['appeal']}/handle",
             who=ADMIN, expect=400, body={"approved": True})
        released = http("GET", f"/forum/v1/admin/user/{MEMBER_ID}/ban", who=ADMIN)[0]
        record("appeal.release.ban", released.get("code") == 0 and not released.get("data"),
               released.get("data"))
        call_retry("appeal.release.canPost", "POST", f"/forum/v1/thread/{state['thread']}/post",
             who=MEMBER, body={"content": "<p>解禁后回帖</p>"})


# -------------------------------------------------------- 个人中心 / 结契 / 对账
def suite_profile_follow(state):
    section("J 个人中心 / 经验 / 结契")
    profile = call("profile.me", "GET", "/forum/v1/profile/me", who=MEMBER)
    record("profile.me.level", int_of((profile or {}).get("level")) >= 1,
           (profile or {}).get("level"))
    record("profile.me.threadCount", int_of((profile or {}).get("threadCount")) is not None,
           (profile or {}).get("threadCount"))
    call("profile.other", "GET", f"/forum/v1/profile/{ADMIN_ID}", who=MEMBER)

    exp = call("exp.me", "GET", "/forum/v1/exp/me", who=MEMBER)
    record("exp.me.total", int_of((exp or {}).get("total")) is not None, (exp or {}).get("total"))

    tid = state.get("thread")
    if not tid:
        skip("follow.*", "主帖缺失")
        return

    author_id = state["author"]["X-User-Id"]
    followed = call("follow.user", "PUT", f"/forum/v1/follow/USER/{author_id}", who=MEMBER)
    record("follow.user.state", (followed or {}).get("following") is True,
           (followed or {}).get("following"))
    again = call("follow.user.idempotent", "PUT", f"/forum/v1/follow/USER/{author_id}", who=MEMBER)
    record("follow.user.idempotent", (again or {}).get("following") is True,
           (again or {}).get("following"))
    state_vo = call("follow.user.get", "GET", f"/forum/v1/follow/USER/{author_id}", who=MEMBER)
    record("follow.user.get.state", (state_vo or {}).get("following") is True,
           (state_vo or {}).get("following"))

    call("follow.self", "PUT", f"/forum/v1/follow/USER/{MEMBER_ID}", who=MEMBER, expect=400)
    call("follow.badType", "PUT", f"/forum/v1/follow/THREAD/{tid}", who=MEMBER, expect=400)

    if state.get("board"):
        call("follow.board", "PUT", f"/forum/v1/follow/BOARD/{state['board']}", who=MEMBER)

    follows = call("my.follows", "GET", "/forum/v1/my/follows", who=MEMBER, params={"size": 50})
    record("my.follows.total", int_of((follows or {}).get("total")) >= 2,
           (follows or {}).get("total"))

    feed = call("my.feed", "GET", "/forum/v1/my/feed", who=MEMBER, params={"size": 50})
    feed_ids = [int_of(r.get("id")) for r in (feed or {}).get("records") or []]
    record("my.feed.containsFollowed", tid in feed_ids, f"total={(feed or {}).get('total')}")

    if state.get("audit_board"):
        board_feed = call("my.feed.board", "GET", "/forum/v1/my/feed", who=MEMBER,
                          params={"boardId": state["audit_board"], "size": 20})
        record("my.feed.boardFilter", isinstance(board_feed, dict),
               f"total={(board_feed or {}).get('total')}")

    unfollowed = call("follow.user.unfollow", "DELETE", f"/forum/v1/follow/USER/{author_id}",
                      who=MEMBER)
    record("follow.user.unfollow.state", (unfollowed or {}).get("following") is False,
           (unfollowed or {}).get("following"))


def suite_reconcile_negative(state):
    section("K 对账 / 负向越权")
    dry = call("reconcile.dryRun", "POST", "/forum/v1/admin/count/reconcile", who=ADMIN,
               params={"fix": "false", "limit": 100})
    record("reconcile.dryRun.flag", (dry or {}).get("dryRun") is True, (dry or {}).get("dryRun"))
    fixed = call("reconcile.fix", "POST", "/forum/v1/admin/count/reconcile", who=ADMIN,
                 params={"fix": "true", "limit": 100})
    record("reconcile.fix.flag", (fixed or {}).get("dryRun") is False,
           (fixed or {}).get("dryRun"))
    call("reconcile.forbidden", "POST", "/forum/v1/admin/count/reconcile", who=MEMBER, expect=403)

    call("notify.flush", "POST", "/forum/v1/admin/notify/flush", who=ADMIN,
         params={"limit": 50})

    tid = state.get("thread")
    if tid:
        call("negative.crossTenant.detail", "GET", f"/forum/v1/thread/{tid}", who=TENANT2,
             expect=30001)
        call("negative.missingPerm.auditPage", "GET", "/forum/v1/admin/audit/page", who=MEMBER,
             expect=403)
        call("negative.notOwner.delete", "DELETE", f"/forum/v1/thread/{tid}", who=MEMBER2,
             expect=30060)
        call("negative.notOwner.edit", "PUT", f"/forum/v1/thread/{tid}", who=MEMBER2,
             expect=30060, body={"title": "越权改标题"})

    # 作者删帖：软删后可重建同名帖，原帖对他人不可见
    deleter = fresh()
    own = call_retry("thread.create.forDelete", "POST", "/forum/v1/thread", who=deleter,
               body={"boardId": state.get("board"), "title": f"待删帖 {RUN}",
                     "content": "<p>待删</p>"})
    own_id = int_of((own or {}).get("id"))
    if own_id:
        call("thread.delete.self", "DELETE", f"/forum/v1/thread/{own_id}", who=deleter)
        call("thread.delete.visible", "GET", f"/forum/v1/thread/{own_id}", who=MEMBER,
             expect=30001)
        # 同名重建换个人发：同一账号 60 秒内只能发一帖，复用 deleter 会撞 30042
        rebuilt = call_retry("thread.rebuild.sameTitle", "POST", "/forum/v1/thread", who=fresh(),
                       body={"boardId": state.get("board"), "title": f"待删帖 {RUN}",
                             "content": "<p>重建</p>"})
        record("thread.rebuild.ok", int_of((rebuilt or {}).get("id")) is not None,
               (rebuilt or {}).get("threadNo"))


def finish():
    total = len(RESULTS)
    skipped = [r for r in RESULTS if r.get("skipped")]
    failed = [r for r in RESULTS if not r["ok"]]
    passed = total - len(failed) - len(skipped)
    summary = {
        "mode": MODE,
        "base": BASE,
        "run": RUN,
        "total": total,
        "passed": passed,
        "failed": len(failed),
        "skipped": len(skipped),
        "finishedAt": datetime.now().isoformat(timespec="seconds"),
        "cases": RESULTS,
    }
    path = RATE_REPORT if MODE == "rate" else REPORT
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(summary, fp, ensure_ascii=False, indent=2)
    print(f"\n=== 汇总 {passed}/{total - len(skipped)} 通过，失败 {len(failed)}，跳过 {len(skipped)} ===")
    if failed:
        print("失败清单：")
        for item in failed:
            print(f"  - {item['name']}: {item['detail']}")
    print(f"报告：{os.path.abspath(path)}")
    return 0 if not failed else 1


# ------------------------------------------------------------------ 限流专项
def rate_suite(state):
    """限流专项：需服务端按默认配置起（发帖 60s / 回帖 10s 间隔不回退）。

    用两个「本次运行专属的新用户」隔离历史数据：限流是按人统计的，
    复用压测账号会被上一轮的帖子挡住，看到的 30042 就不是本轮触发的。
    """
    section("R 限流专项（默认配置）")
    # 用户 ID 带 RUN 后缀：同一账号 60 秒内只能发一帖，复用固定 ID 会让第二次运行
    # 的第一帖就被上一轮挡住（看到的 30042 不是本轮触发的）
    poster = identity(f"91{RUN}", f"smoke_rate_poster_{RUN}", MEMBER_PERMS, roles="member")
    replier = identity(f"92{RUN}", f"smoke_rate_replier_{RUN}", MEMBER_PERMS, roles="member")

    board = call("rate.board", "POST", "/forum/v1/board", who=ADMIN, body={
        "code": f"rate-{RUN}", "name": f"限流版块{RUN}", "auditMode": 0, "postPolicy": "OPEN",
        "visibility": "ALL"})
    board_id = int_of((board or {}).get("id"))
    if not board_id:
        skip("rate.*", "版块创建失败")
        return

    first = call("rate.thread.first", "POST", "/forum/v1/thread", who=poster, body={
        "boardId": board_id, "title": f"限流首帖 {RUN}", "content": "<p>首帖</p>"})
    call("rate.thread.second", "POST", "/forum/v1/thread", who=poster, expect=30042, body={
        "boardId": board_id, "title": f"限流次帖 {RUN}", "content": "<p>次帖</p>"})
    tid = int_of((first or {}).get("id"))
    # 新用户首帖必审（PENDING），待审帖对他人不可见，回帖会 30001：
    # 先由管理员审过，再验证回帖限流，避免把审核口径误读成限流缺陷。
    if tid and (first or {}).get("status") == "PENDING":
        call("rate.audit.pass", "POST", "/forum/v1/admin/audit/pass", who=ADMIN, body={
            "targetType": "THREAD", "targetId": tid, "remark": "限流专项放行"})
    if tid:
        call("rate.reply.first", "POST", f"/forum/v1/thread/{tid}/post", who=replier,
             body={"content": "<p>首层</p>"})
        call("rate.reply.second", "POST", f"/forum/v1/thread/{tid}/post", who=replier,
             expect=30042, body={"content": "<p>次层</p>"})


if __name__ == "__main__":
    sys.exit(main())
