/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证微前端链路：
 *   主应用(:3000) → 按路由加载子应用(:3200 / :3300) → 全局状态下发 → 切换路由卸载。
 *
 * 全程走真实浏览器，不依赖任何测试替身 —— 微前端的问题（脚本执行方式、
 * 生命周期对接、容器挂载）几乎都只在真实运行时才暴露。
 */
import { spawn } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9333;
const PORTAL_URL = "http://127.0.0.1:3000/";

/** HMR 探测：临时改这一行文本，看子应用是否自动更新，测完还原。 */
const HMR_TARGET =
  "D:/workspace/BlueWhale/web/platform/platform-log-console/src/App.tsx";
const HMR_FROM = "查询与链路追踪的端到端演示";
const HMR_TO = "查询与链路追踪的端到端演示·HMR探测";

/** 样式热更新探测：追加一条自定义属性，用计算样式判断是否生效。 */
const CSS_TARGET =
  "D:/workspace/BlueWhale/web/platform/platform-log-console/src/App.less";
const CSS_PROBE = "\n.topbar { --hmr-probe: on; }\n";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitForDebugger(timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`);
      if (res.ok) return true;
    } catch {
      /* 还没起来 */
    }
    await sleep(300);
  }
  throw new Error("Chrome 调试端口未就绪");
}

async function main() {
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-micro-"));
  const chrome = spawn(
    CHROME,
    [
      "--headless=new",
      `--remote-debugging-port=${DEBUG_PORT}`,
      `--user-data-dir=${userDataDir}`,
      "--no-first-run",
      "--no-default-browser-check",
      "--no-proxy-server",
      "--disable-gpu",
      "--window-size=1400,1000",
      PORTAL_URL,
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then(
      (r) => r.json(),
    );
    const page = targets.find(
      (t) => t.type === "page" && t.url.includes("127.0.0.1:3000"),
    );
    if (!page)
      throw new Error(
        "未找到主应用页面：" + JSON.stringify(targets.map((t) => t.url)),
      );

    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let seq = 0;
    const waiting = new Map();
    const errors = [];
    const logs = [];
    const badResponses = [];
    const assetRequests = [];

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && waiting.has(message.id)) {
        waiting.get(message.id)(message.result);
        waiting.delete(message.id);
        return;
      }
      if (message.method === "Runtime.exceptionThrown") {
        const d = message.params.exceptionDetails;
        errors.push(d.exception?.description || d.text);
      }
      if (
        message.method === "Log.entryAdded" &&
        message.params.entry.level === "error"
      ) {
        errors.push(
          `${message.params.entry.text} @ ${message.params.entry.url ?? "-"}`,
        );
      }
      if (
        message.method === "Network.responseReceived" &&
        message.params.response.status >= 400
      ) {
        badResponses.push(
          `${message.params.response.status} ${message.params.response.url}`,
        );
      }
      if (
        message.method === "Network.requestWillBeSent" &&
        /App\.tsx|vite\/client|react-refresh/.test(message.params.request.url)
      ) {
        assetRequests.push(message.params.request.url);
      }
      if (message.method === "Runtime.consoleAPICalled") {
        const text = message.params.args
          .map((a) => a.value ?? a.description ?? "")
          .join(" ")
          .slice(0, 200);
        logs.push(`[${message.params.type}] ${text}`);
      }
    };

    const call = (method, params = {}) =>
      new Promise((resolve) => {
        const id = ++seq;
        waiting.set(id, resolve);
        ws.send(JSON.stringify({ id, method, params }));
      });

    const evaluate = async (expression) => {
      const result = await call("Runtime.evaluate", {
        expression,
        awaitPromise: true,
        returnByValue: true,
      });
      if (result.exceptionDetails) {
        throw new Error(
          "页面内求值失败：" + JSON.stringify(result.exceptionDetails.text),
        );
      }
      return result.result.value;
    };

    await call("Runtime.enable");
    await call("Log.enable");
    await call("Network.enable");

    const step = (title, value) => console.log(`\n== ${title} ==\n${value}`);

    await sleep(2500);

    step(
      "1. 主应用首页",
      await evaluate(`JSON.stringify({
        nav: [...document.querySelectorAll('.basic-layout__link')].map(a => a.textContent),
        entries: [...document.querySelectorAll('.home__link')].map(a => a.textContent),
      })`),
    );

    /* ---------------- 加载日志控制台 ---------------- */

    await evaluate(
      `[...document.querySelectorAll('.basic-layout__link')].find(a => a.textContent.includes('日志控制台')).click()`,
    );
    await sleep(4000);

    step(
      "2. 挂载日志控制台",
      await evaluate(`JSON.stringify({
        url: location.pathname,
        phase: document.querySelector('.micro-app__phase')?.textContent,
        hostHasApp: !!document.querySelector('.micro-app__host #root')?.children.length,
        indicator: document.getElementById('micro-host-indicator')?.textContent,
        subAppText: document.querySelector('.micro-app__host')?.textContent.replace(/\\s+/g,' ').slice(0, 90),
      })`),
    );

    /* ---------------- 子应用热更新 ---------------- */

    const originalSource = readFileSync(HMR_TARGET, "utf8");
    let hmrResult;
    const logMark = logs.length;
    try {
      writeFileSync(
        HMR_TARGET,
        originalSource.replace(HMR_FROM, HMR_TO),
        "utf8",
      );
      await sleep(4000);
      hmrResult = await evaluate(
        `document.querySelector('.micro-app__host')?.textContent.includes('HMR探测') ? '已自动更新' : '未更新（热更新未生效）'`,
      );
    } finally {
      writeFileSync(HMR_TARGET, originalSource, "utf8");
      await sleep(2500);
    }
    step("2b. 改子应用源码后是否自动热更新", hmrResult);
    step(
      "2c. 热更新期间的模块请求 / 日志",
      [
        "资源请求：",
        ...[...new Set(assetRequests)].map((u) => "  " + u),
        "刷新运行时状态：",
        "  " +
          (await evaluate(
            `JSON.stringify({ preamble: window.__vite_plugin_react_preamble_installed__ ?? null, refreshReg: typeof window.$RefreshReg$, hook: (() => { const h = window.__REACT_DEVTOOLS_GLOBAL_HOOK__; if (!h) return null; return { renderers: h.renderers?.size ?? null, interfaces: h.rendererInterfaces?.size ?? null, scheduleRefresh: typeof h.scheduleRefresh }; })() })`,
          )),
        "日志：",
        ...logs.slice(logMark).map((l) => "  " + l.slice(0, 160)),
      ].join("\n"),
    );

    /* ---------------- 样式热更新（对照组：不经过 React Refresh） ---------------- */

    const originalCss = readFileSync(CSS_TARGET, "utf8");
    let cssResult;
    try {
      writeFileSync(CSS_TARGET, originalCss + CSS_PROBE, "utf8");
      await sleep(3500);
      cssResult = await evaluate(
        `(() => { const el = document.querySelector('.micro-app__host .topbar'); if (!el) return '未找到 .topbar'; return getComputedStyle(el).getPropertyValue('--hmr-probe').trim() === 'on' ? '样式已自动更新' : '样式未更新'; })()`,
      );
    } finally {
      writeFileSync(CSS_TARGET, originalCss, "utf8");
      await sleep(2000);
    }
    step("2d. 改子应用样式是否自动热更新", cssResult);

    /* ---------------- 全局状态下发 ---------------- */

    await evaluate(
      `[...document.querySelectorAll('.basic-layout__action')].find(b => b.textContent.includes('登录')).click()`,
    );
    await sleep(600);

    await evaluate(
      `[...document.querySelectorAll('.basic-layout__action')].find(b => b.textContent.includes('全局主题')).click()`,
    );
    await sleep(600);

    step(
      "3. 全局状态下发到子应用",
      await evaluate(`JSON.stringify({
        indicator: document.getElementById('micro-host-indicator')?.textContent,
        hostProps: typeof window.__MICRO_HOST__,
      })`),
    );

    /* ---------------- 子应用内真实调用后端 ---------------- */

    step(
      "4. 子应用经主应用代理访问后端",
      await evaluate(`(async () => {
        const res = await fetch('/api/health');
        const body = await res.json();
        return JSON.stringify({ status: body.data?.status, profile: body.data?.profile });
      })()`),
    );

    /* ---------------- 切换到事件控制台 ---------------- */

    await evaluate(
      `[...document.querySelectorAll('.basic-layout__link')].find(a => a.textContent.includes('行为事件')).click()`,
    );
    await sleep(4000);

    step(
      "5. 切换到行为事件控制台",
      await evaluate(`JSON.stringify({
        url: location.pathname,
        phase: document.querySelector('.micro-app__phase')?.textContent,
        indicator: document.getElementById('micro-host-indicator')?.textContent,
        subAppText: document.querySelector('.micro-app__host')?.textContent.replace(/\\s+/g,' ').slice(0, 90),
      })`),
    );

    /* ---------------- 返回首页：确认卸载 ---------------- */

    await evaluate(
      `[...document.querySelectorAll('.basic-layout__link')].find(a => a.textContent.includes('概览')).click()`,
    );
    await sleep(1500);

    step(
      "6. 返回首页后子应用是否清理干净",
      await evaluate(`JSON.stringify({
        url: location.pathname,
        microAppNode: document.querySelectorAll('.micro-app').length,
        indicator: document.getElementById('micro-host-indicator')?.textContent ?? null,
        hostProps: typeof window.__MICRO_HOST__,
      })`),
    );

    /* ---------------- 错误汇总 ---------------- */

    const relevantErrors = errors.filter(
      (e) =>
        !e.includes("Download the React DevTools") &&
        !e.includes("favicon.ico"),
    );
    step(
      "7. 浏览器侧错误",
      relevantErrors.length ? relevantErrors.join("\n---\n") : "（无）",
    );
    step(
      "7b. 4xx/5xx 请求",
      badResponses.length ? [...new Set(badResponses)].join("\n") : "（无）",
    );
    step("8. 控制台输出摘录", logs.slice(0, 25).join("\n"));

    await ws.close();
  } finally {
    chrome.kill();
  }
}

main().catch((error) => {
  console.error("\n验证失败：", error.message);
  process.exit(1);
});
