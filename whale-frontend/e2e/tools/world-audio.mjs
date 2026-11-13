/**
 * 城中声景冒烟 —— 能不能出声、静音开关、状态持久化、以及"音频不拖垮场景"。
 *
 * <p>浏览器里没法断言"听到了什么"，所以这里验的是**可观察的那一层**：
 * AudioContext 是否被用户手势解锁、静音状态是否落盘、切天气/走路/传送这些
 * 会触发声音的路径是否把页面搞挂（pageerror）。音色好坏只能靠人耳。</p>
 *
 * <p>注意 headless 下 AudioContext 可能一直是 suspended。若 env 为 true 时仍拿不到
 * ready，脚本会明确报出来而不是静默通过 —— 否则这个冒烟等于摆设。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-audio.mjs
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
      name: '听风人',
      height: 1,
      build: 1,
      faceWidth: 1,
      skin: '#e8c4a0',
      robe: '#2b4a63',
      hair: '#15110f',
    }),
  );
  // 每个用例从"未静音"起步，避免上一轮的偏好影响断言。
  // 注意 addInitScript 在 reload 后会**再执行一次**，所以必须用 sessionStorage 做一次性标记，
  // 否则刚写进去的静音状态会被自己清掉（reload 前 sessionStorage 不丢）。
  if (!sessionStorage.getItem('bww.e2e.audio.seeded')) {
    sessionStorage.setItem('bww.e2e.audio.seeded', '1');
    localStorage.removeItem('bww.audio.muted');
  }
});

const audioState = () => page.evaluate(() => window.__bwwAudio?.() ?? null);
const soundLabel = () => page.evaluate(() => {
  const btn = document.querySelector('[data-action="sound"]');
  return btn ? btn.textContent : null;
});

await page.goto(`${BASE}?debug=1`, { waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => typeof window.__bwwDebug === 'function', { timeout: 30000 });
await page.waitForTimeout(1200);

console.log('\n[1] 初始状态');
const initial = await audioState();
ok(initial !== null, '声景探针可用');
ok(initial?.muted === false, '默认不静音');
ok(initial?.ready === false, '未交互前不出声（自动播放策略）');
ok((await soundLabel()) === '声：开', '按钮文案与初始状态一致');

console.log('\n[2] 用户手势解锁');
await page.keyboard.press('KeyW');
await page.waitForTimeout(600);
const unlocked = await audioState();
ok(unlocked?.ready === true, '按键后 AudioContext 已解锁');

console.log('\n[3] 静音开关');
await page.click('[data-action="sound"]');
await page.waitForTimeout(300);
ok((await audioState())?.muted === true, '点击后进入静音');
ok((await soundLabel()) === '声：静', '按钮文案改为「声：静」');
await page.keyboard.press('KeyN');
await page.waitForTimeout(300);
ok((await audioState())?.muted === false, 'N 键恢复出声');
ok((await soundLabel()) === '声：开', '按钮文案改回「声：开」');

console.log('\n[4] 静音偏好落盘');
await page.keyboard.press('KeyN');
await page.waitForTimeout(300);
const persisted = await page.evaluate(() => localStorage.getItem('bww.audio.muted'));
ok(persisted === '1', '静音写入 localStorage');
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => typeof window.__bwwDebug === 'function', { timeout: 30000 });
await page.waitForTimeout(900);
ok((await audioState())?.muted === true, '刷新后仍是静音');
ok((await soundLabel()) === '声：静', '刷新后按钮文案跟着变（不是写死「声：开」）');
await page.keyboard.press('KeyN');
await page.waitForTimeout(300);

console.log('\n[5] 走路：出声路径不拖垮场景');
await page.evaluate(() => window.__bwwWarp(0, 78));
await page.waitForTimeout(400);
const before = await page.evaluate(() => window.__bwwDebug());
await page.keyboard.down('w');
await page.waitForTimeout(2500);
await page.keyboard.up('w');
await page.waitForTimeout(300);
const after = await page.evaluate(() => window.__bwwDebug());
const walked = Math.hypot(after.x - before.x, after.z - before.z);
ok(walked > 1.5, `走了 ${walked.toFixed(2)}m`);
ok(errors.length === 0, '走路期间无页面错误');

console.log('\n[6] 地面判定');
await page.evaluate(() => window.__bwwWarp(0, 78));
ok((await audioState())?.ground === 'stone', '御道上判石板');
await page.evaluate(() => window.__bwwWarp(40, 40));
ok((await audioState())?.ground === 'grass', '广场空地判夯土');

console.log('\n[7] 会触发声音的交互都不报错');
// 工具栏在 #bww-scene 外面（兄弟节点），曾经因为 city.ts 用 host.querySelectorAll
// 而整排按钮从未绑上点击 —— 点下去毫无反应且不报错。这里用「按钮文案真的变了」
// 来钉住这条：只断言"无报错"是抓不到的。
const weatherLabel = () => page.evaluate(() => {
  const btn = document.querySelector('[data-action="weather"]');
  return btn ? btn.textContent : null;
});
const weatherBefore = await weatherLabel();
await page.click('[data-action="weather"]');
await page.waitForTimeout(300);
const weatherAfter = await weatherLabel();
ok(weatherBefore !== weatherAfter, `点工具栏真的切了天象（${weatherBefore} → ${weatherAfter}）`);
// 再把剩下三种走一遍，确认每种天气的音频床切换都不炸
for (let i = 0; i < 3; i += 1) {
  await page.click('[data-action="weather"]');
  await page.waitForTimeout(220);
}
ok(errors.length === 0, '连切四种天象无错误');
await page.click('[data-action="portal"]');
await page.waitForTimeout(400);
await page.keyboard.press('Escape');
await page.waitForTimeout(300);
ok(errors.length === 0, '开合传送面板无错误');
await page.keyboard.press('Digit3');
await page.waitForTimeout(1500);
ok(errors.length === 0, '传送（带传送音）无错误');

console.log('\n[8] 收尾');
ok(errors.length === 0, `全程无页面错误${errors.length ? `：${errors[0]}` : ''}`);

await browser.close();
console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
