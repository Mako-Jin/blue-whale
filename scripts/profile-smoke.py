"""身份档案读写回归：建档 → 读取掩码 → 二次保存保留敏感原值 → 删档 → 可重建。

验证三件事：
1. PUT 建档（学历 + 家庭成员 + 证件 + 联系方式）；
2. GET view 能读回，且敏感字段是掩码；
3. 再次 PUT 时子项带 id、敏感字段留空 → 原值保留（本次后端改动语义）；
4. 密保问题不传（null）→ 不清空既有密保。
"""
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("QA_BASE", "http://127.0.0.1:8080")


def load_cred():
    """按优先级取测试账号：qa_cred 文件 → QA_USERNAME/QA_PASSWORD → .env 的 QA_ADMIN_*。"""
    cred_file = os.environ.get("QA_CRED_FILE")
    if cred_file and os.path.exists(cred_file):
        with open(cred_file, "r", encoding="utf-8") as fh:
            lines = [line.strip() for line in fh.read().splitlines() if line.strip()]
        if len(lines) >= 2:
            return lines[0], lines[1]
    user = os.environ.get("QA_USERNAME")
    password = os.environ.get("QA_PASSWORD")
    env_file = os.environ.get(
        "QA_ENV_FILE",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "deploy", "backend", ".env"),
    )
    if not password and os.path.exists(env_file):
        with open(env_file, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, val = line.partition("=")
                if key.strip() == "QA_ADMIN_USER" and not user:
                    user = val.strip()
                elif key.strip() == "QA_ADMIN_PASSWORD":
                    password = val.strip()
    return user or "qa_admin", password or ""


def call(method, path, token=None, body=None):
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


def main():
    user, password = load_cred()
    if not password:
        print("SKIP: 未取到测试账号凭据")
        return 0
    status, payload = call("POST", "/auth/v1/login", body={"username": user, "password": password})
    token = (payload.get("data") or {}).get("accessToken")
    if not token:
        print(f"FAIL login http={status}")
        return 1
    status, me = call("GET", "/auth/v1/me", token=token)
    uid = (me.get("data") or {}).get("userId")
    print(f"OK   login uid={uid}")

    base = f"/user-profile/v1/{uid}"
    first = {
        "gender": "MALE",
        "birthDate": "1990-05-06",
        "nation": "汉族",
        "maritalStatus": "MARRIED",
        "contacts": [{"contactType": "MOBILE", "contactValue": "13800001111", "primary": True, "sortNo": 0}],
        "credentials": [{"credentialType": "ID_CARD", "credentialNo": "110101199005061234", "holderName": "验证用户", "primary": True}],
        "educations": [{"school": "蓝鲸大学", "major": "软件工程", "degree": "BACHELOR", "sortNo": 0}],
        "emergencyContacts": [{"contactName": "张三", "relationship": "SPOUSE", "phone": "13900002222", "sortNo": 0}],
        "secretQuestions": [{"question": "母亲姓名", "answer": "李四"}],
    }
    status, payload = call("PUT", base, token=token, body=first)
    if status != 200 or payload.get("code") != 0:
        print(f"FAIL 建档 http={status} body={payload}")
        return 1
    print("OK   建档（含学历/家庭成员/证件/联系方式/密保）")

    status, payload = call("GET", f"{base}/view", token=token)
    data = payload.get("data") or {}
    if status != 200 or not data:
        print(f"FAIL 读取档案 http={status} body={payload}")
        return 1
    edu = data.get("educations") or []
    fam = data.get("emergencyContacts") or []
    cre = data.get("credentials") or []
    print(f"OK   读取：学历={len(edu)} 家庭成员={len(fam)} 证件={len(cre)} 密保={len(data.get('secretQuestions') or [])}")
    print(f"     掩码示例：证件号={cre[0].get('credentialNoMasked') if cre else None} 家庭电话={fam[0].get('phoneMasked') if fam else None}")
    contact_masked = (data.get("contacts") or [{}])[0].get("contactValue")
    print(f"     联系方式掩码={contact_masked}")

    # 第二遍：子项带 id，敏感字段留空 → 必须保留原值
    second = dict(first)
    second["remarks"] = "二次保存，敏感字段留空"
    second["contacts"] = [{"id": (data.get("contacts") or [{}])[0].get("id"),
                           "contactType": "MOBILE", "contactValue": None, "primary": True, "sortNo": 0}]
    second["credentials"] = [{"id": cre[0].get("id") if cre else None,
                              "credentialType": "ID_CARD", "credentialNo": None,
                              "holderName": "验证用户", "primary": True}]
    second["emergencyContacts"] = [{"id": fam[0].get("id") if fam else None,
                                    "contactName": "张三", "relationship": "SPOUSE", "phone": None, "sortNo": 0}]
    second.pop("secretQuestions", None)  # 未提供 → 不清空既有密保

    status, payload = call("PUT", base, token=token, body=second)
    if status != 200 or payload.get("code") != 0:
        print(f"FAIL 二次保存 http={status} body={payload}")
        return 1
    status, payload = call("GET", f"{base}/view", token=token)
    data2 = payload.get("data") or {}
    edu2 = data2.get("educations") or []
    cre2 = data2.get("credentials") or []
    fam2 = data2.get("emergencyContacts") or []
    secrets2 = data2.get("secretQuestions") or []

    ok = True
    if (data2.get("contacts") or [{}])[0].get("contactValue") != contact_masked:
        print("FAIL 联系方式明文被清空/覆盖（掩码变化）")
        ok = False
    if cre2 and cre2[0].get("credentialNoMasked") != (cre[0].get("credentialNoMasked") if cre else None):
        print("FAIL 证件号被清空/覆盖（掩码变化）")
        ok = False
    if fam2 and fam2[0].get("phoneMasked") != (fam[0].get("phoneMasked") if fam else None):
        print("FAIL 家庭电话被清空/覆盖（掩码变化）")
        ok = False
    if len(edu2) != len(edu):
        print(f"FAIL 学历行数变化 {len(edu)} -> {len(edu2)}（未传 educations 应保留）")
        ok = False
    if len(secrets2) != 1:
        print(f"FAIL 密保被清空（应保留 1 条，实际 {len(secrets2)}）")
        ok = False
    if data2.get("remarks") != "二次保存，敏感字段留空":
        print("FAIL 普通字段未更新")
        ok = False
    print("OK   二次保存：敏感字段留空保留原值 / 密保未传不清空" if ok else "二次保存语义异常")

    # 清理：删档案，保持测试号干净
    status, _ = call("DELETE", base, token=token)
    print(f"OK   清理档案 http={status}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
