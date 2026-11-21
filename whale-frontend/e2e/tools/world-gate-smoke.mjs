// 城门楼地标（S5 / T5.10）冒烟：网关在城里的化身，只有地标、没有内页。
//
// 这一批与前面九殿的分工：
//   九殿都是"能进殿看数"——三件套是 overview 契约 + 屏风页 + 厅配置；
//   城门楼反过来：网关是无状态代理，没有业务表也没有 overview 契约，硬开一个端点
//   就得让全城唯一入口多一个白名单暴露面，还要重建镜像。所以它**只做"被认出"**。
//
// 于是能自动验的就三件事，也正是玩家会踩的三个坑：
//   ① 走到门洞前有没有提示？提示写的必须不是"进入"（没有内页，写进入就是骗人）；
//   ② 按 E 会不会静默无反应？门洞里按 E 应当"过门"（开传送门列表）并说明"这是通道"，
//      且**不能顺带进入任何屏风**（屏风优先，说明优先级没写反）；
//   ③ 城图上四门有没有缺口、门楼有没有标出来？城图尺度必须等于布局数据
//      ——这里曾经写死 v1 的 92/48/22/68，扩城到 110/58/30/76 后整圈城墙跑到画布外。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s5-shots';
mkdirSync(OUT, { recursive: true });

let pass = 0;
let fail = 0;
const check = (name, ok, detail) => {
  if (ok) {
    pass += 1;
    console.log(`PASS ${name}`);
  } else {
    fail += 1;
    console.log(`FAIL ${name}${detail === undefined ? '' : ` :: ${JSON.stringify(detail)}`}`);
  }
};

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1024, height: 640 } });

const errors = [];
page.on('pageerror', (e) => errors.push(`PAGEERROR ${String(e).slice(0, 200)}`));
page.on('console', (m) => {
  /* 本环境不起后端：overview / metrics 一路 404 是预期的，
     只筛掉"资源加载失败"，其余控制台错误照收。 */
  if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) {
    errors.push(m.text().slice(0, 200));
  }
});

await page.addInitScript(() => {
  try {
    localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 无所谓：没档案也只是多弹一次捏脸面板 */
  }
});
await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
/* 首帧要编译着色器，等场景真的开始动（软渲染下 4s 不够） */
await page.waitForFunction(() => Boolean(window.__bwwLandmarks), null, { timeout: 60000 });
await page.waitForTimeout(2500);

const hintNow = () =>
  page.evaluate(() => {
    const el = document.getElementById('bww-portal-hint');
    return {
      shown: el?.classList.contains('bww-show') ?? false,
      text: (el?.textContent ?? '').replace(/\s+/g, ' ').trim(),
    };
  });

/* ---- 1. 地标表：四座有匾额的外城门楼 + 两座内城门洞 ---- */
const landmarks = await page.evaluate(() => window.__bwwLandmarks());
const gates = landmarks.gates;
check('地标表共 6 座城门（四外门 + 两内门）', gates.length === 6, gates.length);
const landmarksOnly = gates.filter((g) => g.landmark);
check(
  '只有四座外城门楼是"地标"（内城门洞无匾额，不冒充城门楼）',
  landmarksOnly.length === 4 && landmarksOnly.every((g) => g.tier === 'outer'),
  landmarksOnly.map((g) => g.nodeKey),
);
check(
  '四门门名与匾额一致',
  ['南薰門', '望江門', '朝陽門', '鯨津門'].every((n) => landmarksOnly.some((g) => g.name === n)),
  landmarksOnly.map((g) => g.name),
);
/* 坐标必须落在城墙线上：写死数字迟早与布局漂移，这里直接对着 110 核 */
const onWall = landmarksOnly.every(
  (g) => Math.abs(Math.abs(g.x === 0 ? g.z : g.x)) === 110,
);
check('四座门楼坐标落在外城墙线上（±110）', onWall, landmarksOnly.map((g) => [g.x, g.z]));

/* ---- 2. 出生点（0,66）不该被门楼提示占住：最近的地标门楼在 44m 外 ---- */
check('出生点不在门楼提示圈内', landmarks.near === null, landmarks.near);

/* ---- 3. 走到南薰門门洞前：提示出现，且写的是"过门"不是"进入" ---- */
await page.evaluate(() => window.__bwwWarp?.(0, 100));
await page.waitForTimeout(900);
const nearGate = await page.evaluate(() => window.__bwwLandmarks());
check('走到南薰門前识别到该地标', nearGate.near?.nodeKey === 'gate-nanxun', nearGate.near);
check('提示条出现', nearGate.hint.shown === true, nearGate.hint);
check('提示写明"按 E 过门"', /按 E 过门/.test(nearGate.hint.text), nearGate.hint.text);
check(
  '提示含门名与"通道非殿"（没有内页就说没有）',
  /南薰門/.test(nearGate.hint.text) && /通道非殿/.test(nearGate.hint.text),
  nearGate.hint.text,
);
check('提示里不出现"进入"字样', !/进入/.test(nearGate.hint.text), nearGate.hint.text);
await page.screenshot({ path: `${OUT}/80-gate-hint.png` });

/* ---- 4. 按 E：过门（开传送门列表）+ 一句说明，但不进入任何屏风 ---- */
await page.keyboard.press('KeyE');
await page.waitForTimeout(700);
const afterE = await page.evaluate(() => {
  const toast = document.getElementById('bww-toast');
  return {
    panelOpen: document.getElementById('bww-portal-panel')?.classList.contains('bww-open') ?? false,
    toastShown: toast?.classList.contains('bww-show') ?? false,
    toastTitle: document.getElementById('bww-toast-title')?.textContent ?? '',
    toastSub: document.getElementById('bww-toast-sub')?.textContent ?? '',
    engaged: document.body.dataset.screenEngaged ?? null,
  };
});
check('按 E 打开传送门列表（= 过门）', afterE.panelOpen === true, afterE.panelOpen);
check('同时弹出说明，标题点名门楼', /城门楼 · 南薰門/.test(afterE.toastTitle), afterE.toastTitle);
check('说明写清"通道，不是殿"', /通道，不是殿/.test(afterE.toastSub), afterE.toastSub);
check('门洞里按 E 不会误进任何屏风', afterE.engaged === null, afterE.engaged);
await page.screenshot({ path: `${OUT}/81-gate-e.png` });

/* 关掉面板，回到空地 */
await page.keyboard.press('Escape');
await page.waitForTimeout(500);

/* ---- 5. 走开：提示消失（否则会变成"挂在屏幕上的一块牌子"） ---- */
await page.evaluate(() => window.__bwwWarp?.(0, 86));
await page.waitForTimeout(900);
const far = await page.evaluate(() => window.__bwwLandmarks());
check('离门楼超过触发半径后提示消失', far.hint.shown === false, far.hint);

/* ---- 6. 提示优先级：屏风 > 传送门 > 城门楼（加第三条不能抢前两条） ---- */
await page.evaluate(() => window.__bwwWarp?.(-7.6, 65.8));
await page.waitForTimeout(800);
const atScreen = await hintNow();
check('屏风前的提示仍是"进入"（没被门楼抢走）', /或点击进入/.test(atScreen.text), atScreen);
/* 告示屏就在内城南门旁（8m），若门楼优先级写错，这里会变成"过门" */
check('屏风前不出现"过门"', !/过门/.test(atScreen.text), atScreen.text);

const portalStop = await page.evaluate(() => window.__bwwGuideStops?.()[0] ?? null);
if (portalStop) {
  await page.evaluate(({ x, z }) => window.__bwwWarp?.(x, z), portalStop);
  await page.waitForTimeout(800);
  const atPortal = await hintNow();
  check('传送门前的提示仍是"打开传送门"', /打开传送门/.test(atPortal.text), atPortal);
}

/* ---- 7. 城图：尺度来自布局数据，四门留缺口 ---- */
const map = await page.evaluate(() => window.__bwwMinimap());
check('城图外城半径 = 布局值 110（曾写死 92）', map.outer === 110, map.outer);
check('城图内城半径 = 布局值 58（曾写死 48）', map.inner === 58, map.inner);
check('城图皇城半径 = 布局值 30（曾写死 22）', map.royal === 30, map.royal);
check('城图环路半径 = 布局值 76（曾写死 68）', map.ring === 76, map.ring);
check('城图视野大于外城（否则城墙画在画布外）', map.half > map.outer, [map.half, map.outer]);
check('城图留了 10 处门洞（四外门 + 两内门 + 四皇城御道门）', map.openings === 10, map.openings);
check('城图标记四座门楼', map.gates.length === 4, map.gates.map((g) => g.name));

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
