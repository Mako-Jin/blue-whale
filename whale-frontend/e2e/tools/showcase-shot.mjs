/**
 * 展示场景截图：用本机 Playwright chromium 打开 dev server，等场景稳定后出图。
 *
 * 用法：node shot.mjs <输出目录>
 * 依赖环境变量 PLAYWRIGHT_BROWSERS_PATH 指向 .toolchains/playwright
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const OUT = process.argv[2] ?? 'F:\\blue-whale\\tmp\\showcase-shot\\out';
const ONLY = process.argv[3]; // 可选：只截名字包含该串的那几张，调参时省时间
mkdirSync(OUT, { recursive: true });

const SHOTS = [
  { name: '01-overview-dusk', time: '黄昏', preset: '全景', wait: 2600 },
  { name: '02-bridge-dusk', time: '黄昏', preset: '桥头', wait: 1600 },
  { name: '03-street-dusk', time: '黄昏', preset: '街景', wait: 1600 },
  { name: '04-dock-dusk', time: '黄昏', preset: '码头', wait: 1600 },
  // 夜景码头：唯一能验收"灯影摇红"的机位。灯高 3m、相机高 3.6m 时，灯笼的倒影点
  // 才落在河面内；全景机位（高 21m）算出来的倒影点落在岸上，物理上就没有灯影。
  { name: '04b-dock-night', time: '夜晚', preset: '码头', wait: 1600 },
  { name: '05-overview-day', time: '白天', preset: '全景', wait: 1600 },
  { name: '06-overview-blue', time: '蓝调', preset: '全景', wait: 1600 },
  { name: '07-overview-night', time: '夜晚', preset: '全景', wait: 1600 },
];

const browser = await chromium.launch({
  // 用系统 Edge：本机 .toolchains/playwright 里的 chromium 与 e2e 依赖的 revision 对不上，
  // 走 channel 免下载。WebGL 在 headless Edge 下走 ANGLE/D3D11，能出真实 GPU 渲染图。
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});

const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });

const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(msg.text());
});
page.on('pageerror', (err) => errors.push(`PAGEERROR: ${err.message}`));

const Q = process.argv[4] ?? ''; // 可选画质：high|mid|low，用于测帧率
await page.goto(`http://127.0.0.1:8126/${Q ? `?q=${Q}` : ''}`, { waitUntil: 'load', timeout: 60000 });
// 首帧：等地形生成 + 阴影贴图烘一次
await page.waitForTimeout(5000);

const readStats = async () => {
  const rows = await page.$$eval('.sc-hud-row', (els) =>
    els.map((el) => el.textContent?.replace(/\s+/g, ' ').trim() ?? ''),
  );
  return rows.join(' | ');
};

for (const shot of (ONLY ? SHOTS.filter((s) => s.name.includes(ONLY)) : SHOTS)) {
  await page.getByRole('button', { name: shot.time, exact: true }).click();
  await page.getByRole('button', { name: shot.preset, exact: true }).click();
  await page.waitForTimeout(shot.wait);
  const file = join(OUT, `${shot.name}.png`);
  await page.screenshot({ path: file });
  console.log(`SHOT ${shot.name} ${await readStats()}`);
}

console.log(errors.length ? `ERRORS:\n${errors.slice(0, 8).join('\n')}` : 'NO_ERRORS');
await browser.close();
