// 屏风接近提示与 E 键进入（T2.5 的可用性补充）
//
// 屏风光"能点"不够：3D 里没人知道一块布告板可以走进去。
// 这条验收三件事：走到近处提示出现、按 E 能进、走远了提示消失。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s2-shots';
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', args: ['--use-angle=d3d11','--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errs = [];
page.on('pageerror', (e) => errs.push(String(e).slice(0,160)));
await page.addInitScript(() => { try { localStorage.setItem('bww.avatar.v1', JSON.stringify({name:'舰长'})); } catch {} });
await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(8000);

// 走到告示屏风前 3m
await page.evaluate(() => { window.__bwwWarp?.(-7.6, 65.8); });
await page.waitForTimeout(1500);
const hint1 = await page.evaluate(() => {
  const el = document.getElementById('bww-portal-hint');
  return { text: el?.textContent?.trim(), shown: el?.classList.contains('bww-show') };
});
console.log('NEAR_HINT ' + JSON.stringify(hint1));
await page.screenshot({ path: OUT + '/50-hint.png' });

// 按 E 应当进入屏风
await page.keyboard.press('KeyE');
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged || null,
  focus: document.querySelector('[data-screen-id="notice"]')?.dataset.focus,
  parent: document.querySelector('[data-screen-id="notice"]')?.parentElement?.id,
}));
console.log('E_ENTER ' + JSON.stringify(engaged));

// 远处（城墙边）应当没有屏风提示
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
await page.evaluate(() => { window.__bwwWarp?.(0, 95); });
await page.waitForTimeout(1200);
const hint2 = await page.evaluate(() => {
  const el = document.getElementById('bww-portal-hint');
  return { text: el?.textContent?.trim(), shown: el?.classList.contains('bww-show') };
});
console.log('FAR_HINT ' + JSON.stringify(hint2));
console.log('ERRORS ' + (errs.join(' | ') || 'none'));
await browser.close();
