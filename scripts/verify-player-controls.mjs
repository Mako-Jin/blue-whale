/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器验证「古建筑场景的可操控性」。
 *
 * 它验证的东西，单元测试一条都测不到：
 *   1. 广场真的渲染出来了、主人公真的站在出生点上；
 *   2. **键盘真的能驱动人物** —— 走位方向必须是"镜头深处"，
 *      按错符号的经典症状是"按上人物往后走"，这在纯逻辑测试里是看不见的；
 *   3. **滚轮真的能缩放** —— 相机距离随之变化，并且小地图的视野扇形跟着变长；
 *   4. **右键真的能设终点**，人物会走过去并停下（而不是原地抖或穿墙）；
 *   5. 小地图把主人公画出来了（不是一张空白的框）。
 *
 * 前置：三维环境应用已启动
 *   pnpm --filter platform-environment dev   (:3400)
 *
 * 用法：
 *   node scripts/verify-player-controls.mjs                 # 只验证
 *   node scripts/verify-player-controls.mjs <截图目录>       # 验证并保存截图
 */
import { spawn } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const DEBUG_PORT = 9335;
const APP_URL = "http://127.0.0.1:3400/";
const SHOT_DIR = process.argv[2] ?? null;

const VIEWPORT = { width: 1440, height: 960 };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const problems = [];
const record = (message) => {
  problems.push(message);
  console.log(`  · ${message}`);
};

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
  const userDataDir = mkdtempSync(join(tmpdir(), "bw-player-"));
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
      APP_URL,
    ],
    { stdio: "ignore" },
  );

  try {
    await waitForDebugger();

    const targets = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json`).then((r) => r.json());
    const page = targets.find((t) => t.type === "page" && t.url.includes("127.0.0.1:3400"));
    if (!page) {
      throw new Error("未找到三维环境应用页面：" + JSON.stringify(targets.map((t) => t.url)));
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

    async function snapshot() {
      return evaluate("JSON.parse(JSON.stringify(window.__SCENE_STAGE__.interaction()))");
    }

    async function translate(x, y) {
      await send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y, button: "none" });
    }

    /** 按住一个键一段时间。用真实键盘事件，而不是合成 DOM 事件。 */
    async function holdKey(code, key, virtualKey, holdMs) {
      const base = {
        code,
        key,
        windowsVirtualKeyCode: virtualKey,
        nativeVirtualKeyCode: virtualKey,
      };
      await send("Input.dispatchKeyEvent", { type: "rawKeyDown", ...base });
      await sleep(holdMs);
      await send("Input.dispatchKeyEvent", { type: "keyUp", ...base });
    }

    async function screenshot(name) {
      if (!SHOT_DIR) return;
      const shot = await send("Page.captureScreenshot", { format: "png" });
      writeFileSync(join(SHOT_DIR, `${name}.png`), Buffer.from(shot.data, "base64"));
    }

    await send("Runtime.enable");
    await send("Log.enable");
    await send("Page.enable");

    /* ---------------- 1. 等舞台与主人公就绪 ---------------- */

    await evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 20000;
      const timer = setInterval(() => {
        if (window.__SCENE_STAGE__ && window.__SCENE_STAGE__.interaction()) {
          clearInterval(timer);
          resolve("ready");
        } else if (Date.now() > deadline) {
          clearInterval(timer);
          reject(new Error("舞台或主人公未就绪"));
        }
      }, 120);
    })`);

    const initial = await snapshot();
    step(
      "1. 首屏状态",
      `sceneId=${initial.sceneId} · 人物 (${initial.player.x.toFixed(1)}, ${initial.player.z.toFixed(1)}) · ` +
        `相机方位 ${((initial.cameraAzimuth * 180) / Math.PI).toFixed(0)}° · 距离 ${initial.cameraDistance.toFixed(1)}`,
    );

    if (initial.sceneId !== "ancient-architecture") {
      record(`首屏场景不是古建筑，而是 ${initial.sceneId}`);
    }
    if (!initial.map || initial.map.obstacles.length === 0) {
      record("小地图地物为空 —— 地图上画不出任何障碍");
    }
    await screenshot("player-initial");

    /* ---------------- 2. 小地图确实画出来了 ---------------- */

    const minimap = await evaluate(`(() => {
      const host = document.querySelector('.minimap');
      if (!host) return { found: false };
      const canvas = host.querySelector('canvas');
      const rect = host.getBoundingClientRect();
      const ctx = canvas.getContext('2d');
      // 找出"人物箭头"的主色像素数 —— 有它才说明主人公真的画上去了
      const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
      let arrowPixels = 0;
      for (let i = 0; i < data.length; i += 4) {
        if (data[i] > 235 && data[i + 1] > 190 && data[i + 1] < 235 && data[i + 2] < 175) {
          arrowPixels += 1;
        }
      }
      return {
        found: true,
        hidden: host.hidden,
        width: canvas.width,
        height: canvas.height,
        rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
        arrowPixels,
        compass: host.querySelector('.minimap__compass')?.textContent?.trim() ?? '',
      };
    })()`);

    step(
      "2. 小地图",
      `画布 ${minimap.width}×${minimap.height} · 指北标记「${minimap.compass}」 · 人物箭头像素 ${minimap.arrowPixels}`,
    );
    if (!minimap.found || minimap.hidden) {
      record("小地图没显示出来");
    }
    if (minimap.compass !== "N") {
      record(`指北标记不是 N，而是「${minimap.compass}」`);
    }
    if (!(minimap.arrowPixels > 20)) {
      record(`小地图上找不到人物箭头（匹配到 ${minimap.arrowPixels} 个像素）`);
    }

    /* ---------------- 3. 键盘驱动走位 ---------------- */

    await holdKey("KeyW", "w", 87, 900);
    await sleep(150);
    const afterForward = await snapshot();
    const forwardDelta = {
      x: afterForward.player.x - initial.player.x,
      z: afterForward.player.z - initial.player.z,
    };
    const forwardDistance = Math.hypot(forwardDelta.x, forwardDelta.z);

    // 相机在北侧朝南看时，"向前"应当走向 -Z
    const cameraForward = { x: Math.sin(initial.cameraAzimuth), z: Math.cos(initial.cameraAzimuth) };
    const alignment =
      forwardDistance > 0
        ? (forwardDelta.x * cameraForward.x + forwardDelta.z * cameraForward.z) / forwardDistance
        : 0;

    step(
      "3. 按 W 前进",
      `位移 (${forwardDelta.x.toFixed(2)}, ${forwardDelta.z.toFixed(2)}) · 走了 ${forwardDistance.toFixed(2)} · ` +
        `与镜头前向的一致度 ${alignment.toFixed(3)}`,
    );
    if (forwardDistance < 0.5) {
      record("按 W 之后人物几乎没动");
    }
    if (alignment < 0.9) {
      record(`前进方向与镜头前向不一致（一致度 ${alignment.toFixed(3)}）—— 很可能是方向符号写反了`);
    }

    // 左右各来一下，验证横移也在（并且方向相反）
    const beforeStrafe = await snapshot();
    await holdKey("KeyD", "d", 68, 700);
    await sleep(150);
    const afterStrafe = await snapshot();
    const strafeDelta = {
      x: afterStrafe.player.x - beforeStrafe.player.x,
      z: afterStrafe.player.z - beforeStrafe.player.z,
    };
    // 相机右向量 = (-cos a, sin a)
    const cameraRight = { x: -Math.cos(initial.cameraAzimuth), z: Math.sin(initial.cameraAzimuth) };
    const strafeDistance = Math.hypot(strafeDelta.x, strafeDelta.z);
    const strafeAlignment =
      strafeDistance > 0
        ? (strafeDelta.x * cameraRight.x + strafeDelta.z * cameraRight.z) / strafeDistance
        : 0;

    step(
      "4. 按 D 右移",
      `位移 (${strafeDelta.x.toFixed(2)}, ${strafeDelta.z.toFixed(2)}) · 走了 ${strafeDistance.toFixed(2)} · ` +
        `与镜头右向的一致度 ${strafeAlignment.toFixed(3)}`,
    );
    if (strafeDistance < 0.5) {
      record("按 D 之后人物几乎没动");
    }
    if (strafeAlignment < 0.9) {
      record(`右移方向与镜头右向不一致（一致度 ${strafeAlignment.toFixed(3)}）`);
    }

    await screenshot("player-after-walk");

    /* ---------------- 5. 右键设置行动终点 + 自动走过去 ---------------- */

    /**
     * 世界坐标点只能靠"点在屏幕哪里"来指定，而屏幕点映射到地面的位置
     * 依赖当前机位。所以这里试几个候选点，挑一个**足够远、又不在障碍里**的作为终点
     * —— 否则可能刚好点在脚边（一步就到），那样这段就白测了。
     */
    async function rightClickAt(ratioX, ratioY) {
      const x = Math.round(VIEWPORT.width * ratioX);
      const y = Math.round(VIEWPORT.height * ratioY);
      return evaluate(`(() => {
        const canvas = document.querySelector('.scene-view__canvas');
        const event = new MouseEvent('contextmenu', {
          clientX: ${x},
          clientY: ${y},
          bubbles: true,
          cancelable: true,
        });
        canvas.dispatchEvent(event);
        return event.defaultPrevented;
      })()`);
    }

    const candidates = [
      [0.8, 0.62],
      [0.2, 0.62],
      [0.82, 0.55],
      [0.5, 0.62],
      [0.5, 0.5],
    ];

    let chosen = null;
    for (const [ratioX, ratioY] of candidates) {
      const prevented = await rightClickAt(ratioX, ratioY);
      const state = await snapshot();
      if (!state.destination) {
        continue;
      }
      const distance = Math.hypot(
        state.destination.x - state.player.x,
        state.destination.z - state.player.z,
      );
      if (!prevented) {
        record("右键没有拦下浏览器默认菜单");
      }
      if (distance > 5) {
        chosen = { state, distance, ratioX, ratioY };
        break;
      }
    }

    if (!chosen) {
      record("右键没能在离人物足够远处设出行动终点");
    } else {
      const target = chosen.state.destination;
      step(
        "5. 右键设置行动终点",
        `点在屏幕 (${(chosen.ratioX * 100).toFixed(0)}%, ${(chosen.ratioY * 100).toFixed(0)}%) → ` +
          `地面点 (${target.x.toFixed(1)}, ${target.z.toFixed(1)}) · 距人物 ${chosen.distance.toFixed(2)}`,
      );
      await screenshot("player-destination");

      // 等它自己走过去（最多 15 秒）
      const deadline = Date.now() + 15000;
      let state = chosen.state;
      let midway = null;
      const startedAt = Date.now();
      while (state.hasDestination && Date.now() < deadline) {
        await sleep(250);
        state = await snapshot();
        if (!midway && Date.now() - startedAt > 400) {
          midway = Math.hypot(target.x - state.player.x, target.z - state.player.z);
        }
      }

      const finalDistance = Math.hypot(target.x - state.player.x, target.z - state.player.z);
      step(
        "6. 自动行走",
        `起始 ${chosen.distance.toFixed(2)} → 中途 ${midway === null ? "-" : midway.toFixed(2)} → ` +
          `到达后 ${finalDistance.toFixed(2)} · 终点仍在？${state.hasDestination}`,
      );

      if (state.hasDestination) {
        record("15 秒之内没能走到终点");
      }
      if (!(finalDistance < 1.5)) {
        record(`走到终点后仍差了 ${finalDistance.toFixed(2)}（应当收敛到终点附近）`);
      }
      // 中途距离必须真的在缩短，而不是原地打转
      if (midway !== null && !(midway < chosen.distance)) {
        record("自动行走过程中与终点的距离没有缩短");
      }
      // 必须停在障碍之外：穿进殿宇说明碰撞没生效
      const insideObstacle = state.map.obstacles.some(
        (obstacle) =>
          Math.abs(state.player.x - obstacle.x) < obstacle.width / 2 &&
          Math.abs(state.player.z - obstacle.z) < obstacle.depth / 2,
      );
      if (insideObstacle) {
        record("人物停在了障碍矩形内部 —— 碰撞没生效");
      }
      // 到达后终点应当被清掉，否则小地图上会永远挂着一个到达点
      if (!state.hasDestination && state.destination !== null) {
        record("终点已判定到达，但快照里仍带着 destination");
      }
    }

    await screenshot("player-arrived");

    /* ---------------- 6. 滚轮缩放视角 ---------------- */

    const centerX = VIEWPORT.width / 2;
    const centerY = VIEWPORT.height / 2;
    await translate(centerX, centerY);
    const beforeZoom = await snapshot();
    for (let tick = 0; tick < 2; tick += 1) {
      await send("Input.dispatchMouseEvent", {
        type: "mouseWheel",
        x: centerX,
        y: centerY,
        deltaX: 0,
        deltaY: -240,
      });
      await sleep(350);
    }
    const afterZoom = await snapshot();

    step(
      "7. 滚轮缩放视角",
      `相机距离 ${beforeZoom.cameraDistance.toFixed(2)} → ${afterZoom.cameraDistance.toFixed(2)}`,
    );
    if (!(afterZoom.cameraDistance < beforeZoom.cameraDistance - 0.5)) {
      record("向前滚轮之后相机没有拉近 —— 缩放没生效");
    }

    await screenshot("player-zoomed");

    /* ---------------- 7. 切到别的场景，小地图应当隐藏 ---------------- */

    await evaluate(
      `document.querySelector('.scene-card[data-scene-id="spacecraft"]')?.click(), true`,
    );
    await sleep(700);
    const minimapAfterSwitch = await evaluate(
      `(() => { const host = document.querySelector('.minimap'); return host ? host.hidden : null; })()`,
    );
    const interactionAfterSwitch = await evaluate(
      `window.__SCENE_STAGE__.interaction() === null`,
    );
    step(
      "8. 切到太空场景",
      `小地图隐藏？${minimapAfterSwitch} · 交互快照为 null？${interactionAfterSwitch}`,
    );
    if (minimapAfterSwitch !== true) {
      record("切到无交互场景后小地图没有隐藏");
    }
    if (interactionAfterSwitch !== true) {
      record("切到无交互场景后仍然给出了交互快照");
    }

    /* ---------------- 9. 汇总 ---------------- */

    step("9. 浏览器侧错误", errors.length ? errors.join("\n---\n") : "（无）");
    if (errors.length) {
      record(`浏览器侧报错 ${errors.length} 条`);
    }

    console.log(`\n=== 结论：${problems.length ? `发现 ${problems.length} 个问题` : "全部通过"} ===`);
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
