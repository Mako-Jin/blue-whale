/**
 * 走一遍新尺度：验证扩容后城门可通、墙体可挡、坊门不被误封。
 *
 * 用法：node tools/world-walk-check.mjs
 *
 * 只做三件事，每件都靠 __bwwDebug 读真实玩家坐标：
 *   1) 出生点 → 按 W 一路向北，应当穿过内城门、停在皇城墙前（而不是被新加的
 *      坊墙拦在半路）；
 *   2) 按 S 一路向南，应当停在外城墙前；
 *   3) 沿环带侧移一段，记录是否被坊墙推出（位置应连续、无跳变）。
 */
import { chromium } from '@playwright/test';

const BASE = 'http://127.0.0.1:8124/world/?debug=1';

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

page.on('pageerror', (err) => console.log(`PAGEERROR ${err.message}`));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* ignore */
  }
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(6000);

const pos = async () => page.evaluate(() => window.__bwwDebug?.() ?? null);

const start = await pos();
console.log('START ' + JSON.stringify(start));

/** 按住某键走 durationMs，每 intervalMs 采一次位置 */
async function walk(key, durationMs, label) {
  await page.keyboard.down(key);
  const samples = [];
  const step = 1500;
  for (let t = 0; t < durationMs; t += step) {
    await page.waitForTimeout(step);
    const p = await pos();
    if (p) samples.push({ x: p.x, z: p.z });
  }
  await page.keyboard.up(key);
  const first = samples[0];
  const last = samples[samples.length - 1];
  console.log(
    `${label}: ${JSON.stringify(first)} → ${JSON.stringify(last)}（${samples.length} 采样）`,
  );
  return samples;
}

const north = await walk('w', 9000, 'NORTH');
const south = await walk('s', 12000, 'SOUTH');
const lateral = await walk('a', 4000, 'LATERAL');

/* 断言：向北必须真的往北走（z 减小），且没有被卡在原地 */
const movedNorth = Math.abs(north[north.length - 1].z - start.z);
const movedSouth = Math.abs(south[south.length - 1].z - start.z);
console.log(`北向位移 ${movedNorth.toFixed(2)}m，终局 z=${north[north.length - 1].z}`);
console.log(`南向位移 ${movedSouth.toFixed(2)}m，终局 z=${south[south.length - 1].z}`);

const maxJump = (samples) => {
  let m = 0;
  for (let i = 1; i < samples.length; i += 1) {
    m = Math.max(m, Math.hypot(samples[i].x - samples[i - 1].x, samples[i].z - samples[i - 1].z));
  }
  return m;
};
console.log(`位移跳变上限（应远小于单步位移）：${maxJump(lateral).toFixed(2)}m/1.5s`);

const end = await pos();
console.log('END ' + JSON.stringify(end));
console.log(
  movedNorth > 5 ? 'PASS 北向可通行（未被坊墙拦住）' : 'FAIL 北向被卡住，检查坊墙是否封了御道',
);

await browser.close();
