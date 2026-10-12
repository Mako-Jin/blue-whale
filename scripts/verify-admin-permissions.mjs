/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「按钮级权限」这条链路。
 *
 * 它验证的东西，单元测试一条都测不到 —— 单测里权限集合是直接塞进去的、
 * 请求是替身、子应用也没有真正被基座挂载：
 *   1. **权限真的从基座流到了子应用**：子应用页面上的权限集合与后端给的一致；
 *   2. **两种身份在同一段代码下产生不同界面**：管理员有全部操作入口，审计员一个都没有；
 *   3. **写操作真的通、也真的能撤销**：新建一个用户 → 出现在列表 → 删除它；
 *   4. **刷新页面后权限随会话一起恢复**（"权限不在令牌里"的必然后果，见 docs/decisions/0018）；
 *   5. **403 不等于登录失效**：审计员的读请求正常返回，页面停在原处而不是被弹去登录页。
 *
 * 前置（四个进程都要在跑）：
 *   cd server && java -jar sts-app/sts-app-server/target/sts-app-server-0.0.1-SNAPSHOT.jar   # :8080（h2，默认开两个种子账号）
 *   pnpm --filter platform-portal dev        # :3000
 *   pnpm --filter platform-admin dev         # :3500
 *
 * 用法：
 *   node scripts/verify-admin-permissions.mjs                 # 只验证
 *   node scripts/verify-admin-permissions.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9338;
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 960 };
const SHOT_DIR = process.argv[2] ?? null;

/** 两个种子账号（见 sts-server-auth 的 DefaultAdminSeeder 与 AuthProperties）。
 *  h2 profile 每次启动自动种；mysql / dameng 是真实库、不自动种，
 *  需显式开一次种子（`--bluewhale.auth.seed-admin.enabled=true`，见 application-mysql.yml）。 */
const ADMIN = {
  tenantCode: "default",
  username: "admin",
  password: "Admin@12345",
};
const AUDITOR = {
  tenantCode: "default",
  username: "auditor",
  password: "Auditor@12345",
};

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
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-admin-perm-"));
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
      // 直接访问受保护的新子应用路由：未登录时应被基座守卫弹到登录页
      `${BASE_URL}/admin`,
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

    async function clickSelector(selector) {
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

    /** 按可见文本点击（子应用的按钮没有稳定选择器时用） */
    async function clickText(text, scope = "document") {
      const box = await evaluate(`(() => {
        const root = ${scope};
        const el = [...root.querySelectorAll('button, .tabs__item')]
          .find((node) => node.textContent.trim() === ${JSON.stringify(text)});
        if (!el) return null;
        const r = el.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      })()`);
      if (!box) throw new Error(`找不到按钮：${text}`);
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

    /**
     * 给受控输入框赋值。
     *
     * <p>直接改 `input.value` 对 React 受控组件无效（React 记着自己的值，
     * 下一次渲染就把 DOM 改回去）。必须走**原型上的原生 setter**，
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

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /* ---------------- 1. 未登录访问 /admin → 守卫覆盖到新子应用 ---------------- */

    await waitFor(
      `location.pathname === '/login'`,
      "守卫把未登录用户重定向到登录页",
    );
    const pathAfterGuard = await evaluate("location.pathname");
    step("1. 未登录访问 /admin", `落地地址 ${pathAfterGuard}`);
    if (pathAfterGuard !== "/login")
      record(`未登录时没有被重定向到登录页，实际在 ${pathAfterGuard}`);

    /* ---------------- 2. 管理员身份：全部操作入口 + 真实增删 ---------------- */

    await loginAs(ADMIN);
    await waitFor(
      `location.pathname === '/admin' && Boolean(document.querySelector('[data-testid="admin-identity"]'))`,
      "登录后进入权限管理子应用",
      30000,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-row-admin"]'))`,
      "用户列表加载出种子管理员",
      30000,
    );

    const adminView = await evaluate(`(() => {
      const perms = [...document.querySelectorAll('[data-testid="admin-permissions"] li')].map(li => li.textContent);
      return {
        identity: document.querySelector('[data-testid="admin-identity"]').textContent,
        perms,
        create: Boolean(document.querySelector('[data-permission="auth:user:create"]')),
        update: Boolean(document.querySelector('[data-permission="auth:user:update"]')),
        remove: Boolean(document.querySelector('[data-permission="auth:user:delete"]')),
        rowCount: document.querySelectorAll('tbody tr[data-testid^="user-row-"]').length,
      };
    })()`);
    step(
      "2. 管理员视角",
      `身份条「${adminView.identity}」· 权限 ${adminView.perms.length} 项 · ` +
        `新建=${adminView.create} 编辑=${adminView.update} 删除=${adminView.remove} · 列表 ${adminView.rowCount} 行`,
    );
    if (!adminView.create)
      record("管理员看不到「新建用户」按钮 —— 权限下发或判定有问题");
    if (!adminView.update) record("管理员看不到「编辑」按钮");
    if (!adminView.remove) record("管理员看不到「删除」按钮");
    // 23 项 = 用户 4 + 租户 4 + 资源 4 + 角色 4 + 用户组 4 + 审计读 1 + 日志读 1 + 事件读 1
    // （auth:audit:read 是 0019 加的；auth:resource:* 是资源管理接口加的；
    //   auth:user_group:* 是用户组管理接口加的；log:client:read 与 event:client:read
    //   是 0026 加的 —— 本切片之前，日志与事件的查询接口**完全没有鉴权**，
    //   因此当时"管理员有多少项权限"这件事和它们无关）
    if (adminView.perms.length !== 23)
      record(`管理员权限项应为 23，实际 ${adminView.perms.length}`);
    if (!adminView.perms.includes("auth:user:create"))
      record("管理员权限集合里没有 auth:user:create");

    await screenshot("05-admin-can-write");

    /* -------- 3. 真实写操作：新建一个用户 → 出现在列表 → 再删掉 -------- */

    // 用户名带时间戳：脚本可重复运行，不会撞上"登录名已存在"
    const tempUser = `e2e${String(Date.now()).slice(-6)}`;
    await clickSelector('[data-permission="auth:user:create"]');
    await waitFor(
      `Boolean(document.querySelector('#user-form'))`,
      "新建用户对话框打开",
    );
    await setInputValue('#user-form input[name="username"]', tempUser);
    await setInputValue(
      '#user-form input[name="displayName"]',
      "端到端验证用户",
    );
    await setInputValue('#user-form input[name="password"]', "E2ePass@12345");
    await clickSelector(".users__submit");
    await waitFor(
      `(document.querySelector('[data-testid="users-notice"]')?.textContent ?? '').includes('已新建')`,
      "新建成功提示出现",
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-row-${tempUser}"]'))`,
      "新用户在列表里出现",
      20000,
    );

    await clickSelector(
      `[data-testid="user-row-${tempUser}"] [data-permission="auth:user:delete"]`,
    );
    await waitFor(
      `Boolean(document.querySelector('.users__remove-confirm'))`,
      "删除确认框打开",
    );
    await clickSelector(".users__remove-confirm");
    await waitFor(
      `(document.querySelector('[data-testid="users-notice"]')?.textContent ?? '').includes('已删除')`,
      "删除成功提示出现",
    );
    await waitFor(
      `!document.querySelector('[data-testid="user-row-${tempUser}"]')`,
      "被删用户从列表消失",
    );
    step(
      "3. 真实写操作",
      `新建「${tempUser}」→ 出现在列表 → 删除 → 从列表消失（写权限用的确实是自己的令牌）`,
    );

    /* -------- 4. 租户页各自独立判定（同一个子应用，另一组权限标识） -------- */

    await clickText("租户");
    await waitFor(
      `Boolean(document.querySelector('[data-permission="auth:tenant:create"]'))`,
      "租户页的新建按钮出现",
    );
    step(
      "4. 租户页",
      "管理员在租户页同样看到「新建租户」（权限标识换成 auth:tenant:create）",
    );

    /* ---------------- 5. 登出 → 审计员身份（只读） ---------------- */

    await clickSelector(".basic-layout__action:last-of-type");
    await waitFor(`location.pathname === '/login'`, "登出后回到登录页");
    await evaluate(`location.href = ${JSON.stringify(`${BASE_URL}/admin`)}`);
    await loginAs(AUDITOR);
    await waitFor(
      `location.pathname === '/admin' && Boolean(document.querySelector('[data-testid="admin-identity"]'))`,
      "审计员进入权限管理子应用",
      30000,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-row-admin"]'))`,
      "审计员也能读到用户列表",
      30000,
    );

    const auditorView = await evaluate(`(() => {
      const perms = [...document.querySelectorAll('[data-testid="admin-permissions"] li')].map(li => li.textContent);
      return {
        identity: document.querySelector('[data-testid="admin-identity"]').textContent,
        perms,
        path: location.pathname,
        create: Boolean(document.querySelector('[data-permission="auth:user:create"]')),
        update: Boolean(document.querySelector('[data-permission="auth:user:update"]')),
        remove: Boolean(document.querySelector('[data-permission="auth:user:delete"]')),
        error: document.querySelector('[data-testid="users-error"]')?.textContent ?? null,
        rowCount: document.querySelectorAll('tbody tr[data-testid^="user-row-"]').length,
      };
    })()`);
    step(
      "5. 审计员视角（只读）",
      `身份条「${auditorView.identity}」· 权限 [${auditorView.perms.join(", ")}] · ` +
        `新建=${auditorView.create} 编辑=${auditorView.update} 删除=${auditorView.remove} · ` +
        `列表 ${auditorView.rowCount} 行 · 错误提示 ${auditorView.error ?? "（无）"}`,
    );

    // 同一段代码、同一个页面，差别只来自权限集合
    if (auditorView.create)
      record("审计员看到了「新建用户」按钮 —— 按钮级权限没有生效");
    if (auditorView.update) record("审计员看到了「编辑」/「重置口令」按钮");
    if (auditorView.remove) record("审计员看到了「删除」按钮");
    if (auditorView.rowCount === 0)
      record("审计员读不到用户列表 —— 读权限也没生效，这一屏说明不了问题");
    // 3 项 = 用户读 + 租户读 + 审计读（审计读是 0019 加的，审计台要靠它）
    if (auditorView.perms.length !== 3)
      record(`审计员权限项应为 3，实际 ${auditorView.perms.length}`);
    // 403 不该被当成登录失效：页面必须还停在 /admin
    if (auditorView.path !== "/admin")
      record(`审计员被从 /admin 弹走了，实际在 ${auditorView.path}`);

    await screenshot("06-auditor-read-only");

    /* ---------------- 6. 硬刷新 → 权限随会话一起恢复 ---------------- */

    await send("Page.reload", { ignoreCache: true });
    await waitFor(
      `Boolean(document.querySelector('[data-testid="admin-identity"]'))`,
      "刷新后子应用重新挂载",
      30000,
    );
    await waitFor(
      `(document.querySelector('[data-testid="admin-identity"]')?.textContent ?? '').includes('userId=')`,
      "刷新后权限重新就绪",
      30000,
    );
    const afterReload = await evaluate(`(() => ({
      path: location.pathname,
      identity: document.querySelector('[data-testid="admin-identity"]').textContent,
      create: Boolean(document.querySelector('[data-permission="auth:user:create"]')),
    }))()`);
    step(
      "6. 硬刷新",
      `落地 ${afterReload.path} · 「${afterReload.identity}」· 新建按钮=${afterReload.create}`,
    );
    if (afterReload.path === "/login")
      record("刷新页面后掉登录 —— 会话恢复没有生效");
    // 内存里的访问令牌已经没了，权限是走刷新接口重新拿回来的
    if (afterReload.create)
      record("刷新后审计员看到了新建按钮 —— 恢复回来的权限集合不对");
    if (!afterReload.identity.includes("userId="))
      record("刷新后权限没有重新就绪");

    /* ---------------- 7. 登出清干净 ---------------- */

    await clickSelector(".basic-layout__action:last-of-type");
    await waitFor(`location.pathname === '/login'`, "登出后被弹回登录页");
    const afterLogout = await evaluate(`(() => ({
      persisted: localStorage.getItem('bw.session'),
      hasIdentity: Boolean(document.querySelector('[data-testid="admin-identity"]')),
    }))()`);
    step(
      "7. 登出",
      `localStorage 残留=${afterLogout.persisted ?? "（已清空）"}`,
    );
    if (afterLogout.persisted !== null)
      record("登出后 localStorage 里仍留有会话");
    if (afterLogout.hasIdentity)
      record("登出后子应用界面仍在（应随路由一起卸载）");

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
