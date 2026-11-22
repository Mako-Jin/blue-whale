// 滚轮仲裁验收（T2.9）
//
// 判据：在屏风上滚轮，屏风的屏幕矩形**不允许变化**（相机没被拉近/推远）；
// 在空地滚轮，矩形必须变化（相机确实在变焦）。
// 两条都要过——只做前者会把相机滚轮整个废掉，那也是 bug。

import { chromium } from '@playwright/test';

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errs = [];
page.on('pageerror', (e) => errs.push(String(e).slice(0, 160)));
await page.addInitScript(() => {
  try {
    localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 隐私模式下 localStorage 可能抛错，忽略即可 */
  }
});
await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(8000);

const rectOf = () =>
  page.evaluate(() => {
    const el = document.querySelector('[data-screen-id="notice"]');
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return [Math.round(r.width), Math.round(r.height)];
  });

// 站到屏风前，让屏风占一定面积
await page.evaluate(() => {
  window.__bwwWarp?.(-7.6, 66.2);
  window.__bwwLook?.(0, 0.14, 5);
});
await page.waitForTimeout(1800);

const box = await page.locator('[data-screen-id="notice"]').boundingBox();
const r0 = await rectOf();
// 在屏风中心滚三次（向下 = 拉远）
await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
for (let i = 0; i < 3; i += 1) {
  await page.mouse.wheel(0, 240);
  await page.waitForTimeout(120);
}
await page.waitForTimeout(500);
const r1 = await rectOf();
console.log(
  'ON_SCREEN ' +
    JSON.stringify({ before: r0, after: r1 }) +
    ' → ' +
    (r0 && r1 && r0[0] === r1[0] ? 'PASS 相机未变焦' : 'FAIL 屏风滚轮带动了相机'),
);

// 挪到空地（视口右上角上空）再滚
await page.mouse.move(700, 300);
for (let i = 0; i < 3; i += 1) {
  await page.mouse.wheel(0, 240);
  await page.waitForTimeout(120);
}
await page.waitForTimeout(500);
const r2 = await rectOf();
console.log(
  'ON_GROUND ' +
    JSON.stringify({ before: r1, after: r2 }) +
    ' → ' +
    (r1 && r2 && r1[0] !== r2[0] ? 'PASS 相机变焦正常' : 'FAIL 相机滚轮失效'),
);

console.log('ERRORS ' + (errs.join(' | ') || 'none'));
await browser.close();
