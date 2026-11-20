// 遮挡遮罩验收（T2.8）：A/B 对比像素
//
// 判据不是"看起来挡没挡"：
//   开启遮挡层时，屏风左缘应当出现桩子的浅石色；
//   关掉遮挡层后，同一区域应当被 DOM 纸面盖回去。
// 两张图在同一裁剪区做逐像素差，差异显著即证明遮挡层真的参与了合成。
//
// 像素比较放在浏览器里做（OffscreenCanvas 解码 PNG），避免为一个断言引入图像库依赖。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s2-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', args: ['--use-angle=d3d11','--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.addInitScript(() => { try { localStorage.setItem('bww.avatar.v1', JSON.stringify({name:'舰长'})); } catch {} });
await page.goto('http://127.0.0.1:8124/world/?debug=1', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(8000);

await page.evaluate(() => { window.__bwwWarp?.(-7.6, 65.6); window.__bwwLook?.(0, 0.14, 5); });
await page.waitForTimeout(2200);

const box = await page.locator('[data-screen-id="notice"]').boundingBox();
if (!box) { console.log('NO_SCREEN'); await browser.close(); process.exit(1); }
const clip = {
  x: Math.max(0, Math.round(box.x - 60)),
  y: Math.max(0, Math.round(box.y - 40)),
  width: Math.min(Math.round(box.width + 120), 1440),
  height: Math.min(Math.round(box.height + 80), 900),
};
clip.width = Math.min(clip.width, 1440 - clip.x);
clip.height = Math.min(clip.height, 900 - clip.y);
console.log('CLIP ' + JSON.stringify(clip));

const onBuf = await page.screenshot({ clip });
await page.screenshot({ path: OUT + '/30-occluder-on.png', clip });

await page.evaluate(() => {
  const c = document.querySelector('#bww-occluder canvas');
  if (c) c.style.display = 'none';
});
await page.waitForTimeout(700);
const offBuf = await page.screenshot({ clip });
await page.screenshot({ path: OUT + '/31-occluder-off.png', clip });

const stats = await page.evaluate(async ({ a, b }) => {
  const decode = async (b64) => {
    const bin = atob(b64);
    const arr = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i += 1) arr[i] = bin.charCodeAt(i);
    const bmp = await createImageBitmap(new Blob([arr], { type: 'image/png' }));
    const canvas = new OffscreenCanvas(bmp.width, bmp.height);
    const ctx = canvas.getContext('2d');
    ctx.drawImage(bmp, 0, 0);
    return ctx.getImageData(0, 0, bmp.width, bmp.height).data;
  };
  const da = await decode(a);
  const db = await decode(b);
  let diff = 0;
  let max = 0;
  for (let i = 0; i < da.length; i += 4) {
    const d = Math.abs(da[i] - db[i]) + Math.abs(da[i+1] - db[i+1]) + Math.abs(da[i+2] - db[i+2]);
    if (d > 12) diff += 1;
    if (d > max) max = d;
  }
  return { diff, total: da.length / 4, max };
}, { a: onBuf.toString('base64'), b: offBuf.toString('base64') });

const pct = (100 * stats.diff) / stats.total;
console.log('DIFF_PIXELS ' + stats.diff + ' / ' + stats.total + ' = ' + pct.toFixed(2) + '%');
console.log('MAX_DELTA ' + stats.max);
console.log(pct > 0.5 ? 'RESULT 遮挡层生效' : 'RESULT 遮挡层几乎无效果（需检查）');

await browser.close();
