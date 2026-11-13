#!/usr/bin/env python3
"""内网端点边界与 CI 工作目录静态门禁。

背景：内网端点（``/<service>/internal/**``）的访问控制靠三层叠加——

1. 网关 ``InternalApiDenyGlobalFilter``：``/*/internal/**``（单段星号，不跨段）外部 403；
2. 服务侧 ``InternalTokenFilter``：``/**/internal/**``（跨段）校验 ``X-Internal-Token``；
3. auth 的 Spring Security 白名单 ``permitAll``：内网端点若不在此列，会在进入令牌校验前
   就被判「未认证」直接 401——接口写了却永远打不通，且调用方通常把 401 折算成空数据，
   静默失败（2026-11-10 ``/dept/internal/**`` 漏配即此形态，存在数月未被发现）。

三层各管一段，任何一层漏配都只在真正联调时暴露，所以用脚本把三层的口径对齐。
本脚本同时检查 CI 里 ``working-directory`` 指向的目录是否仍然存在——目录重命名
（``backend/`` → ``whale-backend/``）后残留的旧路径会让流水线在 checkout 就失败。

用法：
    python scripts/check-internal-boundary.py            # 全量
    python scripts/check-internal-boundary.py --verbose  # 逐条打印 OK

退出码：0 通过（可能有 WARN）；1 存在 FAIL。
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------- 常量

#: 服务侧令牌过滤器的默认受保护路径（与 InternalTokenProperties 保持一致）
DEFAULT_TOKEN_PATTERNS = ["/**/internal/**", "/audit/log/ingest/**"]

#: 网关默认收口模式（与 InternalApiDenyProperties 保持一致）
DEFAULT_GATEWAY_PATTERNS = ["/*/internal/**", "/*/*/internal/**"]

#: 控制器文件扫描的根目录（相对仓库根）
BACKEND_DIR = "whale-backend"

#: 工作流目录（CI 工作目录检查）
WORKFLOW_DIRS = [".github/workflows", ".workflow"]

MAPPING_RE = re.compile(r"@(Request|Get|Post|Put|Delete|Patch)Mapping\b\s*(\([^)]*\))?")


@dataclass
class Finding:
    """一条核对结果。"""

    level: str
    message: str


@dataclass
class Endpoint:
    """一个内网端点（类级路径 + 方法级路径拼装后的结果）。"""

    path: str
    file: Path
    module: Path
    service: str
    permissions: list[str] = field(default_factory=list)


# ---------------------------------------------------------------- 通用工具


def strip_comments(source: str) -> str:
    """去掉 Java 源码里的注释，保留字符串字面量内容。

    不做这一步会踩坑：``MailInternalController`` 的类注释里写着「刻意不加
    ``@RequirePermission``」，直接 grep 会把它当成真的用了权限注解。

    :param source: 原始源码
    :return: 注释被替换为等长空白的源码（保持行列位置）
    """
    result: list[str] = []
    i = 0
    length = len(source)
    while i < length:
        char = source[i]
        if char == '"' or char == "'":
            quote = char
            result.append(char)
            i += 1
            while i < length:
                result.append(source[i])
                if source[i] == "\\":
                    if i + 1 < length:
                        result.append(source[i + 1])
                        i += 2
                        continue
                elif source[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "/":
            while i < length and source[i] != "\n":
                result.append(" ")
                i += 1
            continue
        if char == "/" and i + 1 < length and source[i + 1] == "*":
            result.append("  ")
            i += 2
            while i + 1 < length and not (source[i] == "*" and source[i + 1] == "/"):
                result.append("\n" if source[i] == "\n" else " ")
                i += 1
            result.append("  ")
            i += 2
            continue
        result.append(char)
        i += 1
    return "".join(result)


def annotation_paths(args: str | None) -> list[str]:
    """从注解参数里取出路径字面量（``value = "..."``、``path = "..."`` 或裸字符串）。

    只认 ``value`` / ``path`` 两个属性：``@RequestMapping(value = "/x", produces =
    "application/json")`` 里的 ``produces`` 不是路径，不能当成端点（否则门禁会凭空
    多出 ``/application/json`` 这类假端点）。

    :param args: 注解括号内的原文，``None`` 表示无参数（映射到类级路径）
    :return: 路径列表；无参数时返回 ``[""]``
    """
    if not args:
        return [""]
    body = args.strip()[1:-1] if args.strip().startswith("(") else args
    named = re.findall(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', body)
    if named:
        paths: list[str] = []
        for item in named:
            paths.extend(re.findall(r'"([^"]*)"', item))
        return paths or [""]
    if re.search(r"\w+\s*=", body):
        # 只有 produces/consumes/method 等属性，没有路径 → 交给类级路径
        return [""]
    literals = re.findall(r'"((?:[^"\\]|\\.)*)"', body)
    return literals or [""]


def join_path(*parts: str) -> str:
    """拼接并规整路径（去掉重复斜杠、去掉尾部斜杠）。

    :param parts: 路径片段
    :return: 规整后的路径
    """
    joined = "/".join(part.strip("/") for part in parts if part and part.strip("/"))
    return "/" + joined if joined else "/"


def normalize_version(path: str) -> str:
    """把契约里的版本占位符 ``v{version}`` 归一为具体版本 ``v1``，供网关/白名单字面量匹配。

    :param path: 含版本占位符的路径
    :return: 归一后的路径
    """
    return re.sub(r"v\{version\}", "v1", path)


def ant_match(pattern: str, path: str) -> bool:
    """Ant 风格路径匹配（``*`` 匹配单段、``**`` 匹配任意多段）。

    :param pattern: Ant 模式
    :param path: 待匹配路径
    :return: 匹配返回 ``True``
    """
    pattern_parts = [part for part in pattern.strip("/").split("/") if part]
    path_parts = [part for part in path.strip("/").split("/") if part]

    def walk(pi: int, xi: int) -> bool:
        if pi == len(pattern_parts):
            return xi == len(path_parts)
        if pattern_parts[pi] == "**":
            if pi == len(pattern_parts) - 1:
                return True
            return any(walk(pi + 1, next_xi) for next_xi in range(xi, len(path_parts) + 1))
        if xi == len(path_parts):
            return False
        if pattern_parts[pi] == "*":
            return walk(pi + 1, xi + 1)
        return pattern_parts[pi] == path_parts[xi] and walk(pi + 1, xi + 1)

    return walk(0, 0)


def matches_any(patterns: list[str], path: str) -> str | None:
    """返回首个命中的模式。

    :param patterns: 模式列表
    :param path: 待匹配路径
    :return: 命中的模式；都不命中返回 ``None``
    """
    for pattern in patterns:
        if pattern and ant_match(pattern, path):
            return pattern
    return None


def module_root(java_file: Path, repo_root: Path) -> Path:
    """向上找到最近的含 ``pom.xml`` 的目录（Maven 模块根）。

    :param java_file: Java 源文件
    :param repo_root: 仓库根
    :return: 模块根目录
    """
    current = java_file.parent
    while current != repo_root and current.parent != current:
        if (current / "pom.xml").is_file():
            return current
        current = current.parent
    return repo_root


def read_yaml_list(yml: Path, key: str, child: str) -> list[str] | None:
    """从 application.yml 里读两层的字符串列表（``key:`` → ``child:`` → ``- value``）。

    只处理形如::

        bluewhale:
          internal-token:
            path-patterns:
              - /**/internal/**

    的固定结构，避免为一行配置引入 PyYAML 依赖。

    :param yml: 配置文件
    :param key: 二级键（如 ``internal-token``）
    :param child: 三级键（如 ``path-patterns``）
    :return: 列表；未声明返回 ``None``
    """
    if not yml.is_file():
        return None
    lines = yml.read_text(encoding="utf-8").splitlines()
    in_key = False
    key_indent = 0
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        if stripped == key + ":":
            in_key = True
            key_indent = indent
            continue
        if in_key and indent <= key_indent:
            in_key = False
        if not in_key:
            continue
        if stripped == child + ":":
            values: list[str] = []
            for follow in lines[index + 1:]:
                follow_stripped = follow.strip()
                follow_indent = len(follow) - len(follow.lstrip())
                if not follow_stripped or follow_stripped.startswith("#"):
                    continue
                if follow_indent <= indent or not follow_stripped.startswith("- "):
                    break
                values.append(follow_stripped[2:].strip().strip('"').strip("'"))
            return values
    return None


# ---------------------------------------------------------------- 端点扫描


def collect_endpoints(repo_root: Path) -> tuple[list[Endpoint], list[Finding]]:
    """扫描全部控制器，提取内网端点。

    :param repo_root: 仓库根
    :return: 端点列表与扫描期发现的问题
    """
    endpoints: list[Endpoint] = []
    findings: list[Finding] = []
    backend = repo_root / BACKEND_DIR
    for java_file in sorted(backend.rglob("*.java")):
        parts = java_file.parts
        if "test" in parts or "target" in parts:
            continue
        raw = java_file.read_text(encoding="utf-8", errors="replace")
        if "@RequestMapping" not in raw or "internal" not in raw:
            continue
        source = strip_comments(raw)
        # API 契约接口（interfaces/api/*Api）只声明映射注解，实现类在 starter 层，
        # 因此这里同时接受「实现类」与「契约接口」两种载体。
        is_api_contract = re.search(r"\binterface\s+\w+Api\b", source) is not None
        if "@RestController" not in source and "@Controller" not in source and not is_api_contract:
            continue
        class_index = source.find("class ")
        if class_index < 0:
            class_index = len(source)
        interface_index = source.find("interface ")
        if 0 <= interface_index < class_index:
            class_index = interface_index

        class_path = ""
        for match in MAPPING_RE.finditer(source):
            if match.start() > class_index:
                break
            if match.group(1) == "Request":
                class_path = annotation_paths(match.group(2))[0]
                break

        for match in MAPPING_RE.finditer(source):
            if match.start() < class_index:
                continue
            method_paths = annotation_paths(match.group(2))
            body_start = source.find("{", match.end())
            body_end = source.find("}", body_start) if body_start >= 0 else len(source)
            method_body = source[match.end(): body_end if body_end >= 0 else len(source)]
            perms = re.findall(r'@RequirePermission\s*\(\s*(?:value\s*=\s*)?"([^"]*)"', method_body)
            for method_path in method_paths:
                full = normalize_version(join_path(class_path, method_path))
                if "/internal" not in full:
                    continue
                module = module_root(java_file, repo_root)
                service = full.strip("/").split("/")[0]
                endpoints.append(Endpoint(
                    path=full,
                    file=java_file,
                    module=module,
                    service=service,
                    permissions=perms,
                ))
    if not endpoints:
        findings.append(Finding("FAIL", "未扫描到任何内网端点，扫描逻辑可能失效（检查 BACKEND_DIR）"))
    return endpoints, findings


# ---------------------------------------------------------------- 各层核对


def gateway_patterns(repo_root: Path) -> list[str]:
    """读网关 internal-deny 的 patterns（缺失时用默认值）。

    :param repo_root: 仓库根
    :return: 网关收口模式列表
    """
    yml = repo_root / BACKEND_DIR / "whale-services/whale-service-gateway/src/main/resources/application.yml"
    patterns = read_yaml_list(yml, "internal-deny", "patterns")
    return patterns if patterns else list(DEFAULT_GATEWAY_PATTERNS)


def token_patterns(module: Path) -> tuple[list[str], list[str]]:
    """读某个服务模块的内网令牌受保护/豁免模式。

    :param module: 模块根
    :return: (受保护模式, 豁免模式)
    """
    yml = module / "src/main/resources/application.yml"
    protected = read_yaml_list(yml, "internal-token", "path-patterns") or list(DEFAULT_TOKEN_PATTERNS)
    excluded = read_yaml_list(yml, "internal-token", "exclude-patterns") or []
    return protected, excluded


def auth_permit_patterns(repo_root: Path) -> list[str]:
    """读 auth SecurityConfig 里 permitAll 的路径模式。

    :param repo_root: 仓库根
    :return: 放行模式列表；读不到返回空列表（此时调用方应报 FAIL）
    """
    config = (repo_root / BACKEND_DIR / "whale-services/whale-service-auth/whale-service-auth-infrastructure"
              / "src/main/java/com/bluewhale/auth/infrastructure/config/SecurityConfig.java")
    if not config.is_file():
        return []
    source = strip_comments(config.read_text(encoding="utf-8"))
    matcher = re.search(r"\.requestMatchers\((.*?)\)\s*\.permitAll\(\)", source, re.S)
    if not matcher:
        return []
    return re.findall(r'"([^"]+)"', matcher.group(1))


def check_endpoints(repo_root: Path, endpoints: list[Endpoint]) -> list[Finding]:
    """按三层访问控制逐个端点核对。

    :param repo_root: 仓库根
    :param endpoints: 端点列表
    :return: 核对结果
    """
    findings: list[Finding] = []
    gateway = gateway_patterns(repo_root)
    permit = auth_permit_patterns(repo_root)
    token_cache: dict[Path, tuple[list[str], list[str]]] = {}

    for endpoint in endpoints:
        if endpoint.service == "internal":
            # 根级 /internal/**（starter 的调度触发端点）：不经网关，只由令牌层保护
            pass
        elif not matches_any(gateway, endpoint.path):
            findings.append(Finding(
                "FAIL",
                f"网关未收口 {endpoint.path}：internal 段前超过一段，`/*/internal/**` 不跨段，"
                f"外部可经网关直达（{endpoint.file.name}）"))
        if endpoint.service == "internal" and not ant_match("/**/internal/**", endpoint.path):
            findings.append(Finding("FAIL", f"根级内网端点未被令牌模式覆盖：{endpoint.path}"))

        if endpoint.module not in token_cache:
            token_cache[endpoint.module] = token_patterns(endpoint.module)
        protected, excluded = token_cache[endpoint.module]
        if matches_any(excluded, endpoint.path):
            findings.append(Finding(
                "WARN",
                f"{endpoint.path} 被 internal-token.exclude-patterns 豁免，只能靠自带凭据鉴权"
                f"（{endpoint.module.name}）"))
        elif not matches_any(protected, endpoint.path):
            findings.append(Finding(
                "FAIL",
                f"令牌层未覆盖 {endpoint.path}：{endpoint.module.name} 的 path-patterns 不含该路径，"
                f"内网接口可被任意调用"))

        if "whale-service-auth" in endpoint.module.parts:
            if not matches_any(permit, endpoint.path):
                findings.append(Finding(
                    "FAIL",
                    f"auth 白名单缺 {endpoint.path}：Spring Security 会先返回 401，"
                    f"令牌层根本轮不到（SecurityConfig.permitAll）"))

        if endpoint.permissions:
            findings.append(Finding(
                "FAIL",
                f"{endpoint.path} 标了 @RequirePermission({endpoint.permissions[0]})："
                f"服务间调用没有用户权限码，必然 403（{endpoint.file.name}）"))
    return findings


def check_ci_workdirs(repo_root: Path) -> list[Finding]:
    """检查工作流里 ``working-directory`` 指向的目录是否存在。

    :param repo_root: 仓库根
    :return: 核对结果
    """
    findings: list[Finding] = []
    pattern = re.compile(r"working-directory:\s*(\S+)")
    for relative in WORKFLOW_DIRS:
        directory = repo_root / relative
        if not directory.is_dir():
            continue
        for yml in sorted(directory.glob("*.yml")):
            for number, line in enumerate(yml.read_text(encoding="utf-8").splitlines(), start=1):
                if line.lstrip().startswith("#"):
                    continue
                match = pattern.search(line)
                if not match:
                    continue
                target = match.group(1).strip('"').strip("'")
                if "${{" in target:
                    continue
                if not (repo_root / target).is_dir():
                    findings.append(Finding(
                        "FAIL",
                        f"{yml.relative_to(repo_root).as_posix()}:{number} "
                        f"working-directory={target} 不存在（目录已重命名？）"))
    return findings


# ---------------------------------------------------------------- 入口


def main() -> int:
    """执行门禁并打印结论。

    :return: 进程退出码
    """
    parser = argparse.ArgumentParser(description="内网端点边界与 CI 工作目录静态门禁")
    parser.add_argument("--root", default=".", help="仓库根目录（默认当前目录）")
    parser.add_argument("--verbose", action="store_true", help="逐条打印通过项")
    args = parser.parse_args()

    repo_root = Path(args.root).resolve()
    endpoints, findings = collect_endpoints(repo_root)
    findings += check_endpoints(repo_root, endpoints)
    findings += check_ci_workdirs(repo_root)

    by_service: dict[str, int] = {}
    for endpoint in endpoints:
        by_service[endpoint.service] = by_service.get(endpoint.service, 0) + 1
    print(f"扫描内网端点 {len(endpoints)} 条，涉及 {len(by_service)} 个前缀："
          + "、".join(f"{name}({count})" for name, count in sorted(by_service.items())))

    if args.verbose:
        for endpoint in endpoints:
            print(f"OK   {endpoint.path}  [{endpoint.module.name}]")

    failed = [finding for finding in findings if finding.level == "FAIL"]
    warned = [finding for finding in findings if finding.level == "WARN"]
    for finding in failed:
        print(f"FAIL {finding.message}")
    for finding in warned:
        print(f"WARN {finding.message}")

    if failed:
        print(f"RESULT: FAIL ({len(failed)} 项阻断，{len(warned)} 项告警)")
        return 1
    print(f"RESULT: PASS ({len(endpoints)} 条内网端点三层口径一致，{len(warned)} 项告警)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
