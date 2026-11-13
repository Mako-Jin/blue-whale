/**
 * 右键自动寻路冒烟 —— 点下去真的会走、绕得开城墙、走得进城门、能被打断。
 *
 * <p>为什么必须用**真鼠标右键**：这个功能最可能的坏法是"事件没绑上"或"被浏览器
 * 右键菜单吃掉"，两者都不报错——直接调 `__bwwWalkTo()` 只能验算路，验不到这条。
 * 所以 [3] 用 `__bwwScreenOf()` 把世界点投成屏幕坐标，再真点一下。</p>
 *
 * <p>为什么必须验跨门洞：全城有三层城墙，门洞只在四条边中点。若 A* 退化或膨胀过头，
 * 角色会卡在外城墙内表面反复磨——此时"点了没反应"看起来像没实现功能。
 * 负向断言用 [6] 的"按方向键必须夺回控制权"兜住。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-path.mjs
 */

import { chromium } from '@playwright/test';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/';

let passed = 0;
let failed = 0;

function ok(condition, label) {
  if (condition) {
    passed += 1;
    console.log(`  PASS ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL ${label}`);
  }
}

const browser = await chromium.launch({
  channel: 'msedge',
  // 这里**不**强制 swiftshader：软件渲染只有 2~3fps，而游戏主循环把单帧 dt 钳在 0.05s，
  // 于是"游戏里的 1 秒"要跑掉真实 6 秒以上，跨城用例根本等不到头（早期就是这么假失败的）。
  // 实测本机 GPU 路径 50+fps；万一某台机器没有 GPU，这个参数保证它退化而不是崩。
  args: ['--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', (error) => errors.push(String(error)));

await page.addInitScript(() => {
  localStorage.setItem(
    'bww.avatar.v1',
    JSON.stringify({
      name: '赶路人',
      height: 1,
      build: 1,
      faceWidth: 1,
      skin: '#e8c4a0',
      robe: '#2b4a63',
      hair: '#15110f',
    }),
  );
});

const debug = () => page.evaluate(() => window.__bwwDebug());
const pathState = () => page.evaluate(() => window.__bwwPath());
const toastText = () => page.evaluate(() => {
  const el = document.getElementById('bww-toast');
  if (!el) return { shown: false, text: '' };
  return { shown: el.classList.contains('bww-show'), text: el.textContent ?? '' };
});
const warp = async (x, z) => {
  await page.evaluate(([px, pz]) => window.__bwwWarp(px, pz), [x, z]);
  await page.waitForTimeout(320);
};

/**
 * 等它自己停下（抵达或受阻），并在页面内按 120ms 采样轨迹。
 *
 * <p>用 waitForFunction 而不是"睡固定时长"：帧率低的机器上游戏时间会慢于真实时间，
 * 固定睡 8 秒可能连一半路都没走完，测试就变成看运气的。轨迹放页面里采，
 * 免得每 160ms 一轮 IPC 自己把帧率又压低一截。</p>
 */
async function walkUntilStop(limitMs = 45000) {
  await page.evaluate(() => {
    window.__e2eTrace = [];
    clearInterval(window.__e2eTimer);
    window.__e2eTimer = setInterval(() => {
      const p = window.__bwwDebug();
      window.__e2eTrace.push({ x: p.x, z: p.z });
    }, 120);
  });
  let stopped = true;
  try {
    await page.waitForFunction(() => window.__bwwPath().active === false, {
      timeout: limitMs,
      polling: 150,
    });
  } catch {
    stopped = false;
  }
  const trace = await page.evaluate(() => {
    clearInterval(window.__e2eTimer);
    return window.__e2eTrace ?? [];
  });
  const finalState = await debug();
  trace.push(finalState);
  return { trace, stopped, path: await pathState() };
}

await page.goto(`${BASE}?debug=1`, { waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => typeof window.__bwwDebug === 'function', { timeout: 30000 });
await page.waitForTimeout(1400);

console.log('\n[1] 探针与初始状态');
const initialPath = await pathState();
ok(initialPath !== null, '寻路探针可用');
ok(initialPath?.active === false, '初始不在自动行走');
const still = await debug();
await page.waitForTimeout(1100);
const stillAfter = await debug();
// 负向：没人让它走，它就不能动 —— 否则"自动寻路"其实是"自动漂移"
ok(Math.hypot(stillAfter.x - still.x, stillAfter.z - still.z) < 0.05, '不设目的地时原地不动');

console.log('\n[2] 算路：点宫殿顶上要能落到殿前空地');
await warp(0, 66);
const ontoPalace = await page.evaluate(() => window.__bwwWalkTo(26.02, 62.82));
ok(ontoPalace === true, '点在宫殿上仍能起步（自动改到最近空地）');
const palaceToast = await toastText();
ok(palaceToast.text.includes('已改道'), `提示已改道（实际：${palaceToast.text}）`);
const palacePath = await pathState();
ok(
  Math.hypot(palacePath.goalX - 26.02, palacePath.goalZ - 62.82) > 3,
  `落点确实被挪到殿外（${palacePath.goalX}, ${palacePath.goalZ}）`,
);
await page.keyboard.press('Escape');
await page.waitForTimeout(250);
ok((await pathState()).active === false, 'Esc 停止');

console.log('\n[3] 真鼠标右键（事件是否真的绑在画布上）');
await warp(0, 66);
await page.evaluate(() => window.__bwwLook(0, 0.5, 18));
await page.waitForTimeout(300);
const target = { x: 0, z: 44 };
const screen = await page.evaluate(([x, z]) => window.__bwwScreenOf(x, z), [target.x, target.z]);
ok(screen?.front === true, '目标点在相机前方（可投影）');
await page.mouse.click(screen.x, screen.y, { button: 'right' });
await page.waitForTimeout(320);
const afterClick = await pathState();
ok(afterClick.active === true, `右键一下就开始走（目标 ${afterClick.goalX}, ${afterClick.goalZ}）`);
ok(
  Math.hypot(afterClick.goalX - target.x, afterClick.goalZ - target.z) < 2.5,
  '落点就在右键点的位置附近',
);
// 点了要有回执：光环落在落点上，否则用户不知道"到底认没认这一下"
const markAfterClick = await page.evaluate(() => window.__bwwMarker());
ok(markAfterClick?.visible === true, '目的地出现光环标记');
ok(
  Math.hypot(markAfterClick.x - afterClick.goalX, markAfterClick.z - afterClick.goalZ) < 1.5,
  `光环落在落点上（${markAfterClick.x}, ${markAfterClick.z}）`,
);
const clickWalk = await walkUntilStop(20000);
const arrived = clickWalk.trace[clickWalk.trace.length - 1];
ok(clickWalk.stopped, '走完自己停了（不是一直抽在原地）');
ok(
  Math.hypot(arrived.x - afterClick.goalX, arrived.z - afterClick.goalZ) < 1.6,
  `抵达落点 差 ${Math.hypot(arrived.x - afterClick.goalX, arrived.z - afterClick.goalZ).toFixed(2)}m`,
);
const moved = Math.hypot(arrived.x - 0, arrived.z - 66);
ok(moved > 15, `真的走了 ${moved.toFixed(1)}m`);
await page.waitForTimeout(800);
const markArrived = await page.evaluate(() => window.__bwwMarker());
ok(markArrived?.visible === false, '抵达后光环自行淡出（不会一直挂在那儿）');

console.log('\n[4] 跨三层城墙：广场 → 皇城内');
await warp(0, 66);
// 主殿（数枢殿）座标 z=-2、半深 10.8，z=6 其实在殿内；这里点殿前的空地
await page.evaluate(() => window.__bwwWalkTo(0, 13));
await page.waitForTimeout(200);
const cross = await walkUntilStop(25000);
ok((await pathState()).active === false || cross.path.total > 0, '路径已建立');
const last = cross.trace[cross.trace.length - 1];
ok(Math.abs(last.z - 13) < 2.5, `进到皇城墙内（z=${last.z}）`);
ok(Math.abs(last.z) < 22, '确实穿过了皇城墙（z=22）而没被挡在外面');
// 内墙(z=48)、皇城墙(z=22) 都在 x=0 的门洞正中，全程 |x| 应远小于门洞半宽 8.5
const maxX = Math.max(...cross.trace.map((p) => Math.abs(p.x)));
ok(maxX < 6, `全程贴着御道走（|x| 最大 ${maxX.toFixed(2)}m，门洞半宽 8.5m）`);

console.log('\n[5] 斜穿全城：外城东南 → 外城西北（必须绕行）');
await warp(76, 76);
await page.evaluate(() => window.__bwwWalkTo(-76, -60));
await page.waitForTimeout(200);
const diag = await walkUntilStop(45000);
const diagLast = diag.trace[diag.trace.length - 1];
ok(diag.stopped, '绕行后正常停下');
ok(Math.hypot(diagLast.x + 76, diagLast.z + 60) < 2.5, `抵达西北角（${diagLast.x}, ${diagLast.z}）`);

console.log('\n[6] 打断：方向键夺回控制权 / Esc 取消');
await warp(0, 66);
await page.evaluate(() => window.__bwwWalkTo(0, -60));
await page.waitForTimeout(400);
ok((await pathState()).active === true, '先进入自动行走');
await page.keyboard.down('KeyW');
await page.waitForTimeout(260);
await page.keyboard.up('KeyW');
await page.waitForTimeout(220);
ok((await pathState()).active === false, '按方向键即停止自动行走');

await page.evaluate(() => window.__bwwWalkTo(0, -60));
await page.waitForTimeout(400);
await page.keyboard.press('Escape');
await page.waitForTimeout(260);
ok((await pathState()).active === false, 'Esc 取消寻路');
// 取消后还有一点惯性滑行（速度是插值衰减的），先等它收尾再测"是否真的不动了"
await page.waitForTimeout(900);
const frozen = await debug();
await page.waitForTimeout(800);
const frozenAfter = await debug();
const drift = Math.hypot(frozenAfter.x - frozen.x, frozenAfter.z - frozen.z);
ok(drift < 0.15, `取消后彻底停住（残余位移 ${drift.toFixed(3)}m）`);

console.log('\n[7] 收尾');
ok(errors.length === 0, `全程无页面错误${errors.length ? `：${errors[0]}` : ''}`);

await browser.close();
console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
