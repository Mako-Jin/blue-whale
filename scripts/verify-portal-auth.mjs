/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「基座登录态」这条链路。
 *
 * 它验证的东西，单元测试一条都测不到 —— 单测里会话是替身、路由是 MemoryRouter：
 *   1. **真实守卫跳转**：未登录访问受保护路由，浏览器地址栏真的变成了 /login；
 *   2. **真实登录**：口令送到后端（h2 profile 种子管理员）并拿到真令牌；
 *   3. **刷新页面会话恢复**：内存里的访问令牌已经没了，靠持久化的刷新令牌续期 ——
 *      这是"权限不在令牌里"（见 docs/decisions/0018）在前端的必然后果；
 *   4. **权限真的下发到了子应用**：子应用右下角的调试标记里 roles / perm 不为 0；
 *   5. **登出真的清干净**：本地存储里的会话被移除，再访问受保护路由又被弹回登录页。
 *
 * 前置（三个进程都要在跑）：
 *   cd server && JAVA_HOME=<jdk21> mvn -pl sts-app/sts-app-server -am spring-boot:run   # :8080（h2，默认开种子）
 *   pnpm --filter platform-portal dev        # :3000
 *   pnpm --filter platform-log-console dev   # :3200（用来观察权限下发）
 *
 * 用法：
 *   node scripts/verify-portal-auth.mjs                 # 只验证
 *   node scripts/verify-portal-auth.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { ADMIN as ACCOUNT } from "./lib/seed-credentials.mjs";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9337;
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 960 };
const SHOT_DIR = process.argv[2] ?? null;

/** h2 profile 的种子管理员，从 `lib/seed-credentials.mjs` 取 —— **不要再在脚本里抄口令**。
 *  真值来源是 `application-h2.yml`，可用 `BW_SEED_ADMIN_PASSWORD` 覆盖。见 `docs/decisions/0029`。 */

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
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-portal-auth-"));
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
      `${BASE_URL}/log-console`,
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

    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && waiting.has(message.id)) {
        const settle = waiting.get(message.id);
        waiting.delete(message.id);
        /*
         * 协议层的错误（`message.error`）也要交回调用方，而不是丢掉 ——
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
     * （`Execution context was destroyed`），而不是结果体。登录成功的一瞬间、
     * 守卫换地址的一瞬间，都会集中踩到这一下 —— 重试几次就好，
     * 而直接抛错会让脚本在断言之前就"自身出错"，看起来像脚本写坏了。
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

    /** 用真实鼠标点一下元素中心（用于聚焦输入框、点按钮） */
    async function clickElement(selector) {
      const box = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`);
      if (!box) throw new Error(`找不到元素：${selector}`);
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
      await send("Input.dispatchMouseEvent", {
        type: "mouseReleased",
        ...base,
      });
    }

    async function typeText(text) {
      await send("Input.insertText", { text });
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /* ---------------- 1. 未登录访问受保护路由 → 被守卫弹到登录页 ---------------- */

    await waitFor(
      `location.pathname === '/login'`,
      "守卫把未登录用户重定向到登录页",
    );
    const pathAfterGuard = await evaluate("location.pathname");
    const pendingText = await evaluate(
      "document.querySelector('.require-auth__text')?.textContent ?? ''",
    );
    step(
      "1. 未登录访问 /log-console",
      `落地地址 ${pathAfterGuard} · 受保护内容未渲染 · 恢复提示「${pendingText || "（已结束）"}」`,
    );
    if (pathAfterGuard !== "/login")
      record(`未登录时没有被重定向到登录页，实际在 ${pathAfterGuard}`);
    const hasNav = await evaluate(
      "Boolean(document.querySelector('.basic-layout__nav'))",
    );
    if (hasNav)
      record("未登录状态下仍然渲染了导航栏（登录页应独立于 BasicLayout）");

    await screenshot("01-login-page");

    /* ---------------- 2. 登录表单：默认值与提示 ---------------- */

    const formState = await evaluate(`(() => {
      const input = (name) => document.querySelector('input[name="' + name + '"]');
      return {
        tenantCode: input('tenantCode')?.value ?? null,
        username: input('username')?.value ?? null,
        passwordType: input('password')?.type ?? null,
        hint: document.querySelector('.login__hint')?.textContent?.trim() ?? '',
      };
    })()`);
    step(
      "2. 登录表单",
      `租户=${formState.tenantCode} · 用户名=${formState.username} · ` +
        `口令框类型=${formState.passwordType} · 提示「${formState.hint}」`,
    );
    if (
      formState.tenantCode !== ACCOUNT.tenantCode ||
      formState.username !== ACCOUNT.username
    ) {
      record("登录表单的默认租户/用户名与种子数据不一致，脚本会填错账号");
    }
    if (formState.passwordType !== "password")
      record("口令框不是 password 类型，输入会被明文显示");

    /* ---------------- 3. 真实登录（口令送到后端） ---------------- */

    await clickElement('input[name="password"]');
    await typeText(ACCOUNT.password);
    const typedLength = await evaluate(
      "document.querySelector('input[name=\"password\"]').value.length",
    );
    if (typedLength !== ACCOUNT.password.length) {
      record(
        `口令没有被完整输入（期望 ${ACCOUNT.password.length} 位，实际 ${typedLength} 位）`,
      );
    }

    await evaluate("document.querySelector('.login__card').requestSubmit()");

    await waitFor(
      `location.pathname === '/log-console'`,
      "登录成功后回到原本想去的页面",
    );
    const afterLogin = await evaluate(`(() => {
      const raw = localStorage.getItem('bw.session');
      return {
        path: location.pathname,
        persisted: raw ? Object.keys(JSON.parse(raw)).join(',') : null,
        user: document.querySelector('.basic-layout__user')?.textContent ?? '',
      };
    })()`);
    step(
      "3. 登录",
      `落地地址 ${afterLogin.path} · 头部身份「${afterLogin.user}」 · ` +
        `localStorage 里持久化了 ${afterLogin.persisted ?? "（无）"}`,
    );
    if (afterLogin.path !== "/log-console")
      record(`登录后没有回到原定页面，实际在 ${afterLogin.path}`);
    if (!afterLogin.persisted)
      record("登录后没有持久化刷新令牌，刷新页面会掉登录");
    if (afterLogin.persisted && afterLogin.persisted.includes("accessToken")) {
      record("持久化内容里出现了 accessToken —— 访问令牌只应放内存");
    }

    await screenshot("02-logged-in");

    /* ---------------- 4. 权限经全局状态下发到子应用 ---------------- */

    await waitFor(
      `document.getElementById('micro-host-indicator')?.textContent?.includes('userId=')`,
      "子应用挂载并渲染出全局状态标记",
      30000,
    );
    const indicator = await evaluate(
      "document.getElementById('micro-host-indicator')?.textContent ?? ''",
    );
    const permCount = Number((indicator.match(/perm=(\d+)/) ?? [])[1] ?? "0");
    const roleCount = Number((indicator.match(/roles=(\d+)/) ?? [])[1] ?? "0");
    step(
      "4. 子应用侧收到的全局状态",
      `${indicator}（角色 ${roleCount} 个 · 权限 ${permCount} 项）`,
    );
    if (indicator.includes("(未登录)"))
      record("子应用收到的仍是未登录状态 —— 全局状态没跟着会话走");
    if (roleCount === 0) record("子应用收到的角色数为 0 —— 角色没有下发");
    if (permCount === 0) record("子应用收到的权限数为 0 —— 权限没有下发");

    await screenshot("03-micro-app-with-permissions");

    /* ---------------- 5. 访问登录页时已登录应被送回首页 ---------------- */

    await evaluate(
      "history.pushState({}, '', '/login'); window.dispatchEvent(new PopStateEvent('popstate'))",
    );
    await waitFor(`location.pathname === '/'`, "已登录访问 /login 被送回首页");
    step(
      "5. 已登录再访问 /login",
      `落地地址 ${await evaluate("location.pathname")}（应不是 /login）`,
    );

    /* ---------------- 6. 刷新页面 → 靠刷新令牌恢复会话 ---------------- */

    const beforeReloadPath = `${BASE_URL}/log-console`;
    await evaluate(`location.href = ${JSON.stringify(beforeReloadPath)}`);
    await waitFor(`location.pathname === '/log-console'`, "回到受保护路由");
    await send("Page.reload", { ignoreCache: true });

    // 刷新后如果没有恢复会话，会被守卫弹到 /login —— 这正是本步要区分的结果
    await waitFor(
      `location.pathname === '/log-console' && Boolean(document.querySelector('.basic-layout__user'))`,
      "刷新后仍在受保护页面（会话已恢复）",
    );
    const afterReload = await evaluate(`(() => ({
      path: location.pathname,
      user: document.querySelector('.basic-layout__user')?.textContent ?? '',
    }))()`);
    step(
      "6. 刷新页面",
      `落地地址 ${afterReload.path} · 头部身份「${afterReload.user}」（内存里的访问令牌已经没了，靠刷新令牌续期）`,
    );
    if (afterReload.path === "/login")
      record("刷新页面后掉登录 —— 会话恢复没有生效");

    await waitFor(
      `(document.getElementById('micro-host-indicator')?.textContent ?? '').includes('userId=')`,
      "刷新后子应用重新收到全局状态",
      30000,
    );
    const indicatorAfterReload = await evaluate(
      "document.getElementById('micro-host-indicator')?.textContent ?? ''",
    );
    if (indicatorAfterReload.includes("(未登录)")) {
      record("刷新后子应用收到的是未登录状态 —— 恢复顺序有问题");
    }

    /* ---------------- 7. 登出 ---------------- */

    await clickElement(".basic-layout__action:last-of-type");
    await waitFor(`location.pathname === '/login'`, "登出后被弹回登录页");
    const afterLogout = await evaluate(`(() => ({
      path: location.pathname,
      persisted: localStorage.getItem('bw.session'),
      nav: Boolean(document.querySelector('.basic-layout__nav')),
    }))()`);
    step(
      "7. 登出",
      `落地地址 ${afterLogout.path} · localStorage 残留=${afterLogout.persisted ?? "（已清空）"} · ` +
        `导航栏=${afterLogout.nav ? "仍在" : "已移除"}`,
    );
    if (afterLogout.persisted !== null)
      record("登出后 localStorage 里仍留有会话");
    if (afterLogout.nav) record("登出后仍渲染导航栏");

    await screenshot("04-after-logout");

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
