// 把 PNG 截图压成 JPEG：1080p 的平涂渲染 PNG 单张 1.4MB，7 张就 10MB，
// 直接进仓库不合适。用 Chromium 的画布转一次，质量 92，体积降到 1/5。
import { chromium } from '@playwright/test';
import { readdirSync, readFileSync, writeFileSync, unlinkSync, statSync } from 'node:fs';
import { join, extname, basename } from 'node:path';

const dir = process.argv[2];
if (!dir) {
  console.error('用法: node tools/to-jpeg.mjs <目录>');
  process.exit(1);
}

const browser = await chromium.launch({ channel: 'msedge' });
const page = await browser.newPage();

let before = 0;
let after = 0;
for (const f of readdirSync(dir)) {
  if (extname(f).toLowerCase() !== '.png') continue;
  const src = join(dir, f);
  before += statSync(src).size;
  const b64 = readFileSync(src).toString('base64');
  const out = await page.evaluate(async (data) => {
    const img = new Image();
    await new Promise((res, rej) => {
      img.onload = res;
      img.onerror = rej;
      img.src = `data:image/png;base64,${data}`;
    });
    const c = document.createElement('canvas');
    c.width = img.width;
    c.height = img.height;
    c.getContext('2d').drawImage(img, 0, 0);
    return c.toDataURL('image/jpeg', 0.92);
  }, b64);
  const buf = Buffer.from(out.split(',')[1], 'base64');
  const dst = join(dir, basename(f, '.png') + '.jpg');
  writeFileSync(dst, buf);
  after += buf.length;
  unlinkSync(src);
  console.log(`${f} → ${basename(dst)} (${(buf.length / 1024).toFixed(0)} KB)`);
}
await browser.close();
console.log(`合计 ${(before / 1048576).toFixed(1)} MB → ${(after / 1048576).toFixed(1)} MB`);
