// S3 数据厅冒烟：确认 WebGL 3D 数据厅能开、能切厅、卡面真的画出来了、离厅停刷新。
//
// 为什么不用肉眼看截图：厅在 y=400 的隔离空间，位置错了、相机没 snap、卡面没画，
// 三种情况在截图里都是"看不见"。这里用探针把三种可能分开——active/相机位姿/
// 卡面位图长度/定时器状态各有断言，修法一目了然。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s3-datahall';
mkdirSync(OUT, { recursive: true });

const BASE = process.env.BASE || 'http://127.0.0.1:8124/world/?debug=1';
const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

let failures = 0;
const ok = (cond, msg) => {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`);
  if (!cond) failures++;
};
const errors = [];
// 无后端环境下 favicon 与审计上报接口必然 404，属预期噪声——e2e 只把真正的 JS
// 异常（pageerror）算失败，console 的 404 记录但不判负（与项目"断言只依赖 DOM/探针"一致）。
page.on('console', (msg) => {
  if (msg.type() === 'error' && !/Failed to load resource/.test(msg.text())) {
    errors.push(msg.text());
  }
});
page.on('pageerror', (err) => errors.push('PAGEERROR ' + err.message));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 忽略 */
  }
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(4000);

// 1) 探针齐备
const probes = await page.evaluate(() => ({
  hasHall: typeof window.__bwwDataHall === 'function',
  hasOpen: typeof window.__bwwHallOpen === 'function',
  hasClose: typeof window.__bwwHallClose === 'function',
  hasNext: typeof window.__bwwHallNext === 'function',
  hasFace: typeof window.__bwwHallFace === 'function',
}));
ok(Object.values(probes).every(Boolean), 'S3 探针齐备: ' + JSON.stringify(probes));

const before = await page.evaluate(() => window.__bwwDataHall());
ok(before && before.hallCount >= 1, `初始厅数 >=1: ${before?.hallCount}`);
ok(before && before.active === false, '初始未进厅');
ok(before && before.refreshing === true, '城池采集定时器在跑');

// 2) V 键进厅
await page.keyboard.press('KeyV');
await page.waitForTimeout(1200);
const opened = await page.evaluate(() => window.__bwwDataHall());
ok(opened.active === true, 'V 键进厅成功');
ok(opened.current && opened.current.nodeKey, `当前厅: ${opened.current?.name}/${opened.current?.nodeKey}`);
ok(opened.refreshing === false, '进厅后城池 60s 采集已停');
ok(opened.hallRefresh === true, '进厅后 30s 厅内刷新已起');

// 3) 相机 snap 到 y=400 展厅（不是留在城中）
ok(opened.cameraY > 390, `相机已 snap 到展厅高度 (cameraY=${opened.cameraY})`);

// 4) 卡面位图真的画出来了（非空白 dataURL）
const faces = await page.evaluate(() => ({
  plaque: window.__bwwHallFace('plaque'),
  kpi0: window.__bwwHallFace('kpi', 0),
  line: window.__bwwHallFace('line'),
  bar: window.__bwwHallFace('bar'),
  donut: window.__bwwHallFace('donut'),
  alert: window.__bwwHallFace('alert'),
}));
for (const [kind, url] of Object.entries(faces)) {
  ok(typeof url === 'string' && url.startsWith('data:image/png') && url.length > 2000, `${kind} 卡面已绘制 (${url?.length ?? 0} bytes)`);
}
await page.screenshot({ path: OUT + '/01-hall-open.png' });

// 4b) 注入演示数据看"有数"态（无后端环境下真接口全 404，评审需要见真出图）
await page.evaluate(() => window.__bwwHallMock());
await page.waitForTimeout(500);
const mockedFaces = await page.evaluate(() => ({
  kpi0: window.__bwwHallFace('kpi', 0),
  line: window.__bwwHallFace('line'),
  bar: window.__bwwHallFace('bar'),
  donut: window.__bwwHallFace('donut'),
}));
for (const [kind, url] of Object.entries(mockedFaces)) {
  ok(typeof url === 'string' && url.length > 3000, `${kind} 演示数据态已重绘 (${url?.length ?? 0} bytes)`);
}
await page.screenshot({ path: OUT + '/01b-hall-mock-data.png' });

// 5) 切厅（右方向键）
const node0 = opened.current?.nodeKey;
await page.keyboard.press('ArrowRight');
await page.waitForTimeout(400);
const afterNext = await page.evaluate(() => window.__bwwDataHall());
ok(afterNext.active === true && afterNext.current?.nodeKey !== node0, `切厅成功: ${node0} → ${afterNext.current?.nodeKey}`);
await page.screenshot({ path: OUT + '/02-hall-next.png' });

// 6) 左方向键切回
await page.keyboard.press('ArrowLeft');
await page.waitForTimeout(400);
const afterPrev = await page.evaluate(() => window.__bwwDataHall());
ok(afterPrev.current?.nodeKey === node0, '左键切回原厅');

// 7) Esc 出厅 + 离厅停刷新
await page.keyboard.press('Escape');
await page.waitForTimeout(1200);
const closed = await page.evaluate(() => window.__bwwDataHall());
ok(closed.active === false, 'Esc 出厅成功');
ok(closed.hallRefresh === false, '离厅后 30s 厅内刷新已停');
ok(closed.refreshing === true, '离厅后城池 60s 采集恢复');
await page.screenshot({ path: OUT + '/03-back-to-city.png' });

// 8) 无 pageerror
ok(errors.length === 0, '无 console/page error' + (errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''));

console.log('\nRESULT: ' + (failures === 0 ? 'ALL PASS' : failures + ' FAILED'));
await browser.close();
process.exit(failures === 0 ? 0 : 1);
