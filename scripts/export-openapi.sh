#!/usr/bin/env bash
#
# 从运行中的应用导出 OpenAPI 契约到 docs/api/openapi.json
#
# 为什么是"导出"而不是"手写"：
#   手写契约的好处是契约先行，代价是它与代码会各自演化，而漂移是无声的 ——
#   前端照着过期的契约写完，才发现接口对不上。本脚本让契约成为**代码的产物**，
#   从结构上消除漂移的可能。代价是无法先于代码评审契约，
#   在只有一支团队推进的阶段可以接受。详见 docs/decisions/0006。
#
# 用法：
#   bash scripts/export-openapi.sh
#
# 前置：先构建好后端 → cd server && mvn -DskipTests install
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT/server"
JAR="$SERVER_DIR/sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar"
OUT="$ROOT/docs/api/openapi.json"
LOG="${TMPDIR:-/tmp}/bluewhale-openapi-export.log"

# 刻意用一个不常用的端口：8080 上很可能正跑着开发用的实例，
# 端口冲突会让脚本"连上了别人的服务并导出一份错误的契约"，
# 而且没有任何报错 —— 这是最难发现的一类错误。
PORT="${OPENAPI_PORT:-18080}"

if [ ! -f "$JAR" ]; then
  echo "错误：未找到 $JAR" >&2
  echo "请先执行：cd server && mvn -DskipTests install" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# JDK 版本门禁
#
# 与构建侧保持一致（见 server/sts-parent 的 enforcer 规则）：JDK 21 是硬要求。
# 开发机常同时装多个 JDK，PATH 上的 java 未必是 21。若直接用旧版本跑这个 jar，
# 报错是 UnsupportedClassVersionError —— 它指向"jar 打不开"，
# 而真正的原因在"JDK 太旧"，两者之间的关联不看错误码第 3 行是发现不了的。
# 在这里先查一次，把话说明白。
# ---------------------------------------------------------------------------
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
if [ -z "$JAVA_BIN" ]; then
  echo "错误：未找到 java。请安装 JDK 21 并设置 JAVA_HOME。" >&2
  exit 1
fi

# 用 java.specification.version 而不是解析 `java -version` 的文本：
# 后者在 8 与 9+ 之间格式改过一次，且不同厂商的输出措辞不一。
JAVA_SPEC="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 \
  | sed -n 's/^ *java\.specification\.version = //p' | head -1)"

case "$JAVA_SPEC" in
  2[1-9]|[3-9][0-9]) ;;
  *)
    echo "错误：导出契约需要 JDK 21 及以上，当前 java 是 ${JAVA_SPEC:-未知}（$JAVA_BIN）。" >&2
    echo "提示：JAVA_HOME=/path/to/jdk-21 bash scripts/export-openapi.sh" >&2
    exit 1
    ;;
esac

mkdir -p "$(dirname "$OUT")"

echo "启动应用（端口 $PORT，JDK $JAVA_SPEC）以导出契约 ..."
# shellcheck disable=SC2086
"$JAVA_BIN" -jar "$JAR" --server.port="$PORT" --springdoc.api-docs.enabled=true > "$LOG" 2>&1 &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true' EXIT

TMP="$OUT.tmp"
rm -f "$TMP"

for _ in $(seq 1 90); do
  # 应用启动期间会连接被拒，-f 让 curl 以非零码退出，从而继续等待
  if curl -sf "http://127.0.0.1:$PORT/v3/api-docs" -o "$TMP"; then
    break
  fi
  # 进程已退出就没必要继续等了，直接看日志
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "错误：应用启动失败。日志尾部：" >&2
    tail -n 40 "$LOG" >&2
    exit 1
  fi
  sleep 1
done

if [ ! -s "$TMP" ]; then
  echo "错误：未能获取 /v3/api-docs。日志尾部：" >&2
  tail -n 40 "$LOG" >&2
  rm -f "$TMP"
  exit 1
fi

# 格式化后再落盘：契约文件要进版本库，压成一行的话每次改动都不可读、不可评审。
node -e '
const fs = require("fs");
const path = process.argv[1];
const doc = JSON.parse(fs.readFileSync(path, "utf8"));
fs.writeFileSync(path, JSON.stringify(doc, null, 2) + "\n");
' "$TMP"

mv "$TMP" "$OUT"

echo "已导出：$OUT"
node -e '
const doc = require(process.argv[1]);
const paths = Object.keys(doc.paths || {});
console.log("  标题   : " + (doc.info && doc.info.title));
console.log("  版本   : " + (doc.info && doc.info.version));
console.log("  接口数 : " + paths.reduce((n, p) => n + Object.keys(doc.paths[p]).length, 0));
console.log("  路径   :");
for (const p of paths) {
  for (const m of Object.keys(doc.paths[p])) {
    console.log("    " + m.toUpperCase().padEnd(6) + p);
  }
}
' "$OUT"
