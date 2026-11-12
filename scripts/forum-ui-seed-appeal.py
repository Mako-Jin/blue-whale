"""给论坛申诉材料的界面实机（whale-frontend/e2e/tools/forum-material-ui-check.mjs）造数据。

产出：一个被禁言的临时用户 + 一条 PENDING 的 BAN 申诉 + 1 张 png + 1 个 txt 材料；
打印 JSON（author_id / appeal_id / materials），把 author_id 传给界面检查脚本即可。


复用 scripts/forum-smoke.py 的注入式身份与上传助手（不经网关，避免被权限头干扰），
产出：一个被禁言用户 + 一条 PENDING 的 BAN 申诉 + 1 张 png + 1 个 txt 材料。
"""

import importlib.util
import json
import os
import struct
import sys
import zlib


def solid_png(width=96, height=96, rgb=(64, 128, 255)):
    """纯色 PNG：1x1 透明图在复核弹窗里渲染出来是一块空白，取证截图没有说服力。"""
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

spec = importlib.util.spec_from_file_location("fsmoke", os.path.join(ROOT, "scripts", "forum-smoke.py"))
fs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fs)

author_id = f"97{fs.RUN}"
author = fs.identity(author_id, f"smoke_ui_{fs.RUN}", fs.MEMBER_PERMS, roles="member")

# 1) 禁言（申诉对象只能是已处置内容或当前禁言）
fs.call("ui.ban", "POST", f"/forum/admin/user/{author_id}/ban", who=fs.ADMIN,
        body={"reason": "UI 实机造数", "banType": "MUTE", "days": 1})

# 2) 材料：png（应渲缩略图）+ txt（应降级为下载链接）
png_id = fs.upload_file("ui.upload.png", author, f"ui-{fs.RUN}.png", solid_png(), "image/png")
txt_id = fs.upload_file("ui.upload.txt", author, f"ui-{fs.RUN}.txt", b"evidence\n", "text/plain")

appeal = fs.call("ui.appeal", "POST", "/forum/appeal", who=author, body={
    "targetType": "BAN",
    "targetId": int(author_id),
    "reason": f"UI 实机申诉 {fs.RUN}",
    "materialFileIds": [int(png_id), int(txt_id)],
})

summary = {
    "run": fs.RUN,
    "author_id": author_id,
    "appeal_id": (appeal or {}).get("id"),
    "status": (appeal or {}).get("status"),
    "materials": [(m.get("fileId"), m.get("contentType")) for m in (appeal or {}).get("materials") or []],
    "failed": [r for r in fs.RESULTS if not r.get("ok")],
}
out = os.path.join(ROOT, ".workbuddy", "tmp", "forum-ui-seed-latest.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as fp:
    json.dump(summary, fp, ensure_ascii=False, indent=2)
print(json.dumps(summary, ensure_ascii=False, indent=2))
print(f"已写入 {out}")
