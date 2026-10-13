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

import { ADMIN, AUDITOR } from "./lib/seed-credentials.mjs";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9338;
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 960 };
const SHOT_DIR = process.argv[2] ?? null;

/** 两个种子账号从 `lib/seed-credentials.mjs` 取 —— **不要再在脚本里抄口令**。
 *  真值来源是 `application-h2.yml`，可用 `BW_SEED_ADMIN_PASSWORD` 等环境变量覆盖。
 *  见 `docs/decisions/0029` 与 `scripts/README.md` 的硬性约束。 */

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

    /* ---------------- 直调后端：核对"界面上看不出来的数据效果" ----------------

       <p>为什么脚本里要额外开一条 API 通道：组织页有几件事**在界面上看不见**。
       典型的是「移动一个节点之后，原父级的 `hasChild` 有没有跟着归零」——
       树的组装用的是 `parentId`，所以就算 `hasChild` 是陈旧的，界面看起来也完全正常。
       上个切片（后端）的用例正是靠这条断言抓出了一个真缺陷：移动时路径与层级改了、
       `parent_id` 没落库，于是"界面显示在原地、数据范围算在新位置"。

       因此这里的 API 调用**只用于读数与造场景**，不替代界面验证：
       界面负责"能不能做、做得对不对"，API 负责"落库之后到底是什么"。 */

    const API_BASE = "http://127.0.0.1:8080";
    let apiToken = null;

    async function apiFetch(path, options = {}) {
      if (!apiToken) {
        const response = await fetch(`${API_BASE}/api/auth/login`, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(ADMIN),
        });
        const envelope = await response.json();
        if (envelope.code !== 0) {
          throw new Error(`脚本内置登录失败：${envelope.message}`);
        }
        apiToken = envelope.data.accessToken;
      }

      const response = await fetch(`${API_BASE}${path}`, {
        method: options.method ?? "GET",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${apiToken}`,
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
      const envelope = await response.json().catch(() => ({}));
      /*
       * 业务失败返 HTTP 200、`code !== 0` —— 判据是 code 不是状态码。
       * 这是 `docs/decisions/0001` 定下的收口方式，脚本这边必须照同一个判据走，
       * 否则"被拒绝"会被读成"成功拿到了一份空数据"。
       */
      if (envelope.code !== 0) {
        throw new Error(
          `${options.method ?? "GET"} ${path} 失败：HTTP ${response.status} ` +
            `code=${envelope.code} ${envelope.message ?? ""}`,
        );
      }
      return envelope.data;
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
    // 45 项 = 27 项读写权限 + 18 条菜单码（5 个应用级 + 13 个页面级，见 docs/decisions/0031）
    //   27 项读写 = 用户 4 + 租户 4 + 组织 4 + 资源 4 + 角色 4 + 用户组 4
    //             + 审计读 1 + 日志读 1 + 事件读 1
    //   （auth:audit:read 是 0019 加的；auth:resource:* 是资源管理接口加的；
    //     auth:user_group:* 是用户组管理接口加的；log:client:read 与 event:client:read
    //     是 0026 加的 —— 本切片之前，日志与事件的查询接口**完全没有鉴权**；
    //     auth:org:* 是组织管理接口加的，见 0030）
    // 菜单码是 0031 加的：它们只决定"界面上出现哪几个入口"，不参与接口准入
    if (adminView.perms.length !== 45)
      record(`管理员权限项应为 45，实际 ${adminView.perms.length}`);
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

    /* -------- 5. 组织页：树 / 移动 / 主岗 —— 数据范围的坐标系 -------- */

    /** 把树摊平，便于按编码取值（带上深度是为了看清它挂在哪一层） */
    const flattenTree = (nodes, depth = 0, out = []) => {
      for (const node of nodes) {
        out.push({ ...node, depth });
        if (node.children?.length) flattenTree(node.children, depth + 1, out);
      }
      return out;
    };
    const findOrg = async (code) => {
      const flat = flattenTree(await apiFetch("/api/auth/orgs/tree"));
      const found = flat.find((node) => node.orgCode === code);
      if (!found) throw new Error(`组织树里找不到 ${code}（现有：${flat.map((n) => n.orgCode).join(", ")}）`);
      return found;
    };
    /**
     * 取组织的详情。
     *
     * <p>**树节点里没有 `hasChild`** —— 那是 `OrganizationVO` 的字段，树接口返回的是
     * `OrganizationTreeNode`（只有结构、层级、路径与状态）。因此"原父级的下级有没有归零"
     * 只能从详情里读。第一版脚本就是在这里写错了期望：读不到值被判成了产品缺陷，
     * 而实际是读错了接口。
     */
    const orgDetailOf = (id) => apiFetch(`/api/auth/orgs/${id}`);

    await clickText("组织");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-node-HQ"]'))`,
      "组织树渲染出种子总部",
      20000,
    );
    const orgSeedView = await evaluate(`(() => ({
      codes: [...document.querySelectorAll('[data-testid^="org-node-"]')]
        .map(el => el.getAttribute('data-testid').replace('org-node-', '')),
      levels: [...document.querySelectorAll('[data-testid^="org-ops-"]')].length,
    }))()`);
    step(
      "5a. 组织页（管理员）",
      `树上节点 ${orgSeedView.codes.join(" / ")}`,
    );
    // 种子三层必须都在：少一层就看不出「本部门」与「本部门及下级」的差别
    for (const code of ["HQ", "RD", "RD_FE"]) {
      if (!orgSeedView.codes.includes(code))
        record(`组织树上缺少种子节点 ${code} —— 种子组织未就位`);
    }

    // 编码带时间戳：脚本可重复运行，不会撞上"编码已存在"
    const stamp = String(Date.now()).slice(-6);
    const tmpParent = `TMPA${stamp}`;
    const tmpChild = `TMPB${stamp}`;
    const tmpUser = `e2eorg${stamp}`;

    const hqBefore = await findOrg("HQ");
    const rdBefore = await findOrg("RD");
    const feBefore = await findOrg("RD_FE");

    /*
     * 界面建一层（证明新增路径通），第二层用接口建。
     *
     * 第二个节点只是**造场景**用的：它要挂在刚建出来的节点下，而那个节点的 id
     * 只有在第一次新建之后才知道。用接口建它，是因为"嵌套层的新增"不是这一步的
     * 验证对象 —— 走界面只会让这一步更慢、更脆。
     */
    await clickSelector(`[data-testid="org-ops-RD_FE"] .orgs__create-child`);
    await waitFor(`Boolean(document.querySelector('#org-form'))`, "新建组织对话框打开");
    await setInputValue('#org-form input[name="orgCode"]', tmpParent);
    await setInputValue('#org-form input[name="orgName"]', "端到端验证节点");
    await setInputValue('#org-form input[name="orgType"]', "TEAM");
    await clickSelector(".orgs__submit");
    await waitFor(
      `(document.querySelector('[data-testid="orgs-notice"]')?.textContent ?? '').includes('已新建')`,
      "新建组织成功提示出现",
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-node-${tmpParent}"]'))`,
      "新组织出现在树上",
      20000,
    );

    const tmpParentCreated = await findOrg(tmpParent);
    if (tmpParentCreated.parentId !== feBefore.id)
      record(`新组织的上级应为前端组(#${feBefore.id})，实际 #${tmpParentCreated.parentId}`);
    if (tmpParentCreated.orgLevel !== feBefore.orgLevel + 1)
      record(`新组织层级应为 ${feBefore.orgLevel + 1}，实际 ${tmpParentCreated.orgLevel}`);
    // 路径是"各级祖先主键加斜杠"，前端组的路径 + 前端组自己的主键
    if (tmpParentCreated.codePath !== `${feBefore.codePath}${feBefore.id}/`)
      record(`新组织路径应为 ${feBefore.codePath}${feBefore.id}/，实际 ${tmpParentCreated.codePath}`);

    const tmpChildCreated = await apiFetch("/api/auth/orgs", {
      method: "POST",
      body: {
        orgCode: tmpChild,
        orgName: "端到端验证子节点",
        parentId: tmpParentCreated.id,
        orgType: "TEAM",
      },
    });
    // 前端组现在有下级了 —— 这一步顺带把"移动之后原父级该不该保留 hasChild"立成了可断言的状态
    const feDetailWithChild = await orgDetailOf(feBefore.id);
    if (feDetailWithChild.hasChild !== 1)
      record(`前端组有了下级之后 hasChild 应为 1，实际 ${feDetailWithChild.hasChild}`);

    step(
      "5b. 组织树的新增",
      `界面建「${tmpParent}」→ 落在前端组下（L${tmpParentCreated.orgLevel}，路径 ${tmpParentCreated.codePath}）；` +
        `接口再建「${tmpChild}」造出两层子树`,
    );

    /* --- 移动：这一次必须同时改对"路径与层级"和"原父级的 hasChild" --- */

    await clickSelector(`[data-testid="org-ops-${tmpParent}"] .orgs__move`);
    await waitFor(`Boolean(document.querySelector('#org-move-form'))`, "移动对话框打开");

    const moveCandidates = await evaluate(`(() => {
      const select = document.querySelector('[data-testid="org-ops-${tmpParent}"]')
        && document.querySelector('.orgs__move-parent');
      if (!select) return null;
      return [...select.options].map(o => ({ value: o.value, text: o.textContent.trim() }));
    })()`);
    // 自身与它的下级必须不在候选里：那两个目标必然被拒(40913)
    const candidateValues = (moveCandidates ?? []).map((o) => o.value);
    if (candidateValues.includes(String(tmpParentCreated.id)))
      record("移动的候选里含自身 —— 会出现一个必然被拒的选项");
    if (candidateValues.includes(String(tmpChildCreated.id)))
      record("移动的候选里含自己的下级 —— 会出现一个必然被拒的选项");

    await evaluate(`(() => {
      const select = document.querySelector('.orgs__move-parent');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
      setter.call(select, ${JSON.stringify(String(hqBefore.id))});
      select.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
    await clickSelector(".orgs__move-submit");
    await waitFor(
      `(document.querySelector('[data-testid="orgs-notice"]')?.textContent ?? '').includes('已移动')`,
      "移动成功提示出现",
    );

    const tmpParentMoved = await findOrg(tmpParent);
    const tmpChildMoved = await findOrg(tmpChild);
    // hasChild 只存在于详情（树节点没有这一列）
    const feDetailAfterMove = await orgDetailOf(feBefore.id);

    if (tmpParentMoved.parentId !== hqBefore.id)
      record(`移动后上级应为总部(#${hqBefore.id})，实际 #${tmpParentMoved.parentId}`);
    if (tmpParentMoved.orgLevel !== hqBefore.orgLevel + 1)
      record(`移动后层级应为 ${hqBefore.orgLevel + 1}，实际 ${tmpParentMoved.orgLevel}`);
    if (tmpParentMoved.codePath !== `${hqBefore.codePath}${hqBefore.id}/`)
      record(`移动后路径应为 ${hqBefore.codePath}${hqBefore.id}/，实际 ${tmpParentMoved.codePath}`);
    // **整棵子树**一起重写 —— 只改被移动的那一个节点，下级就会对不上
    if (tmpChildMoved.codePath !== `${tmpParentMoved.codePath}${tmpParentMoved.id}/`)
      record(`下级路径没有被一起重写：期望 ${tmpParentMoved.codePath}${tmpParentMoved.id}/，实际 ${tmpChildMoved.codePath}`);
    if (tmpChildMoved.orgLevel !== tmpParentMoved.orgLevel + 1)
      record(`下级层级没有被一起重写：期望 ${tmpParentMoved.orgLevel + 1}，实际 ${tmpChildMoved.orgLevel}`);
    // 原父级（前端组）失去了唯一的下级 —— 这一列不跟着归零就是"半更新"
    if (feDetailAfterMove.hasChild !== 0)
      record(`移动后原父级（前端组）的 hasChild 应为 0，实际 ${feDetailAfterMove.hasChild} —— 半更新状态`);
    // DEPT_AND_CHILD 按路径前缀求值，因此"搬走之后还看得见"是数据范围层面的错误
    if (tmpChildMoved.codePath.startsWith(`${feBefore.codePath}${feBefore.id}/`))
      record("被搬走的子树仍然落在原来的路径前缀下 —— 数据范围会多算");

    step(
      "5c. 移动（子树级改动）",
      `「${tmpParent}」从前端组搬到总部：自身 L${tmpParentCreated.orgLevel}→L${tmpParentMoved.orgLevel}、` +
        `路径 ${tmpParentCreated.codePath}→${tmpParentMoved.codePath}；` +
        `下级路径随之重写为 ${tmpChildMoved.codePath}；原父级 hasChild ${feDetailWithChild.hasChild}→${feDetailAfterMove.hasChild}；` +
        `候选里已剔除自身与下级`,
    );

    /* --- 移回原位：派生列必须逐字回到原值 --- */

    await clickSelector(`[data-testid="org-ops-${tmpParent}"] .orgs__move`);
    await waitFor(`Boolean(document.querySelector('#org-move-form'))`, "移动对话框再次打开");
    await evaluate(`(() => {
      const select = document.querySelector('.orgs__move-parent');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
      setter.call(select, ${JSON.stringify(String(feBefore.id))});
      select.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
    await clickSelector(".orgs__move-submit");
    await waitFor(
      `(document.querySelector('[data-testid="orgs-notice"]')?.textContent ?? '').includes('已移动')`,
      "移回原位的提示出现",
    );

    const tmpParentBack = await findOrg(tmpParent);
    const tmpChildBack = await findOrg(tmpChild);
    /*
     * **逐字相等**而不是"层级对了就行"。
     *
     * 派生列是 `parentId` 与各节点名称的确定函数，因此"搬回去"必须精确还原 ——
     * 只要有一处残留（少重写一层、路径多了个前缀、全称用了旧父名），
     * 树看起来照样正常，而按路径前缀求值的数据范围已经从那一刻起算错了。
     */
    if (tmpParentBack.codePath !== tmpParentCreated.codePath)
      record(`搬回后路径与原值不一致：${tmpParentCreated.codePath} → ${tmpParentBack.codePath}`);
    if (tmpParentBack.orgLevel !== tmpParentCreated.orgLevel)
      record(`搬回后层级与原值不一致：${tmpParentCreated.orgLevel} → ${tmpParentBack.orgLevel}`);
    if (tmpParentBack.fullName !== tmpParentCreated.fullName)
      record(`搬回后全称与原值不一致：${tmpParentCreated.fullName} → ${tmpParentBack.fullName}`);
    if (tmpChildBack.codePath !== tmpChildCreated.codePath)
      record(`搬回后子树路径与原值不一致：${tmpChildCreated.codePath} → ${tmpChildBack.codePath}`);

    step(
      "5d. 搬回原位",
      `路径 ${tmpParentBack.codePath}、层级 L${tmpParentBack.orgLevel}、` +
        `全称「${tmpParentBack.fullName}」与原值逐字相等`,
    );

    /* --- 成员与主岗：主岗是 DEPT 档位唯一的取值来源 --- */

    const orgUser = await apiFetch("/api/auth/users", {
      method: "POST",
      body: {
        username: tmpUser,
        displayName: "端到端组织成员",
        // 临时口令**在运行时生成**，不落一个固定字面量：这个账号每次跑完都会删掉，
        // 没有任何地方需要认得它。规则同 `scripts/README.md`：
        // 脚本里不出现会与别处对齐的凭据（种子的那两口口令在 `lib/seed-credentials.mjs`）。
        password: `E2eOrg#${stamp}Kx`,
      },
    });

    await clickSelector(`[data-testid="org-ops-${tmpParent}"] .orgs__members`);
    await waitFor(`Boolean(document.querySelector('.orgs__member-id'))`, "组织成员对话框打开");

    const primaryChoiceBefore = await evaluate(
      `document.querySelector('.orgs__primary-choice').value`,
    );
    // 缺省必须是"不指定"：给个默认的"是"会把服务端那个有信息量的默认值压成二选一
    if (primaryChoiceBefore !== "")
      record(`「是否主岗」的缺省值应为空（不指定），实际 "${primaryChoiceBefore}"`);

    await evaluate(`(() => {
      const select = document.querySelector('.orgs__member-id');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
      setter.call(select, ${JSON.stringify(String(orgUser.id))});
      select.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
    await clickSelector(".orgs__member-submit");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-member-${orgUser.id}"]'))`,
      "新成员出现在成员列表里",
      20000,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-member-${orgUser.id}"] [data-testid="org-primary-flag"]'))`,
      "成员的「主岗」标记出现",
      20000,
    );

    const afterBind = await apiFetch(`/api/auth/users/${orgUser.id}/organizations`);
    const autoPrimary = afterBind.filter((row) => row.primaryPosition);
    /*
     * 服务端对"不传 isPrimaryPosition"的定义：该用户当前没有别的主岗则**自动设为主岗**。
     * 因此这里期望恰好一条主岗 —— 界面没有替它做决定，是服务端按规则补的。
     */
    if (autoPrimary.length !== 1)
      record(`挂成员（不指定主岗）之后该用户的主岗数应为 1，实际 ${autoPrimary.length}`);
    if (autoPrimary[0]?.orgId !== tmpParentCreated.id)
      record(`自动成为主岗的应是刚挂上的组织(#${tmpParentCreated.id})，实际 #${autoPrimary[0]?.orgId}`);

    step(
      "5e. 挂成员与主岗",
      `界面把 #${orgUser.id} 挂到「${tmpParent}」且「是否主岗」保持缺省（不指定）→ ` +
        `服务端按规则自动设为主岗，归属 ${afterBind.length} 条、主岗 ${autoPrimary.length} 条`,
    );

    // 再把同一个人的主岗设到总部 —— 服务端应先把原主岗降下来
    await evaluate(`(() => {
      const close = [...document.querySelectorAll('.dialog__close')].at(-1);
      close.click();
      return true;
    })()`);
    await clickSelector(`[data-testid="org-ops-HQ"] .orgs__members`);
    await waitFor(`Boolean(document.querySelector('.orgs__member-id'))`, "总部成员对话框打开");

    // 总部成员表里还没有这个人，先挂进来（不指定主岗），再点「设为主岗」
    await evaluate(`(() => {
      const select = document.querySelector('.orgs__member-id');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
      setter.call(select, ${JSON.stringify(String(orgUser.id))});
      select.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
    await clickSelector(".orgs__member-submit");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-member-${orgUser.id}"]'))`,
      "成员出现在总部成员表里",
      20000,
    );
    await clickSelector(
      `[data-testid="org-member-${orgUser.id}"] .orgs__set-primary`,
    );
    await waitFor(
      `Boolean(document.querySelector('[data-testid="org-member-${orgUser.id}"] [data-testid="org-primary-flag"]'))`,
      "总部这一条被标成了主岗",
      20000,
    );

    const afterSetPrimary = await apiFetch(`/api/auth/users/${orgUser.id}/organizations`);
    const primaries = afterSetPrimary.filter((row) => row.primaryPosition);
    // **每人每租户至多一个主岗**，而这条约束完全靠服务层（关系表没有唯一索引）
    if (primaries.length !== 1)
      record(`设为主岗之后该用户的主岗数应为 1，实际 ${primaries.length} —— 主岗唯一性失效`);
    if (primaries[0]?.orgId !== hqBefore.id)
      record(`主岗应已变成总部(#${hqBefore.id})，实际 #${primaries[0]?.orgId}`);

    // 主岗搬走会让数据范围求值跟着变，因此顺手看一眼求值结果
    const movedScope = await apiFetch(`/api/auth/users/${orgUser.id}/data-scope`);
    step(
      "5f. 主岗切换",
      `把 #${orgUser.id} 的主岗设到总部 → 归属 ${afterSetPrimary.length} 条、主岗 ${primaries.length} 条` +
        `（原主岗被一并降下）；此人数据范围「${movedScope.scopeLabel ?? "（无）"}」` +
        `precise=${movedScope.precise} 主岗组织 #${movedScope.primaryOrgId ?? "-"}。` +
        // 档位为空不是因为主岗没生效，而是这个临时账号**没有任何角色** ——
        // 无角色档位的用户被按「仅本人」处理。两件事在返回里分得很清楚：
        // scope 为 null，而 precise 是 true。
        `（该账号没有任何角色，因此没有档位可合并 —— scope 为空、precise=true 是"无角色按仅本人"那一支）`,
    );

    await screenshot("07-org-management");

    /* --- 清理：守卫会挡住"删还有成员的部门"，因此顺序是 移出成员 → 删子 → 删父 --- */

    const deleteOutcome = await evaluate(`(() => {
      const close = [...document.querySelectorAll('.dialog__close')].at(-1);
      close.click();
      return true;
    })()`);
    void deleteOutcome;

    await apiFetch(`/api/auth/orgs/${hqBefore.id}/members/${orgUser.id}`, { method: "DELETE" });
    await apiFetch(`/api/auth/orgs/${tmpParentCreated.id}/members/${orgUser.id}`, { method: "DELETE" });
    await apiFetch(`/api/auth/users/${orgUser.id}`, { method: "DELETE" });

    await clickSelector(`[data-testid="org-ops-${tmpChild}"] .orgs__remove`);
    await waitFor(`Boolean(document.querySelector('.orgs__remove-confirm'))`, "删除确认框打开");
    await clickSelector(".orgs__remove-confirm");
    await waitFor(
      `!document.querySelector('[data-testid="org-node-${tmpChild}"]')`,
      "子节点从树上消失",
      20000,
    );

    await clickSelector(`[data-testid="org-ops-${tmpParent}"] .orgs__remove`);
    await waitFor(`Boolean(document.querySelector('.orgs__remove-confirm'))`, "删除确认框再次打开");
    await clickSelector(".orgs__remove-confirm");
    await waitFor(
      `!document.querySelector('[data-testid="org-node-${tmpParent}"]')`,
      "父节点从树上消失",
      20000,
    );

    const treeAfterCleanup = await apiFetch("/api/auth/orgs/tree");
    const codesAfterCleanup = flattenTree(treeAfterCleanup).map((node) => node.orgCode).sort();
    if (codesAfterCleanup.join(",") !== "HQ,RD,RD_FE")
      record(`清理后树应只剩种子三层，实际 ${codesAfterCleanup.join(",")}`);
    const rdAfterCleanup = await orgDetailOf(feBefore.id);

    step(
      "5g. 删除与清理",
      `有下级时先删子再删父（守卫会挡住"删还有成员的部门"）；清理后树回到 ` +
        `${codesAfterCleanup.join(" / ")}，前端组 hasChild 回到 ${rdAfterCleanup.hasChild}`,
    );

    /* ---------------- 6. 登出 → 审计员身份（只读） ---------------- */

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
      "6. 审计员视角（只读）",
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
    // 14 项 = 3 项读写（用户读 + 租户读 + 审计读；审计读是 0019 加的，审计台要靠它）
    //       + 11 条菜单码（权限管理 1 个应用级 + 6 个页面级 + 审计日志 1 + 3，见 0031）
    // 菜单码让审计员**进得去**这几个页面，读写权限才决定他进去之后能做什么 ——
    // 所以下面仍然要断言"一个写按钮都看不到"
    if (auditorView.perms.length !== 14)
      record(`审计员权限项应为 14，实际 ${auditorView.perms.length}`);
    // 403 不该被当成登录失效：页面必须还停在 /admin
    if (auditorView.path !== "/admin")
      record(`审计员被从 /admin 弹走了，实际在 ${auditorView.path}`);

    await screenshot("06-auditor-read-only");

    /* -------- 7. 审计员的组织面：树 403，但个人归属与数据范围可用 -------- */

    /*
     * 这一步验证的是一条**刻意的权限划分**（后端 `UserController` 的注释里写明了）：
     *
     *   组织**树**   归 auth:org:read
     *   个人归属 / 数据范围  归 auth:user:read
     *
     * 两者**不互相蕴含** —— 只有 org:read 的人能改组织结构，却看不到"某个人在哪些部门"；
     * 只有 user:read 的人能反查个人归属，却看不到树本身。审计员的权限是
     * 用户读 + 租户读 + 审计读，因此这个划分在它身上体现得最完整：
     * 组织标签页应当**读不到**（403），而用户页上的两个反查入口应当**照常可用**。
     *
     * 注意标签页本身还在：**菜单级的权限过滤是另一个待办**（见 `docs/README.md`
     * 的规则表与"菜单权限两步"）。这里如实断言"点了会看到 403"，
     * 而不是假装这个标签已经按权限藏起来了。
     */
    await clickText("组织");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="orgs-error"]'))`,
      "审计员在组织页看到错误提示（无 auth:org:read，树接口 403）",
      20000,
    );
    const auditorOrgView = await evaluate(`(() => ({
      error: document.querySelector('[data-testid="orgs-error"]').textContent,
      nodes: document.querySelectorAll('[data-testid^="org-node-"]').length,
      writeEntrances: document.querySelectorAll(
        '.orgs__create, .orgs__create-child, .orgs__edit, .orgs__move, .orgs__members, .orgs__remove',
      ).length,
    }))()`);
    step(
      "7a. 审计员进组织页",
      `错误提示「${auditorOrgView.error}」· 树上节点 ${auditorOrgView.nodes} 个 · ` +
        `写入口 ${auditorOrgView.writeEntrances} 个`,
    );
    if (auditorOrgView.nodes !== 0)
      record("审计员没有 auth:org:read，组织树却渲染出了节点 —— 读权限没有生效");
    if (auditorOrgView.writeEntrances !== 0)
      record("审计员在组织页看到了写入口 —— 按钮级权限没有生效");
    /*
     * 断言的是**报错有没有说清原因**，而不是"有没有 403 字样"。
     *
     * `@RequiresPermission` 拒绝时抛的是业务异常，因此线上是 **HTTP 200 + code=40301**
     * （本仓铁律：业务失败返 HTTP 200），走的是前端 `errors.ts` 里 `BusinessError`
     * 那一条分支 —— 直接显示服务端给的文案。那句文案是「缺少权限：auth:org:read」，
     * 它比一个裸的 403 更有用：**指名道姓说出了缺哪一个权限码**。
     *
     * （第一版这里断言了"必须含 403"，结果是错的期望 —— `describeError` 里的
     * 403 分支只对真正的 HTTP 403 生效，例如网关层拒绝。这条注释留着，是因为
     * 下一版脚本很容易再犯同一个错。）
     */
    if (!auditorOrgView.error.includes("auth:org:read"))
      record(`组织页的报错应当指名缺少哪个权限，实际「${auditorOrgView.error}」`);

    // 个人归属与数据范围归 auth:user:read —— 反查入口必须还在
    await clickText("用户");
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-row-admin"]'))`,
      "回到用户列表",
      20000,
    );
    const auditorUserView = await evaluate(`(() => ({
      orgs: Boolean(document.querySelector('.users__orgs')),
      scope: Boolean(document.querySelector('.users__scope')),
      sources: Boolean(document.querySelector('.users__sources')),
    }))()`);
    step(
      "7b. 审计员在用户页",
      `角色来源=${auditorUserView.sources} 组织归属=${auditorUserView.orgs} 数据范围=${auditorUserView.scope}` +
        `（三者都归 auth:user:read，因此都应在）`,
    );
    if (!auditorUserView.orgs)
      record("审计员看不到「组织归属」—— 它归 auth:user:read，不该被 org:read 的缺失连坐");
    if (!auditorUserView.scope)
      record("审计员看不到「数据范围」—— 同上");

    await clickSelector('[data-testid="user-row-admin"] .users__scope');
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-scope-facts"]'))`,
      "数据范围反查对话框打开",
    );
    const scopeView = await evaluate(`(() => ({
      scope: document.querySelector('[data-testid="user-scope-value"]').textContent,
      precise: document.querySelector('[data-testid="user-scope-precise"]').textContent,
      primaryOrg: document.querySelector('[data-testid="user-scope-primary-org"]').textContent,
      visibleOrgs: document.querySelector('[data-testid="user-scope-visible-orgs"]').textContent,
    }))()`);
    step(
      "7c. 反查管理员的数据范围",
      `档位「${scopeView.scope}」· 能否精确求值「${scopeView.precise}」· ` +
        `主岗组织「${scopeView.primaryOrg}」· 可见组织「${scopeView.visibleOrgs}」`,
    );
    // 种子管理员是超级管理员 → 档位是 ALL
    if (!scopeView.scope.includes("全部"))
      record(`管理员的档位应为「全部数据」，实际「${scopeView.scope}」`);
    if (!scopeView.precise.includes("可以"))
      record(`ALL 档位是自足的，precise 应为 true，实际「${scopeView.precise}」`);
    /*
     * ALL 档位下 `primaryOrgId` 与 `visibleOrgIds` **刻意都是 null**。
     *
     * 理由在 `UserDataScopeVO` 与 `DataScopeService.resolve` 里写明了：
     * 这两个字段只对 `DEPT` 系列有意义（`selfSufficient()` 的档位直接返回，不进组织求值），
     * 而 `visibleOrgIds = null` 的语义是「这一档不是按组织划定的」——
     * 与空集合「按组织划定了但一个也没有」是两件事。
     *
     * 因此这里断言的是**界面有没有把这个区别显示出来**（一句"不是按组织划定的"），
     * 而不是"主岗组织应有值"。第一版脚本在这里写反了期望：
     * 它把 ALL 档下的 null 当成了"种子没给 admin 挂主岗"。
     */
    if (!scopeView.visibleOrgs.includes("不是按组织划定"))
      record(`ALL 档的可见组织集合应显示为"不是按组织划定的"，实际「${scopeView.visibleOrgs}」`);

    // 主岗确实存在 —— 但不该从 data-scope 里看，而要从"组织归属"里看
    await evaluate(`(() => {
      const close = [...document.querySelectorAll('.dialog__close')].at(-1);
      close.click();
      return true;
    })()`);
    await clickSelector('[data-testid="user-row-admin"] .users__orgs');
    await waitFor(
      `Boolean(document.querySelector('[data-testid="user-org-HQ"]'))`,
      "管理员的组织归属里出现总部",
    );
    const adminOrgs = await evaluate(`(() => {
      const rows = [...document.querySelectorAll('[data-testid^="user-org-"]')];
      return {
        codes: rows.map(el => el.getAttribute('data-testid').replace('user-org-', '')),
        primaries: rows
          .filter(el => el.querySelector('[data-testid="user-org-primary"]'))
          .map(el => el.getAttribute('data-testid').replace('user-org-', '')),
      };
    })()`);
    step(
      "7d. 管理员的组织归属",
      `归属 ${adminOrgs.codes.join(" / ")} · 主岗 ${adminOrgs.primaries.join(" / ") || "（无）"}`,
    );
    // 种子说"admin 主岗挂总部"—— 这句话在这里被真的验证一次
    if (adminOrgs.primaries.length !== 1)
      record(`管理员的主岗应恰好 1 条，实际 ${adminOrgs.primaries.length}`);
    if (adminOrgs.primaries[0] !== "HQ")
      record(`管理员的主岗应在总部(HQ)，实际 ${adminOrgs.primaries[0] ?? "（无）"}`);

    await screenshot("07-auditor-reverse-lookup");

    await evaluate(`(() => {
      const close = [...document.querySelectorAll('.dialog__close')].at(-1);
      close.click();
      return true;
    })()`);

    /* ---------------- 8. 硬刷新 → 权限随会话一起恢复 ---------------- */

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
      "8. 硬刷新",
      `落地 ${afterReload.path} · 「${afterReload.identity}」· 新建按钮=${afterReload.create}`,
    );
    if (afterReload.path === "/login")
      record("刷新页面后掉登录 —— 会话恢复没有生效");
    // 内存里的访问令牌已经没了，权限是走刷新接口重新拿回来的
    if (afterReload.create)
      record("刷新后审计员看到了新建按钮 —— 恢复回来的权限集合不对");
    if (!afterReload.identity.includes("userId="))
      record("刷新后权限没有重新就绪");

    /* ---------------- 9. 登出清干净 ---------------- */

    await clickSelector(".basic-layout__action:last-of-type");
    await waitFor(`location.pathname === '/login'`, "登出后被弹回登录页");
    const afterLogout = await evaluate(`(() => ({
      persisted: localStorage.getItem('bw.session'),
      hasIdentity: Boolean(document.querySelector('[data-testid="admin-identity"]')),
    }))()`);
    step(
      "9. 登出",
      `localStorage 残留=${afterLogout.persisted ?? "（已清空）"}`,
    );
    if (afterLogout.persisted !== null)
      record("登出后 localStorage 里仍留有会话");
    if (afterLogout.hasIdentity)
      record("登出后子应用界面仍在（应随路由一起卸载）");

    /* ---------------- 10. 浏览器侧错误 ---------------- */

    step("10. 浏览器侧错误", errors.length ? errors.join("\n") : "（无）");
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
