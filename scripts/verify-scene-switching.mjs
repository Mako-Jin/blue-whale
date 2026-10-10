/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「多场景切换」。
 *
 * 它验证的是三件在无 GPU 环境里根本测不出来的事：
 *   1. 每个场景都能真正渲染出画面（绘制调用与三角面数都非零）；
 *   2. 切场景确实换了场景图，而不是只换了个标题（各场景的渲染规模互不相同）；
 *   3. **反复切换不会把显存吃光** —— 同一个场景在第二轮切回来时，
 *      GPU 上的几何体数量应与第一轮一致而不是翻倍。这一条只能靠
 *      `stage.diagnostics()` 的数字来断言，看画面是看不出来的：
 *      泄漏要攒到上下文上限才会突然白屏，那时已经晚了。
 *
 * 前置：三维环境应用已启动
 *   pnpm --filter platform-environment dev   (:3400)
 *
 * 用法：
 *   node scripts/verify-scene-switching.mjs                 # 只验证
 *   node scripts/verify-scene-switching.mjs <截图目录>       # 验证并逐场景截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9334;
const APP_URL = "http://127.0.0.1:3400/";
const SHOT_DIR = process.argv[2] ?? null;

/** 每个场景切过去后给多少毫秒让渲染稳定（首帧要编译着色器，比后续帧慢得多）。 */
const SETTLE_MS = 600;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function step(title, body) {
  console.log(`\n== ${title} ==\n${body}`);
}

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
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-scenes-"));
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
      APP_URL,
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) => r.json());
    const page = targets.find((t) => t.type === "page" && t.url.includes("127.0.0.1:3400"));
    if (!page) {
      throw new Error(
        "未找到三维环境应用页面：" + JSON.stringify(targets.map((t) => t.url)),
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
        /**
         * favicon 缺失是已记录的未做项（见 ADR-0013「明确未做」），
         * 它既不影响功能也不影响渲染，混进错误里只会训练出"错误列表可以忽略"的习惯。
         */
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

    async function readDiagnostics() {
      return JSON.parse(
        await evaluate("JSON.stringify(window.__SCENE_STAGE__.diagnostics())"),
      );
    }

    async function capture(name) {
      if (!SHOT_DIR) return;
      const shot = await send("Page.captureScreenshot", { format: "png" });
      const { writeFileSync } = await import("node:fs");
      writeFileSync(join(SHOT_DIR, `${name}.png`), Buffer.from(shot.data, "base64"));
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /* ---------------- 等舞台就绪 ---------------- */

    await evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 15000;
      const timer = setInterval(() => {
        if (window.__SCENE_STAGE__) { clearInterval(timer); resolve("ready"); }
        else if (Date.now() > deadline) { clearInterval(timer); reject(new Error("舞台未就绪")); }
      }, 120);
    })`);

    const cards = JSON.parse(
      await evaluate(`JSON.stringify(
        [...document.querySelectorAll(".scene-card")].map((card) => ({
          id: card.dataset.sceneId,
          title: card.querySelector(".scene-card__title")?.textContent ?? "",
          active: card.getAttribute("aria-pressed") === "true",
        })),
      )`),
    );

    step(
      "1. 场景选择器渲染出的卡片",
      cards.map((card) => `${card.title}${card.active ? "（当前）" : ""}`).join("  ·  "),
    );

    const initial = await readDiagnostics();
    step(
      "2. 首屏场景与渲染状态",
      `sceneId=${initial.sceneId} · 绘制 ${initial.drawCalls} · 三角面 ${initial.triangles} · 几何体 ${initial.geometries} · 着色器 ${initial.programs}`,
    );

    /* ---------------- 两轮遍历，比对是否累积 ---------------- */

    // 先切到最后一个，保证第一轮的第一个点击一定是一次真切换
    await evaluate(
      `document.querySelector('.scene-card[data-scene-id="${cards[cards.length - 1].id}"]')?.click(), true`,
    );
    await sleep(SETTLE_MS);

    const rounds = [new Map(), new Map()];

    for (let round = 0; round < rounds.length; round += 1) {
      for (const card of cards) {
        await evaluate(
          `document.querySelector('.scene-card[data-scene-id="${card.id}"]')?.click(), true`,
        );

        const deadline = Date.now() + 6000;
        let current = await readDiagnostics();
        while (current.sceneId !== card.id && Date.now() < deadline) {
          await sleep(120);
          current = await readDiagnostics();
        }
        if (current.sceneId !== card.id) {
          throw new Error(`切换到 ${card.id} 超时，当前仍是 ${current.sceneId}`);
        }

        await sleep(SETTLE_MS);
        rounds[round].set(card.id, await readDiagnostics());
        if (round === 0) {
          await capture(card.id);
        }
      }
    }

    const first = rounds[0];
    step(
      "3. 各场景的渲染规模（第一轮）",
      cards
        .map((card) => {
          const d = first.get(card.id);
          return `${card.title.padEnd(6, "　")} 绘制 ${String(d.drawCalls).padStart(3)} · 三角面 ${String(
            d.triangles,
          ).padStart(7)} · 几何体 ${String(d.geometries).padStart(3)} · 着色器 ${d.programs}`;
        })
        .join("\n"),
    );

    // 渲染规模互不相同 —— 这是"真的换了场景图"而不是"只改了标题"的证据
    const signatures = new Set(
      cards.map((card) => {
        const d = first.get(card.id);
        return `${d.drawCalls}/${d.triangles}`;
      }),
    );
    step(
      "4. 切换是否真的换了场景图",
      signatures.size === cards.length
        ? `通过：${cards.length} 个场景的绘制规模各不相同`
        : `可疑：只有 ${signatures.size} 种规模，说明有场景可能只是改了名字`,
    );

    const leaks = [];
    const table = cards.map((card) => {
      const before = first.get(card.id).geometries;
      const after = rounds[1].get(card.id).geometries;
      const verdict = after <= before ? "无累积" : "⚠ 增长了";
      if (after > before) {
        leaks.push(`${card.id}: ${before} → ${after}`);
      }
      return `${card.title.padEnd(6, "　")} 第一轮 ${String(before).padStart(3)} → 第二轮 ${String(
        after,
      ).padStart(3)}   ${verdict}`;
    });
    step("5. 显存释放：同一场景第二轮切回来的 GPU 几何体数", table.join("\n"));

    const problems = [];
    if (leaks.length) {
      problems.push(`GPU 几何体在第二轮变多了：\n  ${leaks.join("\n  ")}`);
    }
    cards.forEach((card) => {
      const d = first.get(card.id);
      if (d.triangles <= 0) {
        problems.push(`${card.id} 渲染出的三角面数为 0（画面可能是空的）`);
      }
    });

    step("6. 浏览器侧错误", errors.length ? errors.join("\n---\n") : "（无）");
    if (errors.length) {
      problems.push(`浏览器侧报错 ${errors.length} 条`);
    }

    console.log(
      `\n=== 结论：${problems.length ? `发现 ${problems.length} 个问题` : "全部通过"} ===`,
    );
    problems.forEach((item) => console.log(`  · ${item}`));

    ws.close();
    process.exitCode = problems.length ? 1 : 0;
  } finally {
    chrome.kill();
  }
}

if (SHOT_DIR) {
  mkdirSync(SHOT_DIR, { recursive: true });
}

main().catch((error) => {
  console.error("\n验证脚本执行失败：", error);
  process.exitCode = 1;
});
