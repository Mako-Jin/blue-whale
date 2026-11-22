/**
 * 影戏院幕布观感核查（S7 / T7.2，人工用，不进 smoke 套件）。
 *
 * <p>为什么单独一支脚本：幕布的探针能证明"帧在涨、画面不是纯色、视频时刻在推进"，
 * 但证不了"人眼在这个机位看得见"——相机是绕玩家的轨道相机，站在哪、朝哪决定了
 * 幕布在不在画面里。这里把玩家瞬移到幕布正面法线外几米，绕一圈四个方位各拍一张，
 * 一次看清是"没画上"还是"机位不对"。</p>
 *
 * 用法：node e2e/tools/world-yingxi-stage-shot.mjs
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = process.env.OUT ?? 'F:/blue-whale/.toolchains/tmp/s7-shots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 隐私模式忽略 */
  }
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

const stage = await page.evaluate(() => window.__bwwStageScreen?.() ?? null);
console.log('STAGE ' + JSON.stringify(stage));

/* 站到幕布正面法线外 11 米：太近看不全一块 6.4 米的幕，太远（>46）幕布就停了 */
const eye = { x: stage.x - 11.2, z: stage.z + 4.4 };
await page.evaluate((p) => window.__bwwWarp?.(p.x, p.z), eye);
await page.waitForTimeout(2600);

/* 相机是绕玩家的轨道相机：机位 = 玩家 + (sin yaw, cos yaw) × dist，看向玩家。
   所以"看向幕布"要求 -sin(yaw)/-cos(yaw) 等于玩家到幕布的单位向量 */
const dx = stage.x - eye.x;
const dz = stage.z - eye.z;
const yaw = Math.atan2(-dx, -dz);
await page.evaluate((v) => window.__bwwLookAt?.(v, 0.12, 18, 1.6), yaw);
await page.waitForTimeout(1500);
const probe = await page.evaluate(() => window.__bwwStageScreen?.() ?? null);
console.log('AIMED ' + JSON.stringify(probe));
await page.screenshot({ path: `${OUT}/stage-view-aimed.png` });

/* 再补一张贴近的：站在幕前 5 米，确认皮影人/字幕这类细节真的画上去了 */
await page.evaluate((p) => window.__bwwWarp?.(p.x, p.z), { x: stage.x - 5.0, z: stage.z + 2.0 });
await page.waitForTimeout(1600);
await page.evaluate((v) => window.__bwwLookAt?.(v, 0.06, 9, 1.9), yaw);
await page.waitForTimeout(1200);
await page.screenshot({ path: `${OUT}/stage-view-close.png` });

await browser.close();
console.log('done');
