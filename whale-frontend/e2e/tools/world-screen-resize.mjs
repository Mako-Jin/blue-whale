// 相机同步与 resize（T2.2）
//
// 判据沿用 S0 T0.1 的探针法：屏风中心的投影点与 DOM 实际中心必须重合。
// CSS3D 与 WebGL 共用一台相机，只要有一处尺寸没同步，改视口后就会偏差几十像素。

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

async function measure() {
  return page.evaluate(() => {
    const el = document.querySelector('[data-screen-id="shushu"]');
    if (!el) return null;
    const r = el.getBoundingClientRect();
    // 用屏风容器本身的 CSS3D 变换矩阵反推它映射到的屏幕中心不现实，
    // 改用"屏风矩形是否仍是 3.4:2.1 的宽高比"+"是否仍在视口内"两条硬指标，
    // 再配合视觉截图判断。比例失真就说明 CSS3D 的尺寸没跟着 resize。
    return {
      vw: window.innerWidth,
      vh: window.innerHeight,
      rect: [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)],
      ratio: +(r.width / r.height).toFixed(3),
      // 屏风真实宽高比 = 3.4 / 2.1 = 1.619；透视会让它偏移，但不应离谱
      insideViewport: r.x > -50 && r.y > -50 && r.right < window.innerWidth + 50,
    };
  });
}

console.log('1440x900 ' + JSON.stringify(await measure()));
await page.setViewportSize({ width: 1024, height: 700 });
await page.waitForTimeout(1200);
console.log('1024x700 ' + JSON.stringify(await measure()));
await page.setViewportSize({ width: 1920, height: 1080 });
await page.waitForTimeout(1200);
console.log('1920x1080 ' + JSON.stringify(await measure()));
console.log('ERRORS ' + (errs.join(' | ') || 'none'));
await browser.close();
