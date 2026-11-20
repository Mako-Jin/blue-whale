// 屏风系统（S2）冒烟：确认 CSS3D 层真的挂上了屏风，并读出关键几何。
//
// 为什么不用肉眼看截图：屏风在 3D 里，位置错了、被墙挡住、或者根本没渲染，
// 三种情况在截图里都是"看不见"，但修法完全不同。这里一次性把三种可能分开。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s2-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(msg.text());
});
page.on('pageerror', (err) => errors.push('PAGEERROR ' + err.message));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 忽略 */
  }
});

await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

const probe = await page.evaluate(() => {
  const root = document.getElementById('bww-css3d');
  const screens = root ? [...root.querySelectorAll('.bws-screen')] : [];
  const occluder = document.getElementById('bww-occluder');
  return {
    hasCss3dRoot: Boolean(root),
    css3dSize: root ? `${root.clientWidth}x${root.clientHeight}` : null,
    css3dChildren: root ? root.children.length : 0,
    css3dInnerHTMLHead: root ? root.innerHTML.slice(0, 200) : null,
    screenCount: screens.length,
    screens: screens.map((el) => {
      const r = el.getBoundingClientRect();
      return {
        id: el.dataset.screenId,
        rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
        transform: el.style.transform.slice(0, 90),
        text: (el.querySelector('.bws-title')?.textContent ?? '').slice(0, 20),
        bodyLen: (el.querySelector('.bws-body')?.textContent ?? '').length,
        opacity: getComputedStyle(el).opacity,
        display: getComputedStyle(el).display,
      };
    }),
    occluderCanvas: occluder ? occluder.children.length : 0,
    player: window.__bwwDebug ? window.__bwwDebug() : null,
  };
});

console.log(JSON.stringify(probe, null, 2));
console.log('ERRORS:', errors.slice(0, 8).join('\n'));

await page.screenshot({ path: OUT + '/00-spawn.png' });

// 转身面向屏风看能不能看见：屏风在出生点正前方偏两侧
await page.evaluate(() => {
  window.__bwwLook?.(0, 0.32, 12);
});
await page.waitForTimeout(1200);
await page.screenshot({ path: OUT + '/01-look-south.png' });

await browser.close();
console.log('done');
