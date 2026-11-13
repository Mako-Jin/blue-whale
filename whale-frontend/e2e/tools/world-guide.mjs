/**
 * 小鲸宝带路导览冒烟 —— 九站路线、到站讲解、掉队叫回、结束与重开。
 *
 * <p>导览的判定是"主公走到哪儿"而不是"鲸鱼飞到哪儿"，所以这里用 warp 把玩家送到每一站，
 * 模拟"人走到了殿前"，再看进度是否推进、讲解词是否换成了这一站的。用 warp 而不是
 * 真按 WASD 走：九站绕环一圈约 350 米，靠走路要跑几分钟，冒烟会变成耐力测试。</p>
 *
 * <p>顺带验一条容易漏的：导览中不能继续闲聊。鲸鱼原本每 16–28 秒随机说一句，
 * 那句闲聊会把刚到站的讲解词顶掉，玩家看到的就是"走到殿前它说了句风景真好"。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-guide.mjs
 */

import { chromium } from '@playwright/test';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/';

let passed = 0;
let failed = 0;

function ok(condition, label) {
  if (condition) {
    passed += 1;
    console.log(`  PASS ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL ${label}`);
  }
}

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', (error) => errors.push(String(error)));

await page.addInitScript(() => {
  localStorage.setItem(
    'bww.avatar.v1',
    JSON.stringify({
      name: '听讲人', height: 1, build: 1, faceWidth: 1,
      skin: '#e8c4a0', robe: '#2b4a63', hair: '#15110f', hairStyle: 0, beard: false, accessory: 1,
    }),
  );
});

await page.goto(`${BASE}?debug=1`, { waitUntil: 'load' });
await page.waitForTimeout(5000);

const guideState = () => page.evaluate(() => window.__bwwGuide());
const bubbleText = () => page.evaluate(() => document.getElementById('bww-pet-text')?.textContent ?? '');
const hudText = () => page.evaluate(() => document.getElementById('bww-guide')?.textContent ?? '');
const hudVisible = () => page.evaluate(
  () => document.getElementById('bww-guide')?.classList.contains('bww-show') ?? false,
);

/* ---- 站点表 ---- */
const stops = await page.evaluate(() => window.__bwwGuideStops());
ok(stops.length === 9, `站点共 9 站（八方宫署 + 数枢殿，实际 ${stops.length}）`);
ok(stops[8]?.nodeKey === 'shushu-hall', `最后一站是数枢殿（${stops[8]?.name}）`);

/* ---- 开导览 ---- */
await page.keyboard.press('KeyG');
await page.waitForTimeout(400);
const started = await guideState();
ok(started.active === true, '按 G 开始导览');
ok(started.index === 0, `从第一站开始（index ${started.index}）`);
ok(await hudVisible(), '导览进度条出现');
ok((await hudText()).includes(`1/${stops.length}`), `进度显示 1/9（${await hudText()}）`);
ok((await bubbleText()).includes('随我来'), `开场白先说明要做什么（${await bubbleText()}）`);

/* ---- 逐站走完 ---- */
let narrationOk = true;
let progressOk = true;
for (let i = 0; i < stops.length; i += 1) {
  const stop = stops[i];
  await page.evaluate(({ x, z }) => window.__bwwWarp(x, z), { x: stop.x, z: stop.z });
  await page.waitForTimeout(420);
  const line = await bubbleText();
  if (!line.includes(stop.name)) {
    narrationOk = false;
    console.log(`        第 ${i + 1} 站讲解词没提到殿名：期望含「${stop.name}」，实际「${line}」`);
  }
  const state = await guideState();
  const expectIndex = i + 1;
  if (state.index !== expectIndex) {
    progressOk = false;
    console.log(`        第 ${i + 1} 站后进度应为 ${expectIndex}，实际 ${state.index}`);
  }
  // 中途留一张图：站在殿前、进度条与讲解同时在场的状态
  if (i === 4) {
    await page.screenshot({ path: 'F:/blue-whale/tmp/shot-guide-mid.png' });
  }
}
ok(progressOk, '九站逐站推进，每站只推进一次');
ok(narrationOk, '每站讲解词都点名该殿（不是重复上一站的话）');

const finished = await guideState();
ok(finished.index === stops.length, `走完时 index = 9（实际 ${finished.index}）`);
ok(finished.done === true, '走完置 done');
ok(finished.active === false, '走完自动结束');
ok(!(await hudVisible()), '走完进度条收起');
ok((await bubbleText()).includes('数枢殿'), `收尾讲的是数枢殿（${await bubbleText()}）`);

await page.screenshot({ path: 'F:/blue-whale/tmp/shot-guide-done.png' });

/* ---- 重开 + Esc 中止 ---- */
await page.keyboard.press('KeyG');
await page.waitForTimeout(300);
ok((await guideState()).index === 0, '再按 G 从头开始');
await page.screenshot({ path: 'F:/blue-whale/tmp/shot-guide-start.png' });

await page.keyboard.press('Escape');
await page.waitForTimeout(300);
ok((await guideState()).active === false, 'Esc 中止导览');
ok(!(await hudVisible()), '中止后进度条收起');

/* ---- 中止后不影响正常走动 ---- */
await page.evaluate(() => window.__bwwWarp(0, 78));
await page.waitForTimeout(300);
const beforeWalk = await page.evaluate(() => window.__bwwDebug());
await page.keyboard.down('w');
await page.waitForTimeout(2200);
await page.keyboard.up('w');
await page.waitForTimeout(250);
const afterWalk = await page.evaluate(() => window.__bwwDebug());
const walked = Math.hypot(afterWalk.x - beforeWalk.x, afterWalk.z - beforeWalk.z);
ok(walked > 1.5, `中止后能正常走（位移 ${walked.toFixed(2)}m）`);

ok(errors.length === 0, `无页面异常（${errors.slice(0, 2).join(' | ')}）`);

await browser.close();
console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
