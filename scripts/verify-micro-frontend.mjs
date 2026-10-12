/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证微前端链路：
 *   主应用(:3000) → 按路由加载子应用(:3200 / :3300 / :3400) → 全局状态下发 →
 *   切换路由卸载（含三维应用的 WebGL 上下文释放）。
 *
 * 全程走真实浏览器，不依赖任何测试替身 —— 微前端的问题（脚本执行方式、
 * 生命周期对接、容器挂载）几乎都只在真实运行时才暴露。
 *
 * 前置：**后端与五个应用都已启动**
 *   pnpm --filter platform-portal dev        (:3000)
 *   pnpm --filter platform-log-console dev   (:3200)
 *   pnpm --filter platform-event-console dev (:3300)
 *   pnpm --filter platform-environment dev   (:3400)
 *   pnpm --filter platform-admin dev         (:3500)
 *   pnpm --filter platform-audit-console dev (:3600)
 *   后端 :8080（h2 profile，含种子账号）
 *
 * 注意：基座自 ADR-0018 起有**登录守卫**，未登录访问任何受保护路由都会被弹到 `/login`。
 * 因此本脚本必须先真实登录一次，否则页面里既没有导航也没有首页入口。
 */
import { spawn } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9333;
const PORTAL_URL = "http://127.0.0.1:3000/";

/** 种子管理员（h2 profile 自动种下，口令见 AuthProperties.SeedAdmin） */
const ACCOUNT = {
  tenantCode: "default",
  username: "admin",
  password: "Admin@12345",
};

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
        const settle = waiting.get(message.id);
        waiting.delete(message.id);
        /*
         * 协议层的错误（`message.error`）也要交回调用方，而不是丢掉 ——
         * 丢掉的结果是 `undefined`，调用方只能看到一句无信息量的
         * "Cannot read properties of undefined"。
         */
        settle(message.error ? { protocolError: message.error } : message.result);
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

    /**
     * 在页面里求值。
     *
     * <p>**必须容忍"求值的那一刻页面正在导航"**：这时 CDP 回的是协议层错误
     * （`Execution context was destroyed`），而不是结果体。本脚本要挂载 / 卸载 /
     * 切换好几个子应用，守卫换地址的一瞬间也会踩到 —— 重试几次就好，
     * 直接抛错会让脚本在断言之前就"自身出错"。
     */
    const evaluate = async (expression) => {
      let lastError = null;
      for (let attempt = 1; attempt <= 8; attempt += 1) {
        const result = await call("Runtime.evaluate", {
          expression,
          awaitPromise: true,
          returnByValue: true,
        });
        if (result?.exceptionDetails) {
          throw new Error(
            "页面内求值失败：" + JSON.stringify(result.exceptionDetails.text),
          );
        }
        if (result?.result) return result.result.value;
        lastError = result?.protocolError ?? result;
        await sleep(200);
      }
      throw new Error(
        `Runtime.evaluate 连续 8 次没有拿到结果体：${JSON.stringify(lastError)?.slice(0, 200)}`,
      );
    };

    /** 轮询直到表达式为真；超时则抛错并说明在等什么 */
    const waitFor = async (expression, what, timeoutMs = 30000) => {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        try {
          if (await evaluate(expression)) return;
        } catch {
          /* 页面正在导航，下一轮再试 */
        }
        await sleep(250);
      }
      throw new Error(`等待超时（${timeoutMs}ms）：${what}`);
    };

    /** 用真实鼠标点一下元素中心（用于聚焦输入框、点导航） */
    const clickElement = async (selector) => {
      const box = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`);
      if (!box) throw new Error(`找不到元素：${selector}`);
      const base = { x: Math.round(box.x), y: Math.round(box.y), button: "left", clickCount: 1 };
      await call("Input.dispatchMouseEvent", { type: "mouseMoved", ...base });
      await call("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
      await call("Input.dispatchMouseEvent", { type: "mouseReleased", ...base });
    };

    /** 输入文本（走 CDP 的 insertText，会触发真实的 input 事件，React 能收到） */
    const typeText = async (text) => {
      await call("Input.insertText", { text });
    };

    /**
     * 按可见文本点一个元素。
     *
     * <p>找不到时给出**可读的**错误，而不是让 `.find(...)` 返回 `undefined` 再
     * `.click()` 抛一句 `Uncaught` —— 后者既看不出是哪个选择器错了，
     * 也看不出是"布局改了"还是"页面没渲染完"。
     */
    const clickByText = async (selector, text) => {
      const ok = await evaluate(`(() => {
        const el = [...document.querySelectorAll(${JSON.stringify(selector)})]
          .find(n => n.textContent.includes(${JSON.stringify(text)}));
        if (!el) return false;
        el.click();
        return true;
      })()`);
      if (!ok) throw new Error(`找不到含「${text}」的 ${selector}`);
    };

    await call("Runtime.enable");
    await call("Log.enable");
    await call("Network.enable");

    const step = (title, value) => console.log(`\n== ${title} ==\n${value}`);

    const problems = [];
    const record = (message) => problems.push(message);

    await sleep(2500);

    /* ---------------- 0. 真实登录（基座有守卫，不登录什么都看不到） ---------------- */

    await waitFor(
      `location.pathname === '/login'`,
      "守卫把未登录用户重定向到登录页",
    );
    await clickElement('input[name="password"]');
    await typeText(ACCOUNT.password);
    const typedLength = await evaluate(
      `document.querySelector('input[name="password"]').value.length`,
    );
    if (typedLength !== ACCOUNT.password.length) {
      record(`口令没有被完整输入（期望 ${ACCOUNT.password.length} 位，实际 ${typedLength} 位）`);
    }
    await evaluate(`document.querySelector('.login__card').requestSubmit()`);
    await waitFor(`location.pathname === '/'`, "登录成功后回到首页");
    step("0. 登录", `以 ${ACCOUNT.username} 登录后落地 ${await evaluate("location.pathname")}`);

    const home = JSON.parse(
      await evaluate(`JSON.stringify({
        nav: [...document.querySelectorAll('.basic-layout__link')].map(a => a.textContent),
        entries: [...document.querySelectorAll('.home__link')].map(a => a.textContent),
      })`),
    );
    step("1. 主应用首页", `导航 ${home.nav.length} 项：${home.nav.join(" / ")}`);
    // 登录后导航与首页入口都必须有内容 —— 没有就说明守卫或注册表出了问题，
    // 而不是"后面的点击失败了"（后者只是症状）
    if (home.nav.length === 0) record("登录后首页仍然没有导航 —— 守卫或会话恢复没走通");
    // 导航比首页入口多一项：导航第一项是「概览」（基座自己的首页），首页的入口卡片只列子应用。
    // 两边都从同一张注册表推导，因此这个差值恒为 1。
    if (home.entries.length !== home.nav.length - 1) {
      record(`首页入口数 ${home.entries.length} 与导航项数 ${home.nav.length} 不匹配（应相差 1：导航含「概览」）`);
    }

    /* ---------------- 加载日志控制台 ---------------- */

    await clickByText(".basic-layout__link", "日志控制台");
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

    /*
     * 这里原来点的是基座的「登录」调试开关 —— 自 ADR-0018 起它已被**真实会话**取代
     * （登录页 + 路由守卫 + 退出登录），开关本身不存在了。
     *
     * 现在改成两件事：
     *   1) 断言子应用侧拿到的身份来自**真实登录结果**（userId / roles / perm 都非空）；
     *   2) 点「全局主题」→ 断言 `theme=` 确实跟着变（全局状态下发的最小充分证据）。
     * 第 1 条比原来的开关更有意义：它证明的是"会话经全局状态真的到了子应用"。
     */
    const readIndicator = `document.getElementById('micro-host-indicator')?.textContent ?? ''`;
    const identity = await evaluate(`(() => {
      const text = ${readIndicator};
      return {
        text,
        userId: (text.match(/userId=([^\\s·]+)/) ?? [])[1] ?? null,
        roles: Number((text.match(/roles=(\\d+)/) ?? [])[1] ?? '0'),
        perm: Number((text.match(/perm=(\\d+)/) ?? [])[1] ?? '0'),
        theme: (text.match(/theme=(\\w+)/) ?? [])[1] ?? null,
      };
    })()`);
    if (!identity.userId || identity.userId === 'null') {
      record('子应用侧没有收到 userId —— 会话结果没有经全局状态下发');
    }
    if (identity.roles < 1 || identity.perm < 1) {
      record(`子应用侧拿到的 roles / perm 为空（roles=${identity.roles} · perm=${identity.perm}）`);
    }

    const themeToggled = await evaluate(`(() => {
      const btn = [...document.querySelectorAll('.basic-layout__action')]
        .find(b => b.textContent.includes('全局主题'));
      if (!btn) return false;
      btn.click();
      return true;
    })()`);
    if (!themeToggled) record('找不到基座的「全局主题」按钮 —— 布局改了，选择器要跟着改');
    await waitFor(
      `(((${readIndicator}).match(/theme=(\\w+)/) ?? [])[1] ?? null) !== ${JSON.stringify(identity.theme)}`,
      '主题变化经全局状态下发到子应用',
      10000,
    );

    step(
      "3. 全局状态下发到子应用",
      `${identity.text}\n  主题 ${identity.theme} → ${await evaluate(
        `((${readIndicator}).match(/theme=(\\w+)/) ?? [])[1]`,
      )}（点「全局主题」后子应用跟着变）\n  身份经全局状态下发：userId=${identity.userId} · roles=${identity.roles} · perm=${identity.perm}`,
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

    await clickByText(".basic-layout__link", "行为事件");
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

    /* ---------------- 三维环境：WebGL 上下文 ---------------- */

    await clickByText(".basic-layout__link", "三维环境");
    await sleep(6000);

    step(
      "6. 挂载三维环境应用",
      await evaluate(`JSON.stringify({
        url: location.pathname,
        phase: document.querySelector('.micro-app__phase')?.textContent,
        modeBadge: document.querySelector('.env-app__badge')?.textContent,
        bridgeBadge: document.querySelectorAll('.env-app__badge')[1]?.textContent,
        globalStateBadge: document.querySelectorAll('.env-app__badge')[2]?.textContent,
        canvas: document.querySelectorAll('.scene-view__canvas').length,
        stats: [...document.querySelectorAll('.render-panel__metric-value')].map(e => e.textContent),
      })`),
    );

    // 抓住上下文引用（不能靠返回值带出来，WebGL 上下文不可结构化克隆），
    // 卸载之后再用同一份引用判断是否已被主动丢弃。
    step(
      "6b. 承接 WebGL 上下文引用",
      await evaluate(`(() => {
        const canvas = document.querySelector('.scene-view__canvas');
        window.__envCanvas = canvas;
        window.__envGl = canvas ? (canvas.getContext('webgl2') || canvas.getContext('webgl')) : null;
        return JSON.stringify({
          hasCanvas: !!canvas,
          hasContext: !!window.__envGl,
          lostYet: window.__envGl ? window.__envGl.isContextLost() : null,
        });
      })()`),
    );

    /* ---------------- 返回首页：确认卸载与 GPU 释放 ---------------- */

    await clickByText(".basic-layout__link", "概览");
    await sleep(2000);

    step(
      "7. 返回首页后子应用是否清理干净",
      await evaluate(`JSON.stringify({
        url: location.pathname,
        microAppNode: document.querySelectorAll('.micro-app').length,
        envCanvasLeft: document.querySelectorAll('.scene-view__canvas').length,
        contextLostAfterUnmount: window.__envGl ? window.__envGl.isContextLost() : '未取到引用',
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
      "8. 浏览器侧错误",
      relevantErrors.length ? relevantErrors.join("\n---\n") : "（无）",
    );
    // 只打印不判定等于"看不见"：子应用没起时 qiankun 会在浏览器里抛
    // `TypeError: Failed to fetch`，而上面每一步都还能"读出"一个 undefined 继续往下走。
    // 这条把它变成一次明确的失败。
    for (const message of relevantErrors) record(`浏览器报错：${message.split("\n")[0]}`);
    step(
      "8b. 4xx/5xx 请求",
      badResponses.length ? [...new Set(badResponses)].join("\n") : "（无）",
    );
    step("9. 控制台输出摘录", logs.slice(0, 25).join("\n"));

    /* ---------------- 结论 ---------------- */

    if (problems.length > 0) {
      console.log("\n=== 发现问题 ===");
      for (const p of problems) console.log(`  · ${p}`);
      throw new Error(`${problems.length} 项未通过`);
    }
    console.log("\n=== 结论：全部通过 ===");

    await ws.close();
  } finally {
    chrome.kill();
  }
}

main().catch((error) => {
  console.error("\n验证失败：", error.message);
  process.exit(1);
});
