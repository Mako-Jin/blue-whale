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
  // 街景白天：铺面的檐廊、挂落、台阶这些细节只有白天看得清（黄昏只剩剪影）
  { name: '03b-street-day', time: '白天', preset: '街景', wait: 1600 },
  { name: '03c-shop-dusk', time: '黄昏', preset: '铺面', wait: 1600 },
  { name: '03d-shop-night', time: '夜晚', preset: '铺面', wait: 1600 },
  // 巷口：唯一能验收"巷子是不是真的一条巷"的机位（俯视只能看到一道缝）
  { name: '03e-alley-dusk', time: '黄昏', preset: '巷口', wait: 1600 },
  { name: '03f-alley-day', time: '白天', preset: '巷口', wait: 1600 },
  // 俯视：屋顶是画面里面积最大的东西，全景机位看不出脊/马头墙/巷口
  { name: '03g-roofs-day', time: '白天', preset: '俯视', wait: 1600 },
  { name: '03h-roofs-dusk', time: '黄昏', preset: '俯视', wait: 1600 },
  { name: '04-dock-dusk', time: '黄昏', preset: '码头', wait: 1600 },
  // 夜景码头：唯一能验收"灯影摇红"的机位。灯高 3m、相机高 3.6m 时，灯笼的倒影点
  // 才落在河面内；全景机位（高 21m）算出来的倒影点落在岸上，物理上就没有灯影。
  { name: '04b-dock-night', time: '夜晚', preset: '码头', wait: 1600 },
  // 河岸：贴地机位。地被（草簇/灌木/滩石）只有 1m 上下，所有 3.5m 以上的机位
  // 看过去只是一层颗粒，验收不了"这是不是草"
  { name: '04c-bank-dusk', time: '黄昏', preset: '河岸', wait: 1600 },
  { name: '04d-bank-day', time: '白天', preset: '河岸', wait: 1600 },
  { name: '05-overview-day', time: '白天', preset: '全景', wait: 1600 },
  { name: '06-overview-blue', time: '蓝调', preset: '全景', wait: 1600 },
  { name: '07-overview-night', time: '夜晚', preset: '全景', wait: 1600 },
  // 城门（S30）：券门/拱圈/城楼的主验收位；夜景验门洞壁灯
  { name: '08-gate-dusk', time: '黄昏', preset: '城门', wait: 1600 },
  { name: '08b-gate-day', time: '白天', preset: '城门', wait: 1600 },
  { name: '08c-gate-night', time: '夜晚', preset: '城门', wait: 1600 },
  { name: '08d-bridge-gate', time: '黄昏', preset: '桥头', wait: 1600 },
  // 月夜（S32）：唯一月盘入画的机位——月亮悬在河湾上、月光路铺向镜头
  { name: '09-moon-night', time: '夜晚', preset: '月夜', wait: 1600 },
  // 月夜白天参照：同一机位的白天版，验构图不受月光牵就（月亮只在夜里渲染）
  { name: '09b-moon-day', time: '白天', preset: '月夜', wait: 1600 },
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
