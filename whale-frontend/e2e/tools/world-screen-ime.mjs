// IME 修复验收（T2.10）与贴脸静室（T2.11）
//
// 中文候选框是操作系统级 UI，自动化测不了它的位置。但候选框飘走的**根因**是
// 元素带了 matrix3d 祖先、layout 与视觉脱节——这一条可以精确断言：
//   进入屏风后，输入框的祖先链里不允许出现 matrix3d；退出后必须还原。
// 另外验证输入框可聚焦、可输入、退出后屏风回到 3D 容器。
//
// 选择器用 .bwsp-search 而不是 .bws-input：S5 起数枢殿这块屏风是通用业务页，
// 里面同时有一个搜索框和一个状态下拉框（都是 .bws-input），只按 .bws-input 选会命中两个。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s2-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', args: ['--use-angle=d3d11','--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errs = [];
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)));
await page.addInitScript(() => { try { localStorage.setItem('bww.avatar.v1', JSON.stringify({name:'舰长'})); } catch {} });
await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(8000);

const inspect = () => page.evaluate(() => {
  const input = document.querySelector('[data-screen-id="shushu"] .bwsp-search');
  const screenEl = document.querySelector('[data-screen-id="shushu"]');
  if (!input || !screenEl) return null;
  // 走到根的祖先链，找出带 3D 变换的
  const chain = [];
  let el = input.parentElement;
  let has3d = false;
  while (el && el !== document.documentElement) {
    const t = getComputedStyle(el).transform;
    const in3d = t && t.startsWith('matrix3d');
    chain.push({ tag: el.tagName, cls: String(el.className).slice(0, 26), transform: t.slice(0, 24), in3d });
    if (in3d) has3d = true;
    el = el.parentElement;
  }
  const r = input.getBoundingClientRect();
  const sr = screenEl.getBoundingClientRect();
  return {
    has3dAncestor: has3d,
    ancestorCount: chain.length,
    // 输入框是否落在屏风矩形内（平面化定位是否贴对）
    inputInsideScreen: r.left >= sr.left - 2 && r.right <= sr.right + 2,
    inputRect: [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)],
    screenRect: [Math.round(sr.x), Math.round(sr.y), Math.round(sr.width), Math.round(sr.height)],
    parentOfScreen: screenEl.parentElement?.id || '(none)',
  };
});

console.log('OUTSIDE ' + JSON.stringify(await inspect()));

await page.locator('[data-screen-id="shushu"]').click();
await page.waitForTimeout(1500);
const engaged = await inspect();
console.log('ENGAGED ' + JSON.stringify(engaged));
await page.screenshot({ path: OUT + '/40-engaged-input.png' });

// 聚焦并输入（fill 走的是 value 赋值，绕开 IME，但能验证焦点与事件链路）
const input = page.locator('[data-screen-id="shushu"] .bwsp-search');
await input.click();
await page.waitForTimeout(300);
const focusedInfo = await page.evaluate(() => {
  const el = document.activeElement;
  return { tag: el?.tagName, cls: String(el?.className).slice(0, 30), isInput: el?.tagName === 'INPUT' };
});
console.log('FOCUS ' + JSON.stringify(focusedInfo));

await input.fill('蓝鲸城');
const typed = await input.inputValue();
console.log('TYPED ' + JSON.stringify(typed));
await page.screenshot({ path: OUT + '/41-typed.png' });

await page.keyboard.press('Escape');
await page.waitForTimeout(1500);
const after = await inspect();
console.log('EXIT ' + JSON.stringify(after));

console.log('ERRORS ' + (errs.join(' | ') || 'none'));
await browser.close();
console.log('done');
