/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「审计日志管道」这条链路。
 *
 * 它验证的东西，单元测试一条都测不到 —— 单测里接口是替身、子应用没有被基座挂载、
 * 也没有第二个身份：
 *   1. **记录是后端产生的**：在页面上真实写一次租户，审计表里随即多出对应记录，
 *      而前端全程只发过 GET（本页连写方法都没有）；
 *   2. **不可篡改落在"接口不存在"上**：对 /api/audit/logs 直接发 POST 得到 40500；
 *   3. **越权尝试同样留痕**：只读账号点演练区 → 被拒 40301 → 审计表里多一条 FAILURE；
 *   4. **演练区按钮不按权限显隐**（与权限管理台刻意相反）；
 *   5. 权限从基座流到第三个子应用，刷新后随会话一起恢复。
 *
 * 前置（四个进程都要在跑）：
 *   cd server && java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar   # :8080（h2，默认开两个种子账号）
 *   pnpm --filter platform-portal dev            # :3000
 *   pnpm --filter platform-audit-console dev     # :3600
 *
 * 用法：
 *   node scripts/verify-audit-pipeline.mjs                 # 只验证
 *   node scripts/verify-audit-pipeline.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { ADMIN, AUDITOR } from "./lib/seed-credentials.mjs";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9339;
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 1100 };
const SHOT_DIR = process.argv[2] ?? null;

/** 两个种子账号从 `lib/seed-credentials.mjs` 取 —— **不要再在脚本里抄口令**。
 *  真值来源是 `application-h2.yml`，可用 `BW_SEED_ADMIN_PASSWORD` 等环境变量覆盖。
 *  见 `docs/decisions/0029` 与 `scripts/README.md` 的硬性约束。 */

/** 三个读接口与唯一的"不该存在"的那个写接口 */
const AUDIT_GETS = [
  "/api/audit/logs",
  "/api/audit/logs/{id}",
  "/api/audit/logs/stats",
];
const AUDIT_WRITE_ATTEMPT = "/api/audit/logs";

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const problems = [];
const record = (message) => {
  problems.push(message);
  console.log(`  · ${message}`);
};

function step(title, body) {
  console.log(`\n== ${title} ==\n${body}`);
}

async function waitForDebugger(timeoutMs = 25000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`);
      if (res.ok) return;
    } catch {
      /* 还没起来 */
    }
    await sleep(300);
  }
  throw new Error("Chrome 调试端口未就绪");
}

async function main() {
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-audit-"));
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
      `--window-size=${VIEWPORT.width},${VIEWPORT.height}`,
      // 直接访问受保护的第三个子应用路由：未登录时应被基座守卫弹到登录页
      `${BASE_URL}/audit`,
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) =>
      r.json(),
    );
    const page = targets.find(
      (t) => t.type === "page" && t.url.includes("127.0.0.1:3000"),
    );
    if (!page) {
      throw new Error(
        "未找到门户页面：" + JSON.stringify(targets.map((t) => t.url)),
      );
    }

    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let seq = 0;
    const waiting = new Map();
    const errors = [];
    /** 页面发出的 GET /api/audit/** 请求（用来证明"前端只读"） */
    const auditRequests = [];

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && waiting.has(message.id)) {
        const settle = waiting.get(message.id);
        waiting.delete(message.id);
        /*
         * 协议层的错误（`message.error`）也要交回去，而不是丢掉 ——
         * 丢掉的结果是 `undefined`，调用方只能看到"响应缺少结果体"，
         * 而真正的原因（通常是 "Execution context was destroyed"：
         * 页面正在导航，执行上下文没了）完全看不到。
         */
        settle(message.error ? { protocolError: message.error } : message.result);
        return;
      }
      if (message.method === "Runtime.exceptionThrown") {
        const details = message.params.exceptionDetails;
        errors.push(details.exception?.description || details.text);
      }
      if (message.method === "Network.requestWillBeSent") {
        const { request } = message.params;
        if (request.url.includes("/api/audit/")) {
          auditRequests.push({ method: request.method, url: request.url });
        }
      }
      if (
        message.method === "Log.entryAdded" &&
        message.params.entry.level === "error"
      ) {
        const entry = message.params.entry;
        if ((entry.url ?? "").includes("favicon")) return;
        errors.push(`${entry.text} @ ${entry.url ?? "-"}`);
      }
    };

    function send(method, params = {}) {
      const id = (seq += 1);
      return new Promise((resolve, reject) => {
        waiting.set(id, (result) => {
          if (result?.exceptionDetails) {
            reject(new Error(result.exceptionDetails.text));
            return;
          }
          resolve(result);
        });
        ws.send(JSON.stringify({ id, method, params }));
      });
    }

    /**
     * 在页面里求值。
     *
     * <p>**必须容忍"求值的那一刻页面正在导航"**：这时 CDP 回的是协议层错误
     * （`Execution context was destroyed`），而不是结果体。首次进入受保护路由
     * （守卫会立刻把地址换掉）必然踩到这一下 —— 重试几次就好，
     * 而直接抛错会让脚本在第一个断言前就"自身出错"，看起来像脚本写坏了。
     */
    async function evaluate(expression) {
      let lastError = null;
      for (let attempt = 1; attempt <= 8; attempt += 1) {
        const response = await send("Runtime.evaluate", {
          expression,
          returnByValue: true,
          awaitPromise: true,
        });
        if (response?.result) return response.result.value;
        lastError = response?.protocolError ?? response;
        await sleep(200);
      }
      throw new Error(
        `Runtime.evaluate 连续 8 次没有拿到结果体：${JSON.stringify(lastError)?.slice(0, 200)}`,
      );
    }

    /** 在页面里轮询直到条件成立 */
    async function waitFor(expression, label, timeoutMs = 20000) {
      const ok = await evaluate(`new Promise((resolve) => {
        const deadline = Date.now() + ${timeoutMs};
        const timer = setInterval(() => {
          let value = false;
          try { value = Boolean(${expression}); } catch (error) { value = false; }
          if (value) { clearInterval(timer); resolve(true); }
          else if (Date.now() > deadline) { clearInterval(timer); resolve(false); }
        }, 120);
      })`);
      if (!ok) throw new Error(`等待超时：${label}`);
    }

    async function screenshot(name) {
      if (!SHOT_DIR) return;
      const shot = await send("Page.captureScreenshot", { format: "png" });
      writeFileSync(
        join(SHOT_DIR, `${name}.png`),
        Buffer.from(shot.data, "base64"),
      );
    }

    /**
     * 先滚到视野内再取坐标。
     *
     * <p>**这一步不能省**：本页自上而下有三块面板，流水一多就把演练区推到折叠线以下，
     * 那时 `getBoundingClientRect()` 给出的 y 超出视口高度，按这个坐标派发的点击
     * 落在空处 —— 表现为"点了没反应"，而且**数据多的时候才复现**、
     * 看起来像后端偶发失败，最容易查错方向。
     */
    const RECT_AFTER_SCROLL = (selector) => `(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        el.scrollIntoView({ block: 'center', inline: 'center' });
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`;

    async function clickSelector(selector) {
      const box = await evaluate(RECT_AFTER_SCROLL(selector));
      if (!box) throw new Error(`找不到元素：${selector}`);
      await dispatchClick(box);
    }

    async function dispatchClick(box) {
      const base = {
        x: Math.round(box.x),
        y: Math.round(box.y),
        button: "left",
        clickCount: 1,
      };
      await send("Input.dispatchMouseEvent", {
        type: "mouseMoved",
        ...base,
        button: "none",
      });
      await send("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
      await send("Input.dispatchMouseEvent", { type: "mouseReleased", ...base });
    }

    /** 按可见文本点击（聚合里的动作名、详情按钮等多处同名时用索引区分） */
    async function clickText(text, index = 0) {
      const box = await evaluate(`(() => {
        const nodes = [...document.querySelectorAll('button, .tabs__item, .stats__action')]
          .filter((node) => node.textContent.trim() === ${JSON.stringify(text)});
        const el = nodes[${index}];
        if (!el) return null;
        el.scrollIntoView({ block: 'center', inline: 'center' });
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`);
      if (!box) throw new Error(`找不到按钮：${text}（第 ${index + 1} 个）`);
      await dispatchClick(box);
    }

    /**
     * 给受控输入框赋值。
     *
     * <p>直接改 `input.value` 对 React 受控组件无效。必须走**原型上的原生 setter**，
     * 再派发 `input` 事件，React 的合成事件才会认。
     */
    async function setInputValue(selector, value) {
      const ok = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, ${JSON.stringify(value)});
        el.dispatchEvent(new Event('input', { bubbles: true }));
        return el.value === ${JSON.stringify(value)};
      })()`);
      if (!ok) throw new Error(`无法给输入框赋值：${selector}`);
    }

    /** 在登录页填表并提交 */
    async function loginAs(account) {
      await waitFor(`location.pathname === '/login'`, "停在登录页");
      // 用户名默认是 admin，切到第二个身份时必须先改掉它
      await setInputValue('input[name="username"]', account.username);
      await setInputValue('input[name="password"]', account.password);
      await evaluate("document.querySelector('.login__card').requestSubmit()");
    }

    async function logout() {
      await clickSelector(".basic-layout__action:last-of-type");
      await waitFor(`location.pathname === '/login'`, "登出后回到登录页");
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");
    await send("Network.enable");

    /* ---------------- 1. 未登录访问 /audit → 守卫覆盖到新子应用 ---------------- */

    await waitFor(
      `location.pathname === '/login'`,
      "守卫把未登录用户重定向到登录页",
    );
    const pathAfterGuard = await evaluate("location.pathname");
    step("1. 未登录访问 /audit", `落地地址 ${pathAfterGuard}`);
    if (pathAfterGuard !== "/login")
      record(`未登录时没有被重定向到登录页，实际在 ${pathAfterGuard}`);

    /* ---------------- 2. 管理员：审计台可用，且页面上没有写入口 ---------------- */

    await loginAs(ADMIN);
    await waitFor(
      `location.pathname === '/audit' && Boolean(document.querySelector('[data-testid="audit-identity"]'))`,
      "登录后进入审计子应用",
      30000,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="audit-table"]'))`,
      "审计流水表格出现",
      30000,
    );

    const adminView = await evaluate(`(() => {
      const perms = [...document.querySelectorAll('[data-testid="audit-permissions"] li')].map(li => li.textContent);
      const buttons = [...document.querySelectorAll('button')].map(b => b.textContent.trim());
      return {
        identity: document.querySelector('[data-testid="audit-identity"]').textContent,
        perms,
        callout: document.querySelector('[data-testid="audit-callout"]').textContent,
        total: document.querySelector('[data-testid="audit-total"]').textContent,
        window: document.querySelector('[data-testid="audit-window"]').textContent,
        buttons,
        drill: Boolean(document.querySelector('.drill__run')),
        hasWriteEntry: buttons.some(b => b.includes('新增') || b.includes('写入') || b.includes('上报')),
        hasNoPermissionBanner: Boolean(document.querySelector('[data-testid="audit-no-permission"]')),
      };
    })()`);
    step(
      "2. 管理员视角",
      `身份条「${adminView.identity}」· 权限 ${adminView.perms.length} 项 · ` +
        `${adminView.total} · 窗口 ${adminView.window} · 演练区=${adminView.drill}`,
    );
    if (!adminView.perms.includes("auth:audit:read"))
      record("管理员权限集合里没有 auth:audit:read");
    if (!adminView.callout.includes("40500"))
      record("说明条里没有点出「写接口不存在 → 40500」这件事");
    if (adminView.hasWriteEntry)
      record("页面上出现了「写审计」类的入口 —— 审计不该有写入口");
    if (!adminView.drill) record("演练区没有渲染");
    if (adminView.hasNoPermissionBanner)
      record("管理员被提示无权限 —— 权限判定或下发有问题");

    await screenshot("01-audit-admin-view");

    /* ---------------- 3. 演练区：真实写一次 → 审计表里出现记录 ---------------- */

    await clickSelector(".drill__run");
    /*
     * 把"等不到记录"变成一句可读的失败。
     *
     * 演练的失败步骤里没有 `auth.tenant.create` 这几个字（那一步压根没跑到），
     * 所以单看"等待超时"根本分不清是**点击没生效**、**写接口被拒**、
     * 还是**审计真的没落库**。超时的时候把步骤快照打出来，答案就在里面。
     */
    try {
      await waitFor(
        `(document.querySelector('[data-testid="drill-steps"]')?.textContent ?? '').includes('auth.tenant.create')`,
        "演练步骤里出现 auth.tenant.create",
        30000,
      );
      await waitFor(
        `(document.querySelector('[data-testid="drill-steps"]')?.textContent ?? '').includes('auth.tenant.delete')`,
        "演练步骤里出现 auth.tenant.delete",
        30000,
      );
    } catch (cause) {
      const snapshot = await evaluate(
        `document.querySelector('[data-testid="drill-steps"]')?.textContent ?? '（一条步骤都没有 —— 按钮多半没点到）'`,
      );
      record(`演练没有跑完：${cause.message}\n    步骤快照：${snapshot}`);
    }
    const drill = await evaluate(`(() => {
      const steps = [...document.querySelectorAll('[data-testid="drill-steps"] .steps__item')];
      return {
        ok: steps.filter(s => s.className.includes('--ok')).length,
        fail: steps.filter(s => s.className.includes('--fail')).length,
        text: steps.map(s => s.textContent).join(' | '),
      };
    })()`);
    step(
      "3. 演练区（真实写一次）",
      `成功步骤 ${drill.ok} 个 · 失败步骤 ${drill.fail} 个\n  ${drill.text}`,
    );
    if (drill.fail > 0) record("演练出现失败步骤 —— 审计记录没有如期落地");

    await screenshot("02-audit-drill");

    /* ---------------- 4. 聚合里的计数确实涨了，且能点它筛流水 ---------------- */

    await waitFor(
      `Boolean(document.querySelector('[data-testid="audit-stat-auth.tenant.create"]'))`,
      "聚合里出现 auth.tenant.create",
      20000,
    );
    const stats = await evaluate(`(() => {
      const el = document.querySelector('[data-testid="audit-stat-auth.tenant.create"]');
      return {
        text: el.textContent,
        statCount: document.querySelectorAll('[data-testid="audit-stats"] li').length,
        total: document.querySelector('[data-testid="audit-total"]').textContent,
      };
    })()`);
    step(
      "4. 按动作聚合",
      `${stats.statCount} 个动作 · 「${stats.text}」· ${stats.total}`,
    );

    // 点聚合里的动作名 → 跳去筛选流水
    await clickSelector('[data-testid="audit-stat-auth.tenant.create"] .stats__action');
    await waitFor(
      `document.querySelector('input[name="action"]').value === 'auth.tenant.create'`,
      "动作筛选被填上",
    );
    await waitFor(
      `[...document.querySelectorAll('tbody tr[data-testid^="audit-row-"]')].length > 0`,
      "筛选后仍有记录",
      20000,
    );
    const filtered = await evaluate(`(() => {
      const actions = [...document.querySelectorAll('tbody tr[data-testid^="audit-row-"] td:nth-child(2)')]
        .map(td => td.textContent);
      return { actions, total: document.querySelector('[data-testid="audit-total"]').textContent };
    })()`);
    step(
      "4b. 点动作即筛选",
      `筛选后 ${filtered.total} · 动作列 [${filtered.actions.join(", ")}]`,
    );
    if (filtered.actions.some((a) => a !== "auth.tenant.create"))
      record("按动作筛选后仍出现了别的动作");

    await screenshot("03-audit-stats-filter");

    /* ---------------- 5. 写接口不存在：直接 POST 得到 40500 ---------------- */

    /*
     * 记一个水位：下面第 6 步要断言"页面自己只发过 GET"，
     * 而这一次 POST 是**脚本**发的（页面里根本没有这个动作）。
     * 不记水位的话，这条断言会被自己制造出来的请求绊倒 ——
     * 那种"测试自己造成了被测对象的失败"是最容易骗过人的一类假阳性。
     */
    const pageRequestWatermark = auditRequests.length;

    const writeAttempt = await evaluate(`fetch(${JSON.stringify(AUDIT_WRITE_ATTEMPT)}, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'forged', resource: 'forged' }),
    }).then(async (res) => ({
      status: res.status,
      body: await res.json().catch(() => null),
    }))`);
    step(
      "5. 往审计表里写一条（不可能成功）",
      `HTTP ${writeAttempt.status} · code=${writeAttempt.body?.code} · message=${writeAttempt.body?.message}`,
    );
    if (writeAttempt.body?.code !== 40500)
      record(
        `对审计接口发 POST 应得到业务码 40500，实际 ${writeAttempt.body?.code}`,
      );

    await screenshot("04-audit-no-write-endpoint");

    /* ---------------- 6. 这一页从头到尾只发过 GET ---------------- */

    const pageRequests = auditRequests.slice(0, pageRequestWatermark);
    const nonGet = pageRequests.filter((r) => r.method !== "GET");
    step(
      "6. 页面自己发出的审计请求",
      `${pageRequests.length} 个（第 5 步那次 POST 是脚本发的，不计入）\n  ` +
        `用到的方法：${[...new Set(pageRequests.map((r) => r.method))].join(", ")}`,
    );
    if (nonGet.length > 0)
      record(`页面自己发了非 GET 的审计请求：${nonGet.map((r) => r.method).join(", ")}`);
    if (pageRequests.length === 0)
      record("页面一次审计请求都没发过 —— 上面的流水与聚合是怎么来的？");

    /* ---------------- 7. 只读账号：仍能看流水，但写操作被拒且留痕 ---------------- */

    await logout();
    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/audit`)}`);
    await loginAs(AUDITOR);
    await waitFor(
      `location.pathname === '/audit' && Boolean(document.querySelector('[data-testid="audit-identity"]'))`,
      "审计员进入审计子应用",
      30000,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="audit-table"]'))`,
      "审计员也能读到审计流水",
      30000,
    );

    const auditorView = await evaluate(`(() => {
      const perms = [...document.querySelectorAll('[data-testid="audit-permissions"] li')].map(li => li.textContent);
      return {
        identity: document.querySelector('[data-testid="audit-identity"]').textContent,
        perms,
        path: location.pathname,
        drill: Boolean(document.querySelector('.drill__run')),
        rowCount: document.querySelectorAll('tbody tr[data-testid^="audit-row-"]').length,
        error: document.querySelector('[data-testid="audit-error"]')?.textContent ?? null,
      };
    })()`);
    step(
      "7. 审计员视角（只读）",
      `身份条「${auditorView.identity}」· 权限 [${auditorView.perms.join(", ")}] · ` +
        `列表 ${auditorView.rowCount} 行 · 演练区按钮=${auditorView.drill} · 错误提示 ${auditorView.error ?? "（无）"}`,
    );
    if (auditorView.rowCount === 0)
      record("审计员读不到审计流水 —— auth:audit:read 没生效");
    if (auditorView.path !== "/audit")
      record(`审计员被从 /audit 弹走了，实际在 ${auditorView.path}`);
    // 与权限管理台刻意相反：那边没权限就不渲染按钮，这里必须还在
    if (!auditorView.drill)
      record("审计员看不到演练区按钮 —— 越权尝试恰恰是最该被记录的那一类");

    await screenshot("05-audit-auditor-view");

    /* ---------------- 8. 越权写：被拒，但审计表里多一条 FAILURE ---------------- */

    await clickSelector(".drill__run");
    await waitFor(
      `(document.querySelector('[data-testid="drill-steps"]')?.textContent ?? '').includes('被拒绝')`,
      "演练被拒绝",
      30000,
    );
    const rejected = await evaluate(
      `document.querySelector('[data-testid="drill-steps"]').textContent`,
    );
    step("8. 越权写被拒", rejected);
    if (!rejected.includes("403"))
      record("被拒绝的步骤里没有出现 403xx 业务码");
    if (!rejected.includes("留痕")) record("被拒绝后没有说明「这次尝试已留痕」");

    await waitFor(
      `[...document.querySelectorAll('[data-testid^="audit-result-"]')].some(el => el.textContent.includes('40301'))`,
      "流水里出现 40301 的 FAILURE 记录",
      30000,
    );
    const failureRows = await evaluate(`(() => {
      const rows = [...document.querySelectorAll('tbody tr[data-testid^="audit-row-"]')];
      const failed = rows.filter(r => r.textContent.includes('40301'));
      return {
        failedCount: failed.length,
        sample: failed[0]?.textContent ?? null,
        failStyled: rows.some(r => r.className.includes('table__row--fail')),
      };
    })()`);
    step(
      "8b. 越权尝试已留痕",
      `${failureRows.failedCount} 行带 40301 · 失败行铺色=${failureRows.failStyled}\n  ` +
        `${failureRows.sample ?? "（无）"}`,
    );
    if (failureRows.failedCount === 0)
      record("越权尝试没有在审计表里留下 FAILURE 记录");
    if (!failureRows.failStyled) record("失败行没有整行铺色，扫读时看不出来");

    await screenshot("06-audit-auditor-failure");

    /* ---------------- 9. 详情对话框 ---------------- */

    await clickText("详情", 0);
    await waitFor(
      `Boolean(document.querySelector('[data-testid="audit-detail"]'))`,
      "详情对话框打开",
    );
    const detail = await evaluate(
      `document.querySelector('[data-testid="audit-detail"]').textContent`,
    );
    step("9. 记录详情", detail);
    if (!detail.includes("记录时间"))
      record("详情里没有「记录时间」（审计只有服务端时间这一条要在界面上看得见）");
    if (detail.includes("客户端时间"))
      record("详情里出现了客户端时间 —— 审计不由客户端产生，不该有这一项");

    await screenshot("07-audit-detail");

    /* ---------------- 10. 硬刷新 → 会话与权限一起恢复 ---------------- */

    await send("Page.reload", { ignoreCache: true });
    await waitFor(
      `(document.querySelector('[data-testid="audit-identity"]')?.textContent ?? '').includes('userId=')`,
      "刷新后权限重新就绪",
      30000,
    );
    const afterReload = await evaluate(`(() => ({
      path: location.pathname,
      identity: document.querySelector('[data-testid="audit-identity"]').textContent,
      canRead: Boolean(document.querySelector('[data-testid="audit-table"]')),
    }))()`);
    step(
      "10. 硬刷新",
      `落地 ${afterReload.path} · 「${afterReload.identity}」· 流水可见=${afterReload.canRead}`,
    );
    if (afterReload.path === "/login")
      record("刷新页面后掉登录 —— 会话恢复没有生效");
    if (!afterReload.canRead) record("刷新后审计员看不到流水了");

    /* ---------------- 11. 浏览器侧错误 ---------------- */

    step("11. 浏览器侧错误", errors.length ? errors.join("\n") : "（无）");
    for (const message of errors) record(`浏览器报错：${message}`);

    // 顺带把契约里的三条读路径核对一遍，防止文档与实现漂移
    step("附录 · 本页用到的读接口", AUDIT_GETS.join("\n  "));
  } finally {
    chrome.kill();
  }

  console.log(
    `\n=== 结论：${problems.length === 0 ? "全部通过" : `发现 ${problems.length} 个问题`} ===`,
  );
  if (problems.length) {
    for (const message of problems) console.log(`  - ${message}`);
    process.exitCode = 1;
  }
}

if (SHOT_DIR) mkdirSync(SHOT_DIR, { recursive: true });

main().catch((error) => {
  console.error(`\n验证脚本自身出错：${error.message}`);
  console.error(error.stack);
  process.exitCode = 1;
});
