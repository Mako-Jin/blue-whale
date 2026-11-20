/**
 * 蓝鲸城 v2 骨架截图：进场景 → 拉俯角 → 出图，另截一张城图（M）。
 *
 * 用法：node tools/world-capital-shot.mjs [输出目录] [基址]
 *   默认  D:/BlueWhale/BlueWhale/.toolchains/tmp/s1-shots  http://127.0.0.1:8124/world
 *
 * 为什么不能"一张图看全城"：相机 dist 上限 48、pitch 上限 1.15rad（≈66°），
 * 第三人称跟随机位本来就看不全 220m 见方的外城。所以这里出三张：
 *   1) 默认街景——看新尺度下的城墙与坊门比例是否还正常；
 *   2) 俯角拉满 + 距离拉满——能覆盖小半个内城环带，够看 12 坊是不是等距一圈；
 *   3) 城图（M）——这是唯一能看到全城的视角，也是检验 12 坊不重叠的主视图。
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const OUT = process.argv[2] ?? 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s1-shots';
const BASE = process.argv[3] ?? 'http://127.0.0.1:8124/world';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });

const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(msg.text());
});
page.on('pageerror', (err) => errors.push(`PAGEERROR: ${err.message}`));

// 预置角色档案，跳过「录名入城」（loadProfile 对缺失字段一律回落默认值）
await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 隐私模式忽略 */
  }
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(6000);

/** 拖拽转视角：dy 为正即压低机位（pitch 变大 → 更接近俯视） */
async function drag(dx, dy) {
  await page.mouse.move(720, 470);
  await page.mouse.down();
  await page.mouse.move(720 + dx, 470 + dy, { steps: 24 });
  await page.mouse.up();
  await page.waitForTimeout(500);
}

/** 滚轮拉远（deltaY > 0 增大 dist，上限 48） */
async function zoom(delta) {
  await page.mouse.move(720, 470);
  await page.mouse.wheel(0, delta);
  await page.waitForTimeout(400);
}

const shot = async (name) => {
  await page.screenshot({ path: join(OUT, `${name}.png`) });
  console.log(`SHOT ${name}`);
};

await shot('01-street-default');

// 俯角拉满 + 距离拉满：先转视角再拉远，避免拉远时被建筑挡住
await drag(0, 620);
await zoom(2600);
await page.waitForTimeout(900);
await shot('02-far-high');

// 再转四分之一圈，看另一侧的坊与殿
await drag(-260, 0);
await page.waitForTimeout(700);
await shot('03-far-high-rot');

// 城图：全城俯视，检验 12 坊是否等距、是否压墙
await page.keyboard.press('m');
await page.waitForTimeout(900);
await shot('04-city-map');

const debug = await page.evaluate(() => (window.__bwwDebug ? window.__bwwDebug() : null));
console.log('DEBUG ' + JSON.stringify(debug));

console.log(errors.length ? `ERRORS:\n${errors.slice(0, 8).join('\n')}` : 'NO_ERRORS');
await browser.close();
