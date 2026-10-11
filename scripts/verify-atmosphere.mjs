/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「大气与天气的现实映射」。
 *
 * 它验证的是单元测试**一条都测不到**的东西 —— 内核算得对，不等于画面上真的变了：
 *
 *   1. 天穹真的被画出来了（天空区域不再是那个写死的背景色）；
 *   2. **拨时刻，画面真的跟着变** —— 正午的蓝天、黄昏的暖霞、夜里的暗；
 *   3. 亮度随太阳高度角单调变化（正午 > 黄昏 > 夜），且黄昏的地平线偏暖；
 *   4. **时间流逝真的在走**，且速率对得上 `flow`（×3600 就该是 3600 倍）；
 *   5. **动态 IBL 不漏显存** —— 反复拨时刻触发几十次重烘，纹理数不持续增长。
 *
 * 第 5 条尤其重要：环境贴图重烘是这次改动里唯一"每次都会新建 GPU 资源"的地方，
 * 漏一次 dispose 就是一个缓慢的显存泄漏，而它在两三分钟内不会表现出任何症状。
 *
 * ## 为什么全篇按「帧」等，而不是按毫秒 `sleep`
 *
 * `setEnvironment()` 是**同步**改光和雾的，但画面要等下一帧才重绘。而这一版脚本跑在
 * 软件渲染（`--use-gl=swiftshader`）的无头浏览器里，**单帧实测要 ~2.2 秒**（约 0.5 fps）。
 * 于是"等 650 毫秒再截图"这种写法会稳定地截到上一帧 —— 症状是四个时刻的天空颜色几乎一样，
 * 看起来像"天穹没生效"，其实是**测量方法错了**。
 *
 * 所以等待一律走 `waitForFrames()`，判据来自舞台自己数出来的 `diagnostics().frames`。
 * 换到有 GPU 的机器上帧率上去了，脚本会自然变快，不必回头改常数。
 *
 * 同理，第 4 条测"时间流逝"时**必须把窗口对齐到帧边界**：不对齐的话，窗口起点落在
 * 某一帧的中间，而那一帧的 delta 是从上一帧算起的整段时长，会把速率算高一大截。
 *
 * 前置：三维环境应用已启动
 *   pnpm --filter platform-environment dev   (:3400)
 *
 * 用法：
 *   node scripts/verify-atmosphere.mjs                 # 只验证
 *   node scripts/verify-atmosphere.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/**
 * 浏览器路径自动探测。
 *
 * 不写死 Chrome：开发机上装的可能只有 Edge，而两者都是 Chromium 内核、
 * CDP 协议一致。写死路径的后果是"脚本在本机跑不起来"，而那种失败
 * 看起来像"验证失败"，会把排查方向带偏。
 */
const BROWSER_CANDIDATES = [
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  "C:/Users/84628/AppData/Local/Google/Chrome/Application/chrome.exe",
  "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
];
const BROWSER = BROWSER_CANDIDATES.find((candidate) => existsSync(candidate));
if (!BROWSER) {
  console.error("找不到可用的 Chromium 内核浏览器，候选路径：");
  BROWSER_CANDIDATES.forEach((candidate) => console.error(`  · ${candidate}`));
  process.exit(1);
}

const DEBUG_PORT = 9336;
const APP_URL = "http://127.0.0.1:3400/";
const SHOT_DIR = process.argv[2] ?? null;

const VIEWPORT = { width: 1440, height: 960 };
/**
 * 天空取样带：占**画布**高度的比例。
 *
 * 必须按画布的实际位置取，不能写死 `y = 0` —— 画布上方还有顶栏与场景选择器，
 * 写死坐标会截到顶栏的浅色背景，得到"所有时刻的天空都是 rgb(219,221,223)"这种
 * 看起来像"天穹没生效"、其实是**测量位置错了**的结果。
 */
const SKY_BAND_RATIO = 0.2;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const problems = [];
const record = (message) => {
  problems.push(message);
  console.log(`  · ${message}`);
};

/**
 * 兜底超时。
 *
 * CDP 的某个 await 一旦不返回，脚本就会**永远挂着** —— 而"永远挂着"比"报错"难查得多：
 * 没有输出、没有退出码，只能靠人盯着。这里强制给自己一个上限。
 *
 * 给到 5 分钟：软件渲染下整条流程要真的画出几十帧，每帧 ~2 秒，3 分钟会不够用。
 */
const HARD_TIMEOUT_MS = 300000;

function step(title, body) {
  console.log(`\n== ${title} ==\n${body}`);
}

async function waitForDebugger(timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`);
      if (res.ok) {
        return;
      }
    } catch {
      /* 还没起来 */
    }
    await sleep(300);
  }
  throw new Error("Chrome 调试端口未就绪");
}

async function main() {
  console.log(`使用浏览器：${BROWSER}`);

  const hardTimeout = setTimeout(() => {
    console.error(`\n超过 ${HARD_TIMEOUT_MS / 1000} 秒仍未完成，强制退出。已发现的问题：`);
    problems.forEach((item) => console.error(`  · ${item}`));
    // 用 `exit` 而不是 `exitCode`：CDP 的 WebSocket 与浏览器子进程句柄
    // 会让事件循环一直不空，设退出码是退不掉的
    process.exit(1);
  }, HARD_TIMEOUT_MS);

  const userDataDir = mkdtempSync(join(tmpdir(), "bw-atmosphere-"));
  const chrome = spawn(
    BROWSER,
    [
      "--headless=new",
      `--remote-debugging-port=${DEBUG_PORT}`,
      `--user-data-dir=${userDataDir}`,
      "--no-first-run",
      "--no-default-browser-check",
      "--no-proxy-server",
      // 无头环境必须走软件渲染，否则 WebGL 上下文创建不出来
      "--use-gl=swiftshader",
      "--enable-unsafe-swiftshader",
      `--window-size=${VIEWPORT.width},${VIEWPORT.height}`,
      // 只开一个空白页。要打开的地址由 `Page.navigate` 给 ——
      // 把 URL 写在命令行上，首次启动时它可能还没被处理，目标列表里就只有 about:blank
      "about:blank",
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    /**
     * 等**页面目标**出现，而不是查一次就断言。
     *
     * "调试端点先就绪、页面目标后出现"是常态（首次启动的 profile 初始化尤其慢）。
     * 只查一次会把纯粹的时序问题报成配置错误，把排查方向带偏。
     */
    async function findPageTarget(timeoutMs = 30000) {
      const deadline = Date.now() + timeoutMs;
      let seen = [];
      while (Date.now() < deadline) {
        try {
          seen = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) => r.json());
        } catch {
          seen = [];
        }
        const found = seen.find((target) => target.type === "page");
        if (found) {
          return found;
        }
        await sleep(400);
      }
      throw new Error(
        `30 秒内没等到页面目标。现有目标：${JSON.stringify(seen.map((t) => `${t.type}:${t.url}`))}`,
      );
    }

    const page = await findPageTarget();

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
        waiting.get(message.id)(message.result);
        waiting.delete(message.id);
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

    async function evaluate(expression) {
      const result = await send("Runtime.evaluate", {
        expression,
        returnByValue: true,
        awaitPromise: true,
      });
      return result.result?.value;
    }

    /**
     * 带重试的求值。
     *
     * 页面刚导航完时执行上下文可能还没建好，"Cannot find context" 这类失败
     * 属于时序问题而不是脚本错误 —— 直接报死会得到一条毫无信息量的失败。
     */
    async function evaluateWithRetry(expression, attempts = 10) {
      let lastError;
      for (let attempt = 0; attempt < attempts; attempt += 1) {
        try {
          return await evaluate(expression);
        } catch (error) {
          lastError = error;
          await sleep(500);
        }
      }
      throw lastError;
    }

    async function screenshot(name) {
      if (!SHOT_DIR) return;
      const shot = await send("Page.captureScreenshot", { format: "png" });
      writeFileSync(join(SHOT_DIR, `${name}.png`), Buffer.from(shot.data, "base64"));
    }

    /**
     * 读**画布上部一条带**的平均色。
     *
     * 两处讲究：
     * 1. 取样矩形来自画布的实际布局位置，而不是写死的屏幕坐标；
     * 2. 为什么要绕一圈用 CDP 截图 + 页面内解码，而不是直接在页面里读 WebGL 画布 ——
     *    渲染器的 `preserveDrawingBuffer` 是 `false`（持续动画不需要保留上一帧），
     *    一旦出了 `requestAnimationFrame` 回调，绘制缓冲就不可读了。
     */
    async function skyBand() {
      const canvasRect = await evaluate(`(() => {
        const canvas = document.querySelector(".scene-view__canvas");
        if (!canvas) return null;
        const rect = canvas.getBoundingClientRect();
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
      })()`);
      if (!canvasRect) {
        throw new Error("找不到 .scene-view__canvas —— 画布还没挂上");
      }

      const shot = await send("Page.captureScreenshot", {
        format: "png",
        clip: {
          x: canvasRect.x,
          y: canvasRect.y,
          width: canvasRect.width,
          height: Math.max(8, canvasRect.height * SKY_BAND_RATIO),
          scale: 0.5,
        },
      });
      return evaluate(`(async () => {
        const image = new Image();
        image.src = "data:image/png;base64,${shot.data}";
        await image.decode();
        const canvas = document.createElement("canvas");
        canvas.width = image.width;
        canvas.height = image.height;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(image, 0, 0);
        const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
        let r = 0, g = 0, b = 0, count = 0;
        // 同时估一个"明暗结构"：有云就该有起伏，纯色渐变会明显更平
        const luma = [];
        for (let i = 0; i < data.length; i += 4) {
          r += data[i]; g += data[i + 1]; b += data[i + 2];
          luma.push(0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2]);
          count += 1;
        }
        r /= count; g /= count; b /= count;
        const mean = luma.reduce((sum, v) => sum + v, 0) / luma.length;
        const variance = luma.reduce((sum, v) => sum + (v - mean) ** 2, 0) / luma.length;
        return { r, g, b, luma: mean, contrast: Math.sqrt(variance) };
      })()`);
    }

    const clockText = () =>
      evaluate(`document.querySelectorAll(".render-panel__field-value")[0]?.textContent?.trim() ?? ""`);
    const stageEnvironment = () =>
      evaluate(`JSON.parse(JSON.stringify(window.__SCENE_STAGE__.environment()))`);
    const diagnostics = () =>
      evaluate(`JSON.parse(JSON.stringify(window.__SCENE_STAGE__.diagnostics()))`);

    /**
     * 等舞台真的**画出 N 帧**。
     *
     * 这是全篇唯一可靠的等待原语：判据是舞台自己数的 `diagnostics().frames`，
     * 而不是"过了多少毫秒"。软件渲染下单帧要 2 秒，毫秒级等待会稳定截到上一帧；
     * 换到有 GPU 的机器上又是另一套数字 —— 只有按帧等才对两边都成立。
     */
    async function waitForFrames(count, timeoutMs = 60000) {
      const start = await diagnostics();
      const target = start.frames + count;
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        await sleep(120);
        const now = await diagnostics();
        if (now.frames >= target) {
          return now.frames - start.frames;
        }
      }
      // 超时不当成致命错误抛出：让外层按实际测到的数据去 `record` 一条问题，
      // 这样失败信息里能带上"到底走了几帧"，比一句 timeout 有用得多
      const stalled = await diagnostics();
      record(`等 ${count} 帧超时（${timeoutMs}ms 内只画了 ${stalled.frames - start.frames} 帧）`);
      return stalled.frames - start.frames;
    }

    /** 拨动滑块。React 的受控 input 必须走原生 setter + input 事件，直接改 value 不生效 */
    async function setSlider(index, value, frames = 2) {
      await evaluate(`(() => {
        const slider = document.querySelectorAll(".render-panel__range")[${index}];
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        setter.call(slider, String(${value}));
        slider.dispatchEvent(new Event("input", { bubbles: true }));
        return slider.value;
      })()`);
      /**
       * 等两帧而不是等固定时长，两个原因都必要：
       * 1. 画面要下一帧才用上新的光色与曝光；
       * 2. 环境贴图重烘受 `IBL_MIN_INTERVAL`（320ms）节流，**可能被当帧挡掉**、
       *    留到下一帧由 `frame()` 里的兜底检查补上 —— 只等一帧会漏掉最后那次变化。
       */
      await waitForFrames(frames);
    }

    /** 点击倍速按钮（暂停 / ×600 / ×3600） */
    async function clickFlowChip(label) {
      await evaluate(`(() => {
        const chips = [...document.querySelectorAll(".render-panel__chip")];
        const target = chips.find((chip) => chip.textContent.trim() === ${JSON.stringify(label)});
        if (!target) throw new Error("找不到倍速按钮：" + ${JSON.stringify(label)});
        target.click();
        return true;
      })()`);
    }

    /**
     * 测时间流逝速率用的窗口：**同时**满足「至少若干帧」与「至少若干秒」才收。
     *
     * 两个条件都要，各自堵一个坑：
     * - 只要帧数：软件渲染下 3 帧就是 12 秒，没问题；但有 GPU 时 3 帧只有 50 毫秒，
     *   而轮询本身就有几十毫秒的延迟，测出来的窗口会被延迟撑大，速率算出来偏低一大截。
     * - 只要秒数：还没画出第二帧，`localHours` 根本没往前走。
     *
     * 轮询间隔取 80 毫秒（而不是 `waitForFrames` 的 120 毫秒）是为了让"检测到边界"的
     * 滞后尽量小 —— 那点滞后是速率误差的唯一来源。
     */
    async function waitForFlowWindow(minFrames, minSeconds, capMs) {
      const start = await diagnostics();
      const t0 = Date.now();
      while (Date.now() - t0 < capMs) {
        await sleep(80);
        const now = await diagnostics();
        if (now.frames - start.frames >= minFrames && Date.now() - t0 >= minSeconds * 1000) {
          return { frames: now.frames - start.frames };
        }
      }
      const stalled = await diagnostics();
      record(
        `时间流逝测量窗口超时（${capMs}ms）：只画了 ${stalled.frames - start.frames} 帧、` +
          `过了 ${((Date.now() - t0) / 1000).toFixed(1)} 秒`,
      );
      return { frames: stalled.frames - start.frames };
    }

    await send("Page.enable");
    await send("Runtime.enable");
    await send("Log.enable");
    // 导航放在监听装好之后，这样加载期的报错一条都不会漏
    await send("Page.navigate", { url: APP_URL });
    /**
     * 先等导航落地再挂等待。
     *
     * 反过来做的话，那个"等舞台就绪"的 Promise 会挂在**即将被销毁的执行上下文**里 ——
     * 它既不会 resolve 也不会 reject，脚本从此静默挂死。这正是上一版卡了 7 分钟的原因。
     */
    await sleep(2500);

    /* ---------------- 1. 等舞台就绪 ---------------- */

    await evaluateWithRetry(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 60000;
      const timer = setInterval(() => {
        if (window.__SCENE_STAGE__ && window.__SCENE_STAGE__.environment()) {
          clearInterval(timer);
          resolve("ready");
        } else if (Date.now() > deadline) {
          clearInterval(timer);
          reject(new Error("舞台未就绪"));
        }
      }, 150);
    })`);

    const initial = await stageEnvironment();
    step(
      "1. 舞台就绪",
      `时刻 ${initial.localHours.toFixed(2)}h · 日期 ${initial.dayOfYear} · 经纬度 ${initial.latitude}, ${initial.longitude} · ` +
        `倍速 ${initial.flow}`,
    );
    if (Math.abs(initial.longitude - 116.4074) > 0.001) {
      record(`默认经度不是北京（${initial.longitude}）`);
    }

    /* ---------------- 2. 四个时刻的取样 ---------------- */

    /** 按键锚点：12:00 正午、19:30 日落前后、00:00 午夜、5:30 日出前 */
    const SAMPLES = [
      { key: "noon", minutes: 12 * 60, label: "正午 12:00", hint: "blue" },
      { key: "golden", minutes: 18 * 60, label: "黄金时刻 18:00", hint: "warm" },
      { key: "sunset", minutes: 19 * 60 + 30, label: "日落 19:30", hint: "warm" },
      { key: "night", minutes: 0, label: "午夜 00:00", hint: "dark" },
    ];

    const samples = {};
    for (const sample of SAMPLES) {
      await setSlider(0, sample.minutes);
      const environment = await stageEnvironment();
      const band = await skyBand();
      samples[sample.key] = { ...band, environment };
      step(
        `2.${sample.key} ${sample.label}`,
        `舞台时刻 ${environment.localHours.toFixed(2)}h · 天空平均色 rgb(${band.r.toFixed(0)}, ${band.g.toFixed(0)}, ${band.b.toFixed(0)}) · ` +
          `亮度 ${band.luma.toFixed(1)} · 明暗起伏 ${band.contrast.toFixed(1)}`,
      );
      await screenshot(`atmosphere-${sample.key}`);

      if (Math.abs(environment.localHours - sample.minutes / 60) > 0.02) {
        record(`拨到 ${sample.label} 后舞台时刻是 ${environment.localHours.toFixed(2)}h —— 滑块与舞台不同步`);
      }
    }

    /* ---------------- 3. 天空真的是"天"，不是背景色 ---------------- */

    const noon = samples.noon;
    step(
      "3. 天穹是否真的画出来了",
      `正午天空 rgb(${noon.r.toFixed(0)}, ${noon.g.toFixed(0)}, ${noon.b.toFixed(0)})，` +
        `旧的写死背景是 #342f3d（亮度约 53）`,
    );
    if (!(noon.b > noon.r)) {
      record(`正午的天空不是偏蓝的（r=${noon.r.toFixed(0)} b=${noon.b.toFixed(0)}）—— 天穹可能没生效`);
    }
    if (!(noon.luma > 110)) {
      record(`正午天空亮度只有 ${noon.luma.toFixed(1)} —— 更像那个写死的深色背景，而不是被照亮的天空`);
    }
    if (!(noon.contrast > 4)) {
      record(`正午天空几乎没有明暗起伏（${noon.contrast.toFixed(1)}）—— 云层可能没生效`);
    }

    /* ---------------- 4. 亮度随太阳高度角变化 ---------------- */

    const sunset = samples.sunset;
    const night = samples.night;
    const warmth = (band) => band.r / Math.max(1, band.b);
    step(
      "4. 亮度与色温的现实映射",
      `亮度：正午 ${noon.luma.toFixed(1)} → 黄金时刻 ${samples.golden.luma.toFixed(1)} → ` +
        `日落 ${sunset.luma.toFixed(1)} → 午夜 ${night.luma.toFixed(1)}；` +
        `暖色比（红/蓝）：正午 ${warmth(noon).toFixed(2)} · 日落 ${warmth(sunset).toFixed(2)}`,
    );
    if (!(noon.luma > samples.golden.luma && samples.golden.luma > night.luma)) {
      record("天空亮度没有随太阳高度角单调下降");
    }
    if (!(night.luma < noon.luma * 0.5)) {
      record(`午夜天空不够暗（${night.luma.toFixed(1)} vs 正午 ${noon.luma.toFixed(1)}）`);
    }
    if (!(warmth(sunset) > warmth(noon) * 1.15)) {
      record(
        `日落的地平线没有比正午更暖（${warmth(sunset).toFixed(2)} vs ${warmth(noon).toFixed(2)}）—— 霞光可能没算对`,
      );
    }

    /* ---------------- 5. 时间流逝 ---------------- */

    await setSlider(0, 6 * 60);
    await clickFlowChip("×3600");
    /**
     * **先把窗口对齐到帧边界**再开始计时。
     *
     * 单击按钮之后立刻读一次，很可能正处在某一帧的中途；而那一帧的 `getDelta()`
     * 是从上一帧算起的整段时长（软件渲染下有 2~4 秒），这段时长里有一大半发生在
     * 点按钮**之前**。用它当窗口起点，算出来的速率会凭空高出一大截。
     * 先等一帧，窗口起点就落在"flow 已经生效、且上一帧刚结束"的干净位置上。
     */
    await waitForFrames(1);

    const flowStart = await stageEnvironment();
    const flowStartFrames = await diagnostics();
    const flowStartWall = Date.now();
    await waitForFlowWindow(3, 3, 22000);
    const flowEnd = await stageEnvironment();
    const flowEndFrames = await diagnostics();
    const flowWallSeconds = (Date.now() - flowStartWall) / 1000;

    /**
     * `localHours` 收在 `[0, 24)`，跨午夜会回绕 —— 上机实测第一次就栽在这里：
     * 面板从 `13.03h` 走到 `5.22h`，直接相减得到 `-7.82`，看起来像"时间倒流"，
     * 实际是往前推了 16.19 小时。推进量在窗口内恒为正，所以取模补回即可。
     * 窗口被 `waitForFlowWindow` 的 22 秒上限兜住，推进量不会超过 24 小时，
     * 取模不存在"少算一圈"的歧义。
     */
    const rawAdvance = flowEnd.localHours - flowStart.localHours;
    const advancedHours = ((rawAdvance % 24) + 24) % 24;
    /** 实测速率 = 推进的模拟秒数 / 真实秒数，应当与 `flow` 相等 */
    const measuredFlow = (advancedHours * 3600) / flowWallSeconds;
    const clockAfter = await clockText();

    step(
      "5. 时间流逝（×3600）",
      `对齐到帧边界后实测 ${flowEndFrames.frames - flowStartFrames.frames} 帧 · ` +
        `${flowStart.localHours.toFixed(2)}h → ${flowEnd.localHours.toFixed(2)}h` +
        (rawAdvance < 0 ? `（跨午夜，实际推进 ${advancedHours.toFixed(3)} 小时）` : '') +
        ` · ${flowWallSeconds.toFixed(2)} 秒推进 ${advancedHours.toFixed(3)} 小时 · ` +
        `实测速率 ${measuredFlow.toFixed(0)}× （目标 3600×）· 面板显示 ${clockAfter}`,
    );
    if (!(advancedHours > 0)) {
      record(`×3600 档下时间完全没有推进 —— 时间流逝没生效`);
    }
    /**
     * 容差 ±25% 在这个尺度上足够说明问题：真正会出错的写法（用了固定步长、
     * 把 delta 当成帧数、单位换错、把倍速漏乘）偏出来都是数倍到数十倍，
     * 不会卡在 25% 里。剩下的这点误差只有"检测帧边界的轮询滞后"一个来源。
     */
    if (!(measuredFlow > 2700 && measuredFlow < 4500)) {
      record(`时间流逝速率不对：实测 ${measuredFlow.toFixed(0)}×，应当接近 3600×`);
    }
    if (!(flowEnd.flow === 3600)) {
      record(`倍速没有被设成 3600，而是 ${flowEnd.flow}`);
    }
    if (!/^\d{2}:\d{2}$/.test(clockAfter)) {
      record(`面板时钟格式不对：「${clockAfter}」`);
    }
    await screenshot("atmosphere-flowing");

    // 回到暂停，免得后面持续触发重烘
    await clickFlowChip("暂停");
    await waitForFrames(1);

    /* ---------------- 6. 反复拨时刻：动态 IBL 不能漏显存 ---------------- */

    const before = await diagnostics();
    /**
     * 拨 12 次够了。
     *
     * 每拨一次至少触发一次重烘（单帧 2 秒，远超 `IBL_MIN_INTERVAL` 的 320ms 节流窗口），
     * 若旧的环境贴图没释放，纹理数会**线性**涨 12 张 —— 这在 +4 的容差下必然暴露。
     * 而每多拨一次就要多花 ~2.4 秒（软件渲染），24 次会把整条流程拖过超时线。
     */
    for (let index = 0; index < 12; index += 1) {
      await setSlider(0, (index * 120) % 1440, 1);
    }
    const after = await diagnostics();

    step(
      "6. 动态 IBL 的显存回落",
      `拨了 12 次时刻 · 纹理 ${before.textures} → ${after.textures} · 几何体 ${before.geometries} → ${after.geometries} · ` +
        `绘制调用 ${after.drawCalls}`,
    );
    if (after.textures > before.textures + 4) {
      record(
        `反复重烘之后纹理数从 ${before.textures} 涨到 ${after.textures} —— 旧的环境贴图可能没释放`,
      );
    }
    if (after.geometries > before.geometries + 4) {
      record(`反复重烘之后几何体数从 ${before.geometries} 涨到 ${after.geometries}`);
    }

    /* ---------------- 7. 逐个场景：outdoor 都该有天空，真空不该有 ---------------- */

    /**
     * 先把时刻拨回正午 —— 下面要在**多个场景**里读天空色。
     *
     * 环境（`EnvironmentState`）是**跨场景延续**的：它不属于任何一个场景，切场景不会重置它。
     * 所以这里设一次，之后每个场景都停在这个时刻。这既是便利，也是"环境为什么不能塞进
     * `SceneOptions`"的现场注解 —— 选项随场景重置，环境不随。
     */
    await setSlider(0, 12 * 60);

    /**
     * 逐个切过窗口里的场景。
     *
     * 单测只证明得了 `createAtmosphere` **存在**；"天穹真的被画出来了"只有真实浏览器能证。
     * 契约既然是"`outdoor` 就该有天空"，验证就不该只挑古建筑一个 —— 三个室外场景全过一遍，
     * 非室外的那个反过来验"不该有"。只测一家的话，另外两家把 `sky` 传成 `false`
     * 或者干脆忘了挂，脚本会一路绿灯。
     */
    const SCENES_TO_CHECK = [
      { id: "ancient-architecture", title: "古建筑", sky: true },
      { id: "primeval-jungle", title: "原始丛林", sky: true },
      { id: "modern-office", title: "现代写字楼", sky: true },
      { id: "spacecraft", title: "飞船", sky: false },
    ];

    /** 遍历开始前的纹理数。切场景不该让它持续上涨 —— 大气有两份天穹，漏释放最可能在这里露头 */
    const texturesBeforeScenes = (await diagnostics()).textures;

    for (const scene of SCENES_TO_CHECK) {
      await evaluate(
        `document.querySelector('.scene-card[data-scene-id="${scene.id}"]')?.click(), true`,
      );
      /**
       * 切场景要等 3 帧而不是 1 帧：点击只是触发 React 换 `scene`，舞台随后要
       * 释放旧场景、建新场景、上传纹理、强制重烘一次环境贴图 —— 这些跨好几帧。
       * 软件渲染下 3 帧约 7 秒，换来的是"截图里一定是新场景"。
       */
      await waitForFrames(3);

      const band = await skyBand();
      const sceneDiagnostics = await diagnostics();
      step(
        `7. ${scene.title}`,
        `天空平均色 rgb(${band.r.toFixed(0)}, ${band.g.toFixed(0)}, ${band.b.toFixed(0)}) · ` +
          `亮度 ${band.luma.toFixed(1)} · 纹理 ${sceneDiagnostics.textures} · ` +
          `几何体 ${sceneDiagnostics.geometries}`,
      );

      if (scene.sky) {
        /**
         * 判据用**亮度**而不是色相。
         *
         * "偏蓝"在丛林里会被树冠搅浑 —— 画面上部不全是天，还有锥形的树冠压进来。
         * 而"亮得起来"是三个室外场景在正午的共同特征。阈值 80 上距实测的白天天空、
         * 下距兜底背景色（`#342f3d` ≈ 53）都留足了余量，两边都不会误判。
         */
        if (!(band.luma > 80)) {
          record(
            `${scene.title} 标了 outdoor，天空区域亮度却只有 ${band.luma.toFixed(1)} —— 天穹没画出来`,
          );
        }
      } else if (!(band.luma < 60)) {
        record(
          `${scene.title} 不是 outdoor，天空区域却有 ${band.luma.toFixed(1)} 的亮度 —— 真空里不该有天光`,
        );
      }

      if (sceneDiagnostics.textures > texturesBeforeScenes + 6) {
        record(`切到${scene.title}之后纹理数累积到 ${sceneDiagnostics.textures}`);
      }
      await screenshot(`atmosphere-scene-${scene.id}`);
    }

    /* ---------------- 8. 再切回古建筑：大气必须能重新挂上 ---------------- */

    /**
     * 第 7 节已经"进入"过古建筑一次，这里特意**再回来**一次。
     *
     * 两者测的不是一回事：第 7 节验的是"第一次进场能画出天穹"，这一节验的是
     * `dispose()` → `create()` 走了一整圈之后**还能**画出天穹。
     * 切场景必须能来回 —— 只能去不能回，是"释放时把不该清的东西清了"这一类 bug 的特征。
     */
    await evaluate(
      `document.querySelector('.scene-card[data-scene-id="ancient-architecture"]')?.click(), true`,
    );
    await waitForFrames(3);
    const back = await skyBand();
    const backDiagnostics = await diagnostics();
    step(
      "8. 再切回古建筑",
      `天空平均色 rgb(${back.r.toFixed(0)}, ${back.g.toFixed(0)}, ${back.b.toFixed(0)}) · 亮度 ${back.luma.toFixed(1)} · ` +
        `纹理 ${backDiagnostics.textures}`,
    );
    if (!(back.b > back.r && back.luma > 100)) {
      record(`切回古建筑之后天穹没有恢复（亮度 ${back.luma.toFixed(1)}）`);
    }
    if (backDiagnostics.textures > texturesBeforeScenes + 6) {
      record(`来回切场景之后纹理数累积到了 ${backDiagnostics.textures}`);
    }
    await screenshot("atmosphere-back");

    /* ---------------- 9. 汇总 ---------------- */

    step("9. 浏览器侧错误", errors.length ? errors.join("\n---\n") : "（无）");
    if (errors.length) {
      record(`浏览器侧报错 ${errors.length} 条`);
    }

    console.log(`\n=== 结论：${problems.length ? `发现 ${problems.length} 个问题` : "全部通过"} ===`);
    problems.forEach((item) => console.log(`  · ${item}`));

    ws.close();
  } finally {
    clearTimeout(hardTimeout);
    chrome.kill();
  }
}

if (SHOT_DIR) {
  mkdirSync(SHOT_DIR, { recursive: true });
}

main()
  .then(() => {
    // 显式 exit：CDP 的 WebSocket 与浏览器子进程句柄会让事件循环一直不空
    process.exit(problems.length ? 1 : 0);
  })
  .catch((error) => {
    console.error("\n验证脚本执行失败：", error);
    process.exit(1);
  });
