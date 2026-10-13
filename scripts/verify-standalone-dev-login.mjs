/**
 * 用 Chrome DevTools Protocol 验证「子应用单跑 + 开发账号自动登录」这条路径。
 *
 * ## 为什么需要它
 *
 * 这是**全仓唯一没有任何脚本覆盖的运行方式**：所有端到端脚本都从基座（:3000）进去，
 * 而"单独 `pnpm dev` 打开一个子应用"是日常改一行代码的默认姿势。
 * `docs/decisions/0033` 恰好改的就是这条路径的形状（`devAccount` 只在开发构建里构造），
 * 单测覆盖不到"浏览器里真的登录了没有"，只有真机跑一遍才算数。
 *
 * ## 它验证什么（这些是单测结构上测不到的）
 *
 * 1. **独立运行真的走自建会话**：没有宿主桥时，令牌来自应用自己那份会话 ——
 *    证据是登录后 `localStorage` 里出现 `bw.session`（刷新令牌是会话唯一的落盘物）；
 * 2. **开发构建真的用开发账号自动登录**：页面出现**鉴权后才会出现的内容**
 *    （右栏、标签页、身份里的权限项数），而不是只渲染了一个空壳；
 * 3. **后端没起时单跑不白屏**：这一步是 `0031` 特意保护的性质 ——
 *    后端不可用时应给出一句明确的话，而不是一片空白或一个转不完的圈；
 * 4. **此时不该有未捕获的 Promise 拒绝**：自动登录失败是一条**预期内**的路径
 *    （后端没起），不该以"未处理的拒绝"这种形式冒到控制台 ——
 *    那会让真正的异常淹没在噪声里。
 *
 * 脚本**自己判定后端在不在**（探测 `/api/health`），并按对应的期望断言，
 * 因此两种状态下都能跑、且结论都算数。
 *
 * ## 前置
 *
 * 后端（可选，但在跑时本脚本的结论更强）：
 *   cd server && mvn -pl sts-app/sts-app-server -am install -DskipTests        # 首次或上游有改动时
 *   cd server && JAVA_HOME=<jdk21> mvn -pl sts-app/sts-app-server spring-boot:run
 *
 *   **启动这一步不能带 `-am`**：`spring-boot:run` 是 CLI goal，会落到 reactor 里
 *   每一个模块上，而父工程 `sts-parent` 没有 main class ⇒ 直接 BUILD FAILURE
 *   （报 `Unable to find a suitable main class`）。上游模块改为先用 `install` 进本地仓库。
 * dev 服务（跑哪几个就验哪几个，没在跑的会被记为"跳过"）：
 *   pnpm --filter platform-log-console dev          # :3200
 *   pnpm --filter platform-event-console dev        # :3300
 *   pnpm --filter platform-admin dev                # :3500
 *   pnpm --filter platform-audit-console dev        # :3600
 *
 * ## 用法
 *
 *   node scripts/verify-standalone-dev-login.mjs              # 验证
 *   node scripts/verify-standalone-dev-login.mjs <截图目录>    # 验证并保存截图
 *
 * 退出码即结论（有任一问题即非零）。
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9342;
const BACKEND_HEALTH = "http://127.0.0.1:8080/api/health";
const VIEWPORT = { width: 1440, height: 960 };
const SHOT_DIR = process.argv[2] ?? null;

/**
 * 四个子应用，以及两种后端状态下的期望。
 *
 * `ready` 是"自动登录成功"的现场证据 —— 挑的都是**鉴权后才会出现的元素**：
 * 日志台的右栏要 `log:client:read`，管理面的模块标签要 `ui:admin_*:view` 菜单码。
 * 用"页面有东西"当判据是不够的：没登录时它也有一屏空态。
 *
 * <p>**`ready` 只能选"权限产物"，不能选布局外壳。** 这条是实测撞出来的：
 * 最初给管理面选的是 `admin-tabs`（标签栏的容器）、给审计台选的是 `audit-identity`
 * （顶栏那行身份文字）—— 两者都**无条件渲染**，于是后端没起时它们照样在，
 * 脚本就报了"后端没起却出现了鉴权内容"这种假问题。
 * 真正由权限决定的是**里面装的东西**：标签按钮、以及三个区块。
 *
 * `degraded` 是"后端没起"时的期望，四个应用落到两句不同的话上：
 * 两个控制台的两栏由菜单码决定，未登录 ⇒ 两栏都不显示 ⇒ 落到那句指名缺哪个码的空态；
 * 管理面与审计台则是身份读数停在"未就绪" ⇒ 各自的 pending 横幅。
 */
const APPS = [
  {
    name: "platform-log-console",
    port: 3200,
    title: "BlueWhale · 日志控制台",
    ready: '[data-testid="log-server-panel"]',
    degraded: '[data-testid="console-empty"]',
  },
  {
    name: "platform-event-console",
    port: 3300,
    title: "BlueWhale · 行为事件控制台",
    ready: '[data-testid="event-server-panel"]',
    degraded: '[data-testid="event-empty"]',
  },
  {
    name: "platform-admin",
    port: 3500,
    title: "BlueWhale · 权限管理",
    // 用某个**模块标签按钮**而不是 `admin-tabs`：那一层是布局，没有权限时也在
    ready: '[data-testid="admin-tab-users"]',
    degraded: '[data-testid="admin-pending"]',
  },
  {
    name: "platform-audit-console",
    port: 3600,
    title: "BlueWhale · 审计日志",
    // 同理，用「审计流水」区块而不是顶栏那行 `audit-identity`
    ready: '[data-testid="audit-list-panel"]',
    degraded: '[data-testid="audit-pending"]',
  },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const problems = [];
const record = (message) => {
  problems.push(message);
  console.log(`  · ${message}`);
};

function step(title, body) {
  console.log(`\n== ${title} ==\n${body}`);
}

/** 后端在不在 —— 决定本次跑的是哪一套期望 */
async function backendUp() {
  try {
    const res = await fetch(BACKEND_HEALTH, { signal: AbortSignal.timeout(3000) });
    if (!res.ok) return false;
    const body = await res.json();
    // 业务失败返 HTTP 200，判据是 code === 0（见 docs/decisions 的后端约定）
    return body?.code === 0;
  } catch {
    return false;
  }
}

/** 端口上有没有人在听（用来跳过没起的 dev 服务，而不是把它当成失败） */
async function listening(port) {
  try {
    const res = await fetch(`http://127.0.0.1:${port}/`, { signal: AbortSignal.timeout(3000) });
    return res.ok;
  } catch {
    return false;
  }
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
  const up = await backendUp();
  console.log(
    `后端 ${up ? "在跑（期望自动登录成功）" : "没起（期望优雅降级、不白屏）"}：${BACKEND_HEALTH}`,
  );

  const runnable = [];
  for (const app of APPS) {
    if (await listening(app.port)) {
      runnable.push(app);
    } else {
      console.log(`  · 跳过 ${app.name}（:${app.port} 未在监听）`);
    }
  }
  if (runnable.length === 0) {
    console.log("\n=== 结论：没有可验证的应用（先起至少一个 dev 服务） ===");
    process.exitCode = 1;
    return;
  }

  const userDataDir = mkdtempSync(join(tmpdir(), "bw-standalone-"));
  // 先停在空白页：**等附加上调试器并把错误监听装好再导航**。
  // 直接带着目标网址启动会有一段竞态 —— 初始加载期的异常发生在我们挂上之前，
  // 而本轮要看的恰好就是那段（自动登录就在首帧之前发起）。
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
      "about:blank",
    ],
    { stdio: "ignore" },
  );

  /*
   * WebSocket 在 `try` 之外声明。
   *
   * 它要在 `finally` 里被关掉（否则事件循环挂住），而 `const` 声明在 `try` 块里
   * 到了 `finally` 就出了作用域 —— 那会让"结论全过"紧跟一个 `ws is not defined`，
   * 反而把一次成功的验证变成失败退出。
   */
  let ws = null;

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) => r.json());
    const page = targets.find((t) => t.type === "page");
    if (!page) throw new Error("未找到可用的页面目标");

    ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let seq = 0;
    const waiting = new Map();
    /**
     * 本轮导航产生的**脚本层**错误（未捕获异常 / 未处理的拒绝）。
     *
     * 这一类**任何模式下都判失败**：它们是代码缺陷，不是场景。
     * 每次导航前清空。
     */
    let errors = [];
    /**
     * 网络层通知（浏览器为每个非 2xx 响应记的一条）。
     *
     * 与上面分开是因为"后端没起"时请求**本来就该失败**，那种 502 正是被验证的场景本身，
     * 拿它判失败等于让脚本在后端没起时永远红着。它只在**后端在跑**时才算异常
     * （那时任何非 2xx 都说明有别的问题），在降级模式下只报个数。
     */
    let networkNotices = [];

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
        const text = `${entry.text} @ ${entry.url ?? "-"}`;
        if (entry.source === "network") networkNotices.push(text);
        else errors.push(text);
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
     * （`Execution context was destroyed`）。本脚本逐应用导航，一定会踩到。
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

    async function screenshot(name) {
      if (!SHOT_DIR) return;
      const shot = await send("Page.captureScreenshot", { format: "png" });
      writeFileSync(join(SHOT_DIR, `${name}.png`), Buffer.from(shot.data, "base64"));
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /*
     * 页面侧的兜底收集器。
     *
     * CDP 的 `Runtime.exceptionThrown` 覆盖不到"导航刚开始"那一小段，
     * 而本脚本最关心的未捕获拒绝恰恰发生在首帧之前。
     * 这段脚本在**每个新文档的任何脚本之前**执行，因此不丢。
     */
    await send("Page.addScriptToEvaluateOnNewDocument", {
      source: `
        window.__bwErrors = [];
        window.addEventListener('unhandledrejection', (event) => {
          window.__bwErrors.push('unhandledrejection: ' + (event.reason?.message ?? String(event.reason)));
        });
        window.addEventListener('error', (event) => {
          window.__bwErrors.push('error: ' + (event.message ?? String(event.error)));
        });
      `,
    });

    for (const app of runnable) {
      const url = `http://127.0.0.1:${app.port}/`;
      errors = [];
      networkNotices = [];

      await send("Page.navigate", { url });
      // 等首屏：等的是"两种落点之一出现"。**不直接等 ready 选择器** ——
      // 后端没起时它永远不会出现，那会变成一个超时报错，
      // 而不是"如期降级"这个结论
      const expected = up ? app.ready : app.degraded;
      const other = up ? app.degraded : app.ready;

      let settled = "（无）";
      const deadline = Date.now() + 25000;
      while (Date.now() < deadline) {
        const state = await evaluate(`(() => {
          const has = (selector) => Boolean(document.querySelector(selector));
          return {
            ready: has(${JSON.stringify(app.ready)}),
            degraded: has(${JSON.stringify(app.degraded)}),
            mounted: Boolean(document.querySelector('#root')?.children.length),
            pending: has('[data-testid$="-pending"]'),
          };
        })()`);
        if (state.ready) {
          settled = "ready";
          break;
        }
        if (state.degraded) {
          settled = "degraded";
          break;
        }
        settled = state.pending ? "pending" : state.mounted ? "mounted" : "（空白）";
        await sleep(300);
      }

      const facts = await evaluate(`(() => {
        const root = document.querySelector('#root');
        let session = null;
        try {
          session = window.localStorage.getItem('bw.session');
        } catch {
          session = '（读不到）';
        }
        const identity = document.querySelector('[data-testid$="-identity"]');
        return {
          title: document.title,
          text: (root?.textContent ?? '').trim().slice(0, 160),
          rootChildren: root?.children.length ?? 0,
          hasSession: Boolean(session),
          hasRefreshToken: Boolean(session && JSON.parse(session)?.refreshToken),
          identity: (identity?.textContent ?? '').trim(),
          testids: Array.from(document.querySelectorAll('[data-testid]'))
            .map((el) => el.getAttribute('data-testid'))
            .slice(0, 24),
        };
      })()`);

      const pageErrors = await evaluate("window.__bwErrors ?? []");

      step(
        `${app.name}（:${app.port}）`,
        [
          `落地 ${settled} · 标题「${facts.title}」`,
          `#root 子节点 ${facts.rootChildren} 个 · 文本「${facts.text.replace(/\s+/g, " ")}」`,
          `会话已落盘=${facts.hasSession}（含刷新令牌=${facts.hasRefreshToken}）`,
          facts.identity ? `身份「${facts.identity}」` : null,
          `标识 ${facts.testids.join(" · ") || "（无）"}`,
          networkNotices.length
            ? `网络层通知 ${networkNotices.length} 条${up ? "（后端在跑，不该有）" : "（后端没起，属于被验证的场景）"}`
            : null,
        ]
          .filter(Boolean)
          .join("\n"),
      );

      if (facts.title !== app.title) {
        record(`${app.name}：标题不是「${app.title}」（实际「${facts.title}」）—— 端口可能被别的实例占了`);
      }

      // 不白屏：这是本轮最根本的一条
      if (facts.rootChildren === 0 || facts.text.length === 0) {
        record(`${app.name}：页面是空白的 —— 独立运行时不该出现这种状态`);
      }

      if (up) {
        if (settled !== "ready") {
          record(
            `${app.name}：后端在跑，但 ${app.ready} 没有出现（落点 ${settled}）—— ` +
              `自动登录没成功，或登录成功了但权限没推给界面`,
          );
        }
        if (!facts.hasSession) {
          record(`${app.name}：没有自建会话（localStorage 里没有 bw.session）—— 独立运行没有走登录`);
        }
      } else {
        if (settled === "ready") {
          record(`${app.name}：后端没起却出现了鉴权内容（${other}）—— 这条路径不该有数据`);
        }
        if (facts.hasSession) {
          record(`${app.name}：后端没起却留下了一份会话 —— 失败路径不该写盘`);
        }
      }

      /*
       * 脚本层错误：未捕获异常与未处理的拒绝。
       *
       * CDP 与页面侧收集器会**各报一次**同一件事，因此按内容去重 ——
       * 否则一条未处理的拒绝会被记成两个问题，问题清单的长度就不再等于问题的个数。
       */
      const scriptErrors = Array.from(new Set([...errors, ...pageErrors]));
      if (scriptErrors.length) {
        step(`${app.name} 的脚本层报错`, scriptErrors.join("\n"));
        for (const message of scriptErrors) record(`${app.name} 脚本层报错：${message}`);
      }
      if (up && networkNotices.length) {
        for (const message of networkNotices) record(`${app.name} 请求失败（后端在跑）：${message}`);
      }

      await screenshot(`${app.port}-${app.name}`);
    }

    console.log(
      `\n=== 结论：${problems.length === 0 ? "全部通过" : `发现 ${problems.length} 个问题`} ===`,
    );
    if (problems.length) {
      for (const message of problems) console.log(`  - ${message}`);
      process.exitCode = 1;
    }
  } finally {
    /*
     * 收尾要**两样都收**。
     *
     * `ws` 是一条长连接的 WebSocket，不主动关掉它会把事件循环一直挂住 ——
     * 症状是"结论已经打出来了，进程却迟迟不结束"，在流水线里表现为**超时**
     * 而不是那行结论，排查方向会先指向"脚本卡住了"。
     */
    // `?.` 是必要的：调试端口没起来时 `ws` 还是 null，这里再抛一次会把
    // 真正的失败原因（端口没就绪）盖掉
    ws?.close();
    chrome.kill();
  }
}

if (SHOT_DIR) mkdirSync(SHOT_DIR, { recursive: true });

main()
  .catch((error) => {
    console.error(`\n验证脚本自身出错：${error.message}`);
    console.error(error.stack);
    process.exitCode = 1;
  })
  .finally(() => {
    /*
     * 兜底退出。
     *
     * 即使上面关掉了 WebSocket，只要还有任何一个句柄没释放，Node 就会一直等下去；
     * 而"结论正确但进程不结束"是**最难用的一类失败**（看日志一切正常）。
     * 这 500ms 既是给 stdout 冲刷留的余量，也是给正常收尾留的余量。
     */
    setTimeout(() => process.exit(process.exitCode ?? 0), 500);
  });
