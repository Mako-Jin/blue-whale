/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「菜单权限两步」。
 *
 * 单测把权限来源换成了替身、路由是 MemoryRouter，因此下面这些事一条都测不到：
 *   1. **导航栏真的按菜单码过滤**：管理员 5 个入口、审计员 2 个 —— 这是第一步（补 `MENU`/`PAGE`
 *      资源行 + 种子授权）到底有没有生效的唯一现场证据；
 *   2. **URL 直达真的被拦住**：地址栏敲一个没授权的路由，会被送回概览并说明原因 ——
 *      只过滤导航栏的话，地址栏就是后门（那是"假开关"）；
 *   3. **页面级码真的按页面生效**：审计员进权限管理后台时六个标签页都在
 *      （决策是"给审计员全部页面"），而它进不去日志台；
 *   4. **两道门是独立的**：菜单码决定"进不进得去"，读写权限决定"进去之后能做什么"。
 *
 * 前置（四个进程都要在跑）：
 *   cd server && JAVA_HOME=<jdk21> mvn -pl sts-app/sts-app-server -am spring-boot:run   # :8080（h2，默认开两个种子账号）
 *   pnpm --filter platform-portal dev         # :3000
 *   pnpm --filter platform-admin dev          # :3500
 *   pnpm --filter platform-audit-console dev  # :3600
 *
 * 用法：
 *   node scripts/verify-menu-permissions.mjs                 # 只验证
 *   node scripts/verify-menu-permissions.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { ADMIN, AUDITOR } from "./lib/seed-credentials.mjs";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9341;
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 960 };
const SHOT_DIR = process.argv[2] ?? null;

/**
 * 两个身份各自应当看到的**应用入口**。
 *
 * <p>这两行是本次决策的可执行形式：管理员是全部，审计员是「权限管理 + 审计日志」
 * （后者的六个页面码也全给了它）。改动种子授权时，这个数组要跟着改 ——
 * 否则脚本会开始"验证"一件与产品不符的事。
 */
const ADMIN_NAV = ["platform-log-console", "platform-event-console", "platform-environment", "platform-admin", "platform-audit-console"];
const AUDITOR_NAV = ["platform-admin", "platform-audit-console"];

/** 权限管理后台的六个标签页（顺序即声明顺序） */
const ADMIN_TABS = ["users", "tenants", "resources", "roles", "userGroups", "organizations"];

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

/** 比较"期望入口"与"实际入口"，返回人可读的差集描述 */
function diffNav(expected, actual) {
  const missing = expected.filter((name) => !actual.includes(name));
  const extra = actual.filter((name) => !expected.includes(name));
  if (missing.length === 0 && extra.length === 0) return null;
  return `缺少 [${missing.join(", ") || "-"}] · 多出 [${extra.join(", ") || "-"}]`;
}

async function main() {
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-menu-perms-"));
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
      // 直接访问受保护路由：未登录时应当被守卫弹到登录页
      `${BASE_URL}/admin`,
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) => r.json());
    const page = targets.find((t) => t.type === "page" && t.url.includes("127.0.0.1:3000"));
    if (!page) {
      throw new Error("未找到门户页面：" + JSON.stringify(targets.map((t) => t.url)));
    }

    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let seq = 0;
    const waiting = new Map();
    const errors = [];

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && waiting.has(message.id)) {
        const settle = waiting.get(message.id);
        waiting.delete(message.id);
        settle(message.error ? { protocolError: message.error } : message.result);
        return;
      }
      if (message.method === "Runtime.exceptionThrown") {
        const details = message.params.exceptionDetails;
        errors.push(details.exception?.description || details.text);
      }
      if (message.method === "Log.entryAdded" && message.params.entry.level === "error") {
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
     * （`Execution context was destroyed`）。本脚本里"换账号登录""URL 直达被弹回"
     * 两处都会集中踩到，直接抛错会让脚本在断言之前就"自身出错"。
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
      writeFileSync(join(SHOT_DIR, `${name}.png`), Buffer.from(shot.data, "base64"));
    }

    async function clickElement(selector) {
      const box = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`);
      if (!box) throw new Error(`找不到元素：${selector}`);
      const base = { x: Math.round(box.x), y: Math.round(box.y), button: "left", clickCount: 1 };
      await send("Input.dispatchMouseEvent", { type: "mouseMoved", ...base, button: "none" });
      await send("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
      await send("Input.dispatchMouseEvent", { type: "mouseReleased", ...base });
    }

    /**
     * 把受控输入框的值改成 `value`。
     *
     * <p>不能用 `Input.insertText`：那是**追加**，换账号时会拼出 `adminauditor`。
     * 也不能直接赋 `input.value = …`：React 的受控组件靠 value 的 setter 记录旧值，
     * 直接赋值会让它认为"外部改了但没触发 onChange"，下一次渲染又把它改回去。
     * 正解是拿原型上的原生 setter 写值、再手动派发 `input` 事件。
     */
    async function setInput(name, value) {
      await evaluate(`(() => {
        const input = document.querySelector('input[name="' + ${JSON.stringify(name)} + '"]');
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(input, ${JSON.stringify(value)});
        input.dispatchEvent(new Event('input', { bubbles: true }));
      })()`);
    }

    /** 登录：填表 → 提交 → 等到离开登录页 */
    async function signIn(account, label) {
      await waitFor(`location.pathname === '/login'`, `${label}：停在登录页`);
      await setInput("tenantCode", account.tenantCode);
      await setInput("username", account.username);
      await setInput("password", account.password);
      await evaluate("document.querySelector('.login__card').requestSubmit()");
      await waitFor(`location.pathname !== '/login'`, `${label}：登录成功`);
      // 等导航栏渲染出来（它由会话驱动，落点页可能还要恢复一拍）
      await waitFor(
        `Boolean(document.querySelector('[data-testid="portal-nav"]'))`,
        `${label}：导航栏渲染`,
      );
    }

    /** 当前导航栏上的应用入口（不含"概览"） */
    async function readNav() {
      return evaluate(`Array.from(
        document.querySelectorAll('[data-testid^="nav-"]')
      ).map((el) => el.getAttribute('data-testid').replace('nav-', ''))`);
    }

    /** 登出：点最后一个 action（登出按钮） */
    async function signOut() {
      await clickElement(".basic-layout__action:last-of-type");
      await waitFor(`location.pathname === '/login'`, "登出后回到登录页");
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /* ---------------- 1. 未登录：URL 直达被登录守卫拦住 ---------------- */

    await waitFor(`location.pathname === '/login'`, "未登录访问 /admin 被弹到登录页");
    step(
      "1. 未登录访问 /admin",
      `落地地址 ${await evaluate("location.pathname")}（应不是 /admin）`,
    );

    /* ---------------- 2. 管理员：导航栏应有 5 个入口 ---------------- */

    await signIn(ADMIN, "管理员");
    const adminNav = await readNav();
    step("2. 管理员登录后的导航栏", `${adminNav.length} 个入口：${adminNav.join(" · ")}`);
    const adminDiff = diffNav(ADMIN_NAV, adminNav);
    if (adminDiff) record(`管理员导航栏与期望不符：${adminDiff}`);
    await screenshot("01-admin-nav");

    /* ---------------- 3. 管理员：权限管理后台六个标签页都在 ---------------- */

    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/admin`)}`);
    await waitFor(`location.pathname === '/admin'`, "管理员进入 /admin");
    await waitFor(
      `document.querySelectorAll('[data-testid^="admin-tab-"]').length > 0`,
      "标签页渲染",
      30000,
    );
    const adminTabs = await evaluate(`Array.from(
      document.querySelectorAll('[data-testid^="admin-tab-"]')
    ).map((el) => el.getAttribute('data-testid').replace('admin-tab-', ''))`);
    step(
      "3. 管理员的权限管理标签页",
      `${adminTabs.length} 个：${adminTabs.join(" · ")}`,
    );
    const tabDiff = diffNav(ADMIN_TABS, adminTabs);
    if (tabDiff) record(`标签页与期望不符：${tabDiff}`);
    // 标签页顺序在界面上是有意义的（授权链路的顺序），因此额外钉住顺序
    if (adminTabs.join(",") !== ADMIN_TABS.join(",")) {
      record(`标签页顺序与 AuthMenus 的声明顺序不一致：${adminTabs.join(",")}`);
    }
    await screenshot("02-admin-tabs");

    /* ---------------- 4. 换账号：审计员导航栏只应有 2 个入口 ---------------- */

    await signOut();
    await signIn(AUDITOR, "审计员");
    const auditorNav = await readNav();
    step("4. 审计员登录后的导航栏", `${auditorNav.length} 个入口：${auditorNav.join(" · ")}`);
    const auditorDiff = diffNav(AUDITOR_NAV, auditorNav);
    if (auditorDiff) record(`审计员导航栏与期望不符：${auditorDiff}`);
    // 单独再点一次名：日志台是本次过滤里最典型的那一个
    if (auditorNav.includes("platform-log-console")) {
      record("审计员看到了「日志控制台」入口 —— 菜单码过滤没有生效");
    }
    await screenshot("03-auditor-nav");

    /* ---------------- 5. 审计员：URL 直达被拦住并说明原因 ---------------- */

    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/log-console`)}`);
    await waitFor(`location.pathname === '/'`, "审计员直达 /log-console 被送回概览", 30000);
    const denied = await evaluate(
      "document.querySelector('[data-testid=\"home-denied\"]')?.textContent ?? ''",
    );
    step("5. 审计员直达 /log-console", `落地地址 / · 提示「${denied.trim() || "（无）"}」`);
    if (!denied.includes("ui:log_console:view")) {
      record("被弹回后没有说明缺哪一个菜单码 —— 用户只会以为应用坏了");
    }
    // 概览页的入口卡片也要跟着过滤，否则这里会变成"点了就被弹回"的假开关集合
    const homeLinks = await evaluate(`Array.from(
      document.querySelectorAll('.home__link')
    ).map((el) => el.getAttribute('href'))`);
    step("5b. 审计员在概览页看到的入口", homeLinks.join(" · ") || "（无）");
    if (homeLinks.some((href) => String(href).includes("log-console"))) {
      record("概览页仍在列没权限的入口 —— 首页会变成假开关集合");
    }
    await screenshot("04-auditor-denied");

    /* ---------------- 6. 审计员：权限管理后台仍是六个标签页 ---------------- */

    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/admin`)}`);
    await waitFor(`location.pathname === '/admin'`, "审计员进入 /admin");
    await waitFor(
      `document.querySelectorAll('[data-testid^="admin-tab-"]').length > 0`,
      "标签页渲染",
      30000,
    );
    const auditorTabs = await evaluate(`Array.from(
      document.querySelectorAll('[data-testid^="admin-tab-"]')
    ).map((el) => el.getAttribute('data-testid').replace('admin-tab-', ''))`);
    step(
      "6. 审计员的权限管理标签页",
      `${auditorTabs.length} 个：${auditorTabs.join(" · ")}（决策是"页面码全给"）`,
    );
    if (auditorTabs.length !== ADMIN_TABS.length) {
      record(`审计员的标签页数与管理员不同（${auditorTabs.length} vs ${ADMIN_TABS.length}）`);
    }
    // 两道门独立的现场证据：进得去，但写按钮不该出现
    const createVisible = await evaluate(
      `Boolean(Array.from(document.querySelectorAll('.btn')).find((el) => el.textContent.trim() === '新建用户'))`,
    );
    step(
      "6b. 菜单码与读写权限是两层",
      `能进「用户」页=${auditorTabs.includes("users")} · 看得到「新建用户」按钮=${createVisible}`,
    );
    if (createVisible) record("审计员看到了「新建用户」—— 读写权限没有被尊重");
    await screenshot("05-auditor-admin-tabs");

    /* ---------------- 7. 审计员：审计台三块区域都在 ---------------- */

    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/audit`)}`);
    await waitFor(`location.pathname === '/audit'`, "审计员进入 /audit");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="audit-list-panel"]')) ||
       Boolean(document.querySelector('[data-testid="audit-empty"]'))`,
      "审计台渲染出结果",
      30000,
    );
    const auditState = await evaluate(`(() => ({
      flow: Boolean(document.querySelector('[data-testid="audit-list-panel"]')),
      stats: Boolean(document.querySelector('[data-testid="audit-stats-panel"]')),
      drill: Boolean(document.querySelector('[data-testid="drill-panel"]')),
      empty: Boolean(document.querySelector('[data-testid="audit-empty"]')),
    }))()`);
    step(
      "7. 审计台的三个区块",
      `流水=${auditState.flow} · 聚合=${auditState.stats} · 演练=${auditState.drill} · 空态=${auditState.empty}`,
    );
    if (!auditState.flow || !auditState.stats || !auditState.drill) {
      record("审计员没有看到审计台的全部三个区块 —— 页面码没有生效");
    }
    await screenshot("06-auditor-audit");

    /* ---------------- 8. 浏览器侧错误 ---------------- */

    step("8. 浏览器侧错误", errors.length ? errors.join("\n") : "（无）");
    for (const message of errors) record(`浏览器报错：${message}`);
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
