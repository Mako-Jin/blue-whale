/**
 * 放大截取城图（小地图 canvas），用于人工核对 12 坊布局。
 *
 * 用法：node tools/world-minimap-shot.mjs [输出目录]
 *
 * 相机 dist 上限 48 / pitch 上限 1.15rad，游戏里看不全 220m 见方的外城，
 * 所以「12 坊是否等距一圈、是否压墙」只能靠城图核对。这里把 canvas 的 CSS
 * 尺寸放大到 720 逻辑像素，配合 deviceScaleFactor=4 出 2880px 实像素图。
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = process.argv[2] ?? 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s1-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 4 });

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 隐私模式忽略 */
  }
});

await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(7000);

// canvas 内部分辨率是 180×180，放大 CSS 尺寸只增加实像素、不增加信息量，
// 所以这里同时把 width/height 属性一起提上去，逼 minimap 按新尺寸重绘。
await page.evaluate(() => {
  const c = document.getElementById('bww-minimap');
  if (!c) return;
  c.style.width = '720px';
  c.style.height = '720px';
  c.width = 720;
  c.height = 720;
});
await page.waitForTimeout(1500);
await page.locator('#bww-minimap').screenshot({ path: `${OUT}/05-minimap-big.png` });

const dbg = await page.evaluate(() => (window.__bwwDebug ? window.__bwwDebug() : null));
console.log('DEBUG ' + JSON.stringify(dbg));
await browser.close();
console.log('done');
