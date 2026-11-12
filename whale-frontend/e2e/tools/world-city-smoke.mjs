/**
 * 蓝鲸城（whale-world）冒烟：P1 角色 / NPC / 联机骨架 / 城图
 *
 * 前置：先起服务（产物或 dev 均可），并把 URL 传给 BASE
 *   pnpm --filter whale-world build && pnpm --filter whale-world preview --port 8125
 *   BASE=http://127.0.0.1:8125/world/ node tools/world-city-smoke.mjs
 * （dev 端口则用 BASE=http://localhost:8124/）
 *
 * 断言口径：
 *   1. 加载期无 pageerror（白屏类问题直接失败）
 *   2. 首次入城自动弹面板；预览画布有尺寸
 *   3. 保存后 localStorage 落地，姓名与配色一致
 *   4. 6 个 NPC + 4 个「其他玩家」注入
 *   5. 城图 canvas 真的画出了东西（非空白像素）
 *   6. 传送门面板能开且 9 张卡片可见
 *   7. 门洞可通行（正）+ 城墙挡人（负）——缺负向对照就分不清"门通了"和"碰撞全废了"
 *
 * 需要 ?debug=1 才暴露 window.__bwwDebug / __bwwWarp（见 city.ts 末尾）。
 */
import { chromium } from '@playwright/test';

const BASE = (process.env.BASE || 'http://127.0.0.1:8125/world/') + '?debug=1';

const failures = [];
const ok = (cond, msg) => {
  if (!cond) {
    failures.push(msg);
  }
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`);
};

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(e.message));

await page.goto(BASE, { waitUntil: 'load' });
await page.waitForTimeout(4200);

/* 1. 无未捕获异常 */
ok(pageErrors.length === 0, `加载期无 pageerror（实际 ${pageErrors.length} 条）${pageErrors[0] || ''}`);

/* 2. 首访自动弹面板 + 预览画布 */
ok((await page.locator('#bww-char-panel.bww-open').count()) === 1, '首次入城自动打开「录名入城」');
const previewSize = await page.evaluate(() => {
  const c = document.querySelector('.bww-cp-canvas');
  return c ? `${c.width}x${c.height}` : 'none';
});
ok(previewSize !== 'none' && !previewSize.startsWith('0'), `角色预览画布有尺寸（${previewSize}）`);

/* 3. 改形象并保存 */
await page.locator('.bww-cp-swatches button[data-swatch="robe"]').nth(3).click();
await page.locator('.bww-cp-check input').check();
await page.locator('.bww-cp-name').fill('云归');
await page.locator('.bww-cp-save').click();
await page.waitForTimeout(800);
const saved = await page.evaluate(() => localStorage.getItem('bww.avatar.v1'));
const savedObj = saved ? JSON.parse(saved) : {};
ok(savedObj.name === '云归', '形象写入 localStorage（姓名）');
ok(savedObj.robe === '#5a4a7a' && savedObj.beard === true, '形象写入 localStorage（衣色 / 蓄须）');

/* 4. NPC 与联机骨架 */
const world = await page.evaluate(() => window.__bwwDebug());
ok(world && world.npcs === 6, `注入 6 名 NPC（实际 ${world?.npcs}）`);
ok(world && world.peers === 4, `注入 4 名其他玩家（实际 ${world?.peers}）`);

/* 5. 城图真的画了东西 */
const mapPainted = await page.evaluate(() => {
  const c = document.getElementById('bww-minimap');
  if (!c) return false;
  const ctx = c.getContext('2d');
  const data = ctx.getImageData(0, 0, c.width, c.height).data;
  let painted = 0;
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] > 0) painted++;
  }
  return painted > c.width * c.height * 0.5;
});
ok(mapPainted, '城图 canvas 已绘制（>50% 不透明像素）');

/* 6. 传送门面板 */
await page.keyboard.press('KeyE');
await page.waitForTimeout(1200);
const portalItems = await page.evaluate(() => {
  const items = Array.from(document.querySelectorAll('.bww-portal-item'));
  return {
    total: items.length,
    visible: items.filter((el) => getComputedStyle(el).visibility === 'visible' && Number(getComputedStyle(el).opacity) > 0).length,
  };
});
ok(portalItems.total === 9, `传送门 9 项（实际 ${portalItems.total}）`);
ok(portalItems.visible === 9, `传送门卡片可见（实际 ${portalItems.visible}）`);
await page.keyboard.press('Escape');
await page.waitForTimeout(300);

/* 7. 门洞通行（正）+ 城墙阻挡（负） */
const walk = async (ms) => {
  await page.keyboard.down('Shift');
  await page.keyboard.down('w');
  await page.waitForTimeout(ms);
  await page.keyboard.up('w');
  await page.keyboard.up('Shift');
  await page.waitForTimeout(400);
};
await page.evaluate(() => window.__bwwWarp(0, 51)); // 内城门外，门洞 |x|<6.5
await walk(6000);
const throughGate = await page.evaluate(() => window.__bwwDebug());
ok(throughGate.z < 46, `正：沿御道穿过内城门（z 51 → ${throughGate.z}）`);

await page.evaluate(() => window.__bwwWarp(20, 51)); // 同一高度但贴墙
await walk(6000);
const blocked = await page.evaluate(() => window.__bwwDebug());
ok(blocked.z > 49, `负：贴墙处被城墙挡住（z 51 → ${blocked.z}）`);

await browser.close();

console.log(failures.length ? `\n${failures.length} 项失败` : '\n全部通过');
process.exit(failures.length ? 1 : 0);
