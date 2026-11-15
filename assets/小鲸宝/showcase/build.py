"""小鲸宝展示页构建脚本

两种产物，共用 src/app.js + src/template.html：

  python build.py                 目录模式 -> index.html + app.bundle.js
                                  （模型从 ../model/xiaojingbao.glb 相对加载，需经 http 访问：
                                    vite dev server 或 `python -m http.server`）

  python build.py --standalone 出.html [--glb 路径]
                                  单文件模式 -> 一个 html，GLB 以 base64 内嵌，
                                  双击即可打开（file:// 下不触发跨域）

依赖：esbuild + three（自动在 whale-frontend / whale-world 的 node_modules 里找，
      也可用 ESBUILD_BIN 指定 esbuild）
"""
import base64
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src")
TEMPLATE = os.path.join(SRC, "template.html")


def ancestors():
    out = []
    d = HERE
    while True:
        out.append(d)
        parent = os.path.dirname(d)
        if parent == d:
            return out
        d = parent


def find_glb():
    for c in ("model/xiaojingbao.glb", "xiaojingbao.glb", "../model/xiaojingbao.glb"):
        p = os.path.normpath(os.path.join(HERE, "..", c))
        if os.path.exists(p):
            return p
    raise SystemExit("找不到 xiaojingbao.glb：用 --glb 指定")


def find_app_node_modules():
    """three 装在应用里：优先 whale-world，其次任一含 three 的 node_modules"""
    for d in ancestors():
        for rel in ("node_modules", os.path.join("whale-frontend", "node_modules"),
                    os.path.join("whale-frontend", "platform-apps", "whale-world", "node_modules"),
                    os.path.join("platform-apps", "whale-world", "node_modules")):
            cand = os.path.join(d, rel)
            if os.path.isdir(os.path.join(cand, "three")):
                return cand
    raise SystemExit("找不到 three：先 pnpm install，或设置 NODE_PATH")


def _pick_esbuild(store):
    if not os.path.isdir(store):
        return None
    hits = sorted(
        (os.path.join(store, n, "node_modules", "esbuild", "bin", "esbuild")
         for n in os.listdir(store) if n.startswith("esbuild@")),
        reverse=True)
    for h in hits:
        if os.path.exists(h):
            return h
    return None


def find_esbuild():
    env = os.environ.get("ESBUILD_BIN")
    if env and os.path.exists(env):
        return env
    exe = shutil.which("esbuild")
    if exe:
        return exe
    for d in ancestors():
        for rel in ("node_modules/.pnpm",
                    "whale-frontend/node_modules/.pnpm",
                    "whale-frontend/platform-apps/whale-world/node_modules/.pnpm"):
            hit = _pick_esbuild(os.path.join(d, rel))
            if hit:
                return hit
    raise SystemExit("找不到 esbuild：设置环境变量 ESBUILD_BIN 指向可执行文件")


def bundle(outfile):
    os.makedirs(os.path.dirname(outfile), exist_ok=True)
    cmd = [
        "node", find_esbuild(), os.path.join(SRC, "app.js"),
        "--bundle", "--format=iife", "--minify", "--target=es2020",
        '--define:process.env.NODE_ENV="production"',
        f"--outfile={outfile}", "--log-level=warning",
        # 产物是压缩代码，且位于 app 的 src/ 下会被 `eslint src` 扫到，整文件豁免
        "--banner:js=/* eslint-disable */",
    ]
    env = dict(os.environ, NODE_PATH=find_app_node_modules())
    subprocess.run(cmd, check=True, env=env)
    with open(outfile, encoding="utf-8") as f:
        return f.read()


def render(html, js, glb_path, url=""):
    """glb_path 为 None 时产出目录模式（外部 js + 相对路径模型）"""
    if glb_path:
        with open(glb_path, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("ascii")
        model = ('<!-- 模型：base64 内嵌，file:// 可直接打开 -->\n'
                 f'  <script id="glb" type="application/octet-stream">{b64}</script>')
        app = f"<script>{js.replace('</script', '<\\/script')}</script>"
        url_tag = ""
    else:
        model = "<!-- 模型：相对路径加载，需经 http 访问 -->"
        app = '<script src="./app.bundle.js"></script>'
        url_tag = f'<script>window.__GLB_URL__ = "{url}";</script>'
    html = html.replace("__MODEL_PLACEHOLDER__", model)
    html = html.replace("__GLB_URL_PLACEHOLDER__", url_tag)
    html = html.replace("__APP_PLACEHOLDER__", app)
    return html


def main():
    args = sys.argv[1:]
    standalone = "--standalone" in args
    glb = args[args.index("--glb") + 1] if "--glb" in args else find_glb()

    with open(TEMPLATE, encoding="utf-8") as f:
        template = f.read()

    if standalone:
        dst = os.path.join(HERE, "showcase.html")
        i = args.index("--standalone")
        if i + 1 < len(args) and not args[i + 1].startswith("--"):
            dst = args[i + 1]
        js = bundle(os.path.join(HERE, "build", "app.bundle.js"))
        html = render(template, js, glb)
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(html)
        print("standalone -> %s  %.2f MB" % (dst, os.path.getsize(dst) / 1048576))
    else:
        js_path = os.path.join(HERE, "app.bundle.js")
        js = bundle(js_path)
        url = os.path.relpath(glb, HERE).replace(os.sep, "/")
        html = render(template, js, None, url)
        dst = os.path.join(HERE, "index.html")
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(html)
        print("bundle     -> %s  %.0f KB" % (js_path, os.path.getsize(js_path) / 1024))
        print("index.html -> %s  %.0f KB  (model: %s)" % (dst, os.path.getsize(dst) / 1024, url))


main()
