#!/usr/bin/env python3
"""归档 CSV 完整性校验（等保 2.0 8.1.4.3 b) c)：审计记录完整性、归档不可篡改）。

用法：
    # 只校验文件本身（比对清单里记录的 sha256）
    python scripts/verify/archive-hash-verify.py auditLog-archive-xxx.csv

    # 显式指定清单 / 期望摘要
    python scripts/verify/archive-hash-verify.py data.csv --manifest data.csv.sha256
    python scripts/verify/archive-hash-verify.py data.csv --sha256 <64位hex>

    # 校验链值（上一批 chain 从 audit_archive_watermark 或上层清单取）
    python scripts/verify/archive-hash-verify.py data.csv --prev-chain <上一批 chain_sha256>

    # 校验 HMAC 签名（密钥为服务端 whale.audit.export.sign-key）
    python scripts/verify/archive-hash-verify.py data.csv --hmac-key "$WHALE_AUDIT_SIGN_KEY"

    # 生产审计：要求必须有签名（未签名直接不通过）
    python scripts/verify/archive-hash-verify.py data.csv --hmac-key "$KEY" --require-signature

行为：
    · 清单默认按「同名 + .csv.sha256」自动发现（与 AuditArchiveDigest#manifestFileName 一致）；
    · 清单里 hmac= 之前的**全部字节**参与签名，任何字段被改都会验签失败；
    · 链值 = sha256(prev_chain + batch_sha256)，首批 prev_chain 传空串。

退出码：
    0 = 全部通过
    1 = 校验不通过（哈希/链值/签名不符）
    2 = 用法或 IO 错误（文件不存在、清单格式不认识）

只读工具：不修改任何文件，可对归档副本反复执行。
"""

import argparse
import hashlib
import hmac
import os
import sys

MANIFEST_SUFFIX = ".sha256"
HMAC_PREFIX = "hmac="
SUPPORTED_FORMATS = {"whale-audit-archive/1"}


def fail(message, code=1):
    print(f"FAIL {message}")
    return code


def parse_manifest(raw):
    """把清单字节切成 (字段字典, 被签名的原始字节)。

    签名覆盖 hmac= 行之前的全部字节，所以这里必须按字节切分，不能先解码再重编码
    （重编码会改变换行或尾随空白，导致合法清单验签失败）。
    """
    marker = HMAC_PREFIX.encode()
    index = raw.rfind(marker)
    if index < 0:
        raise ValueError("清单缺少 hmac 行")
    signed = raw[:index]
    tail = raw[index + len(marker):].decode("utf-8", "replace").strip()
    fields = {}
    for line in signed.decode("utf-8", "replace").splitlines():
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        fields[key.strip()] = value.strip()
    fields["hmac"] = tail
    return fields, signed


def main():
    parser = argparse.ArgumentParser(add_help=True, description="归档 CSV 完整性校验")
    parser.add_argument("csv", help="待校验的归档 CSV 路径")
    parser.add_argument("--manifest", help="清单路径，默认自动发现 <csv>.sha256")
    parser.add_argument("--sha256", help="期望的 SHA-256（与清单同时给时两者都必须通过）")
    parser.add_argument("--prev-chain", help="上一批 chain_sha256；给出时校验链值连续性")
    parser.add_argument("--hmac-key", help="清单 HMAC 签名密钥；给出时强制验签")
    parser.add_argument("--require-signature", action="store_true",
                        help="要求清单必须带 HMAC 签名且已提供密钥（生产审计用）：未签名即不通过")
    args = parser.parse_args()

    if not os.path.isfile(args.csv):
        return fail(f"文件不存在: {args.csv}", 2)

    with open(args.csv, "rb") as handle:
        content = handle.read()
    actual_sha = hashlib.sha256(content).hexdigest()
    print(f"INFO csv={args.csv} bytes={len(content)} sha256={actual_sha}")

    manifest_path = args.manifest
    if manifest_path is None and os.path.isfile(args.csv + MANIFEST_SUFFIX):
        manifest_path = args.csv + MANIFEST_SUFFIX

    checked = 0
    if args.sha256:
        checked += 1
        if actual_sha.lower() != args.sha256.strip().lower():
            return fail(f"CSV 内容哈希与期望不符: actual={actual_sha} expected={args.sha256}")
        print("PASS csv 内容哈希与期望一致")

    if manifest_path:
        if not os.path.isfile(manifest_path):
            return fail(f"清单不存在: {manifest_path}", 2)
        with open(manifest_path, "rb") as handle:
            fields, signed = parse_manifest(handle.read())
        if fields.get("format") not in SUPPORTED_FORMATS:
            return fail(f"清单格式不支持: {fields.get('format')}", 2)

        checked += 1
        if fields.get("sha256", "").lower() != actual_sha:
            return fail(f"CSV 内容哈希与清单不符: actual={actual_sha} manifest={fields.get('sha256')}")
        print(f"PASS 清单哈希一致（objectKey={fields.get('objectKey', '')}, rows={fields.get('rows', '')}）")

        if args.prev_chain is not None:
            checked += 1
            expected_chain = hashlib.sha256((args.prev_chain.strip() + actual_sha).encode()).hexdigest()
            if fields.get("chainSha256", "").lower() != expected_chain:
                return fail(f"链值不连续: manifest={fields.get('chainSha256')} recomputed={expected_chain}")
            print(f"PASS 链值连续 chainSha256={expected_chain}")

        signed_present = bool(fields.get("hmac"))
        if args.require_signature and (not signed_present or args.hmac_key is None):
            return fail("清单无 HMAC 签名或未提供 --hmac-key，按 --require-signature 判定不通过")

        if args.hmac_key is not None:
            checked += 1
            if not signed_present:
                return fail("清单未签名（hmac 为空），但已提供密钥，按不通过处理")
            expected_hmac = hmac.new(args.hmac_key.encode(), signed, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(fields["hmac"].lower(), expected_hmac):
                return fail(f"清单签名不匹配: manifest={fields['hmac']} recomputed={expected_hmac}")
            print("PASS 清单 HMAC 签名有效")
        elif not signed_present:
            # 不能静默通过：SHA-256 与链值只能证明「没被误改」，
            # 持有库写权限的人可以连哈希一起重算，签名才是不可伪造性的凭证。
            print("WARN 本批清单未签名（hmac 为空）——只校验了内容哈希与链值：")
            print("WARN 能证明归档物未被误改，不能证明未被有库写权限者连同哈希一起改写；")
            print("WARN 生产审计请用 --hmac-key 提供密钥，并加 --require-signature 强制要求签名。")
    elif not args.sha256:
        return fail("未提供清单也未提供 --sha256，没有可校验的期望值", 2)

    print(f"OK 校验通过（{checked} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
