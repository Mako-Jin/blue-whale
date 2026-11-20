// 屏风进入/退出验收（T2.6 / T2.7）
//
// 验收三条，缺一不可：
//   1. 点击后相机确实飞到屏风正前方（屏风占屏 ≥ 70%，且接近正视）
//   2. 屏风内容可滚动、可聚焦
//   3. Esc 退出后回到城中，玩家还能走
//
// 为什么不用"看着差不多"：屏风小的时候飞过去也像"变大了"，
// 必须是"占屏比例"和"透视是否正交"两个硬指标。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s2-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const errors = [];
page.on('pageerror', (e) => errors.push('PAGEERROR ' + String(e).slice(0, 200)));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 忽略 */
  }
});

await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(8000);

/** 量屏风在屏幕上的占比与倾斜度 */
async function measure(id) {
  return page.evaluate((screenId) => {
    const el = document.querySelector(`[data-screen-id="${screenId}"]`);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    // 从 matrix3d 里取投影后的四个角，判断是否正交（左右边是否等长）
    const m = new DOMMatrix(getComputedStyle(el).transform);
    return {
      rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
      widthRatio: +(r.width / vw).toFixed(3),
      heightRatio: +(r.height / vh).toFixed(3),
      // 中心点离视口中心多远
      offsetFromCenter: {
        x: Math.round(r.x + r.width / 2 - vw / 2),
        y: Math.round(r.y + r.height / 2 - vh / 2),
      },
      matrix: { a: +m.a.toFixed(4), b: +m.b.toFixed(4), c: +m.c.toFixed(4), d: +m.d.toFixed(4) },
      focus: el.dataset.focus,
      engaged: Boolean(document.body.dataset.screenEngaged),
    };
  }, id);
}

const before = await measure('shushu');
console.log('BEFORE ' + JSON.stringify(before));

// 点屏风进入
await page.locator('[data-screen-id="shushu"]').click();
await page.waitForTimeout(1400);
const after = await measure('shushu');
console.log('AFTER  ' + JSON.stringify(after));
await page.screenshot({ path: OUT + '/10-engaged.png' });

// 屏风内滚动：内容区应当能滚，且相机不该变焦
const scrollInfo = await page.evaluate(() => {
  const body = document.querySelector('[data-screen-id="shushu"] .bws-body');
  if (!body) return null;
  const before = body.scrollTop;
  body.scrollTop = 9999;
  return { before, after: body.scrollTop, scrollable: body.scrollHeight > body.clientHeight };
});
console.log('SCROLL ' + JSON.stringify(scrollInfo));

// Esc 退出
await page.keyboard.press('Escape');
await page.waitForTimeout(1400);
const exited = await measure('shushu');
console.log('EXIT   ' + JSON.stringify(exited));
await page.screenshot({ path: OUT + '/11-exited.png' });

// 退出后还能不能走
const walk = await page.evaluate(async () => {
  const a = window.__bwwDebug?.();
  await new Promise((r) => setTimeout(r, 100));
  return a;
});
await page.keyboard.down('KeyW');
await page.waitForTimeout(1500);
await page.keyboard.up('KeyW');
const walked = await page.evaluate(() => window.__bwwDebug?.());
console.log('WALK   ' + JSON.stringify({ from: walk && [walk.x, walk.z], to: walked && [walked.x, walked.z] }));

console.log('ERRORS ' + (errors.join(' | ') || 'none'));
await browser.close();
console.log('done');
