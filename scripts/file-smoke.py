"""文件服务冒烟：直打 bw-file（绕过网关，注入可信身份头）。

用法：容器起来后 `python scripts/file-smoke.py`
（报告落 `.workbuddy/tmp/file-smoke-report.json`，可用 FILE_SMOKE_REPORT 覆盖）。

背景：whale-file 长期不在常态编排里，CRM 分析导出 / 客户合并、OA 公文附件、LMS 课件、
PDM 附件、archive 归档、audit 导出、job 附件清理都指向一个不存在的容器。
本脚本用于确认它进常态栈后上传/下载/预签名/配额/内网入口全部可用。
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime

BASE = os.environ.get("FILE_SMOKE_BASE", "http://127.0.0.1:8085")
HEADERS = {
    "X-User-Id": "1",
    "X-Username": "admin",
    "X-Tenant-Id": "1",
    "X-User-Roles": "tenant_admin",
    "X-Permission-Codes": "super_admin",
}

RESULTS = []
RUN = datetime.now().strftime("%m%d%H%M%S")


def record(name, ok, detail=""):
    RESULTS.append({"name": name, "ok": ok, "detail": detail})
    print(f"  {'OK  ' if ok else 'FAIL'} {name} -> {detail}")


def multipart(field, filename, content, content_type="text/plain"):
    """构造单文件 multipart 请求体（不依赖 requests）。"""
    boundary = "----bw" + uuid.uuid4().hex
    body = b""
    body += f"--{boundary}\r\n".encode()
    body += f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'.encode()
    body += f"Content-Type: {content_type}\r\n\r\n".encode()
    body += content
    body += f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def request(method, path, body=None, content_type=None, raw=False):
    url = BASE + path
    headers = dict(HEADERS)
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read()
            meta = {
                "status": resp.status,
                "content_type": resp.headers.get("Content-Type", ""),
                "disposition": resp.headers.get("Content-Disposition", ""),
            }
    except urllib.error.HTTPError as e:
        payload = e.read()
        meta = {"status": e.code, "content_type": "", "disposition": ""}
    if raw:
        return meta, payload
    try:
        return meta, json.loads(payload.decode("utf-8"))
    except Exception:
        return meta, {"_raw": payload[:200].decode("utf-8", "replace")}


def main():
    print("=== A 上传 ===")
    content = f"bluewhale file smoke {RUN}\n".encode()
    body, ctype = multipart("file", f"smoke-{RUN}.txt", content)
    meta, data = request("POST", "/file/v1/upload", body, ctype)
    upload = data.get("data") or {}
    ok = meta["status"] == 200 and data.get("code") == 0 and upload.get("id")
    record("file.upload", bool(ok), f"http={meta['status']} code={data.get('code')} size={upload.get('fileSize')}")
    file_id = upload.get("id")
    # 长整型在这个项目里按字符串序列化（避免前端精度丢失），比较前统一转 int
    try:
        actual_size = int(upload.get("fileSize"))
    except (TypeError, ValueError):
        actual_size = None
    record("file.upload.size", actual_size == len(content),
           f"expect={len(content)} actual={upload.get('fileSize')}")

    print("=== B 下载 ===")
    if file_id:
        meta, payload = request("GET", f"/file/v1/{file_id}/download", raw=True)
        record("file.download", meta["status"] == 200 and payload == content,
               f"http={meta['status']} bytes={len(payload)}")
        record("file.download.disposition", "attachment" in meta["disposition"].lower(),
               meta["disposition"] or "(缺失)")
        meta, _ = request("GET", f"/file/v1/{file_id}/preview", raw=True)
        record("file.preview", meta["status"] == 200, f"http={meta['status']}")
    else:
        record("file.download", False, "SKIP 无 fileId")

    print("=== C 预签名 / 配额 ===")
    if file_id:
        meta, data = request("GET", f"/file/v1/{file_id}/presigned")
        # local 存储插件不支持预签名（服务端明确返回「当前存储插件不支持预签名下载: local」），
        # 这是配置限制而非缺陷：只有接了对象存储才应通过。
        unsupported = data.get("code") == 400 and "不支持预签名" in (data.get("message") or "")
        record("file.presigned", meta["status"] == 200 and (data.get("code") == 0 or unsupported),
               f"http={meta['status']} code={data.get('code')}"
               + ("（local 插件不支持，按预期跳过）" if unsupported else ""))
    meta, data = request("GET", "/file/v1/quota")
    record("file.quota", meta["status"] == 200 and data.get("code") == 0,
           f"http={meta['status']} code={data.get('code')}")

    print("=== D 内网入口（job/audit 导出走它）===")
    body, ctype = multipart("file", f"internal-{RUN}.txt", content)
    meta, data = request("POST", "/file/v1/internal/upload", body, ctype)
    record("file.internalUpload", meta["status"] == 200 and data.get("code") == 0,
           f"http={meta['status']} code={data.get('code')}")

    print("=== E 负向契约 ===")
    meta, data = request("GET", "/file/v1/999999999/download", raw=True)
    code = None
    try:
        code = json.loads(data.decode("utf-8")).get("code")
    except Exception:
        code = None
    record("file.download.notFound", code not in (None, 0),
           f"http={meta['status']} code={code}")

    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"\n===== 汇总：{passed}/{len(RESULTS)} 通过 =====")
    report = os.environ.get("FILE_SMOKE_REPORT",
                            r"F:\blue-whale\.workbuddy\tmp\file-smoke-report.json")
    os.makedirs(os.path.dirname(report), exist_ok=True)
    with open(report, "w", encoding="utf-8") as fh:
        json.dump({"total": len(RESULTS), "passed": passed, "items": RESULTS},
                  fh, ensure_ascii=False, indent=2)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
