/**
 * 同城喊话冒烟 —— 面板开合、预设短语、气泡出现与过期、以及"输入时不误走"。
 *
 * <p>最后一条是必测项：P3 踩过"面板关了但输入框还握着焦点，下一次 WASD 被当成打字吞掉"。
 * 喊话面板同样有输入框，所以要把"开面板→不发飘→关面板→能走"整条走一遍。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-say.mjs
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
      name: '喊话人',
      height: 1,
      build: 1,
      faceWidth: 1,
      skin: '#e8c4a0',
      robe: '#2b4a63',
      hair: '#15110f',
      hairStyle: 0,
      beard: false,
      accessory: 1,
    }),
  );
});

await page.goto(`${BASE}?debug=1`, { waitUntil: 'load' });
await page.waitForTimeout(5000);

const panelOpen = () => page.evaluate(() => document.querySelector('#bww-say-panel')?.classList.contains('bww-open') ?? false);
const bubbles = () => page.evaluate(() => window.__bwwSpeeches());
const position = () => page.evaluate(() => window.__bwwDebug());

/* ---- 面板开合 ---- */
await page.keyboard.press('KeyQ');
await page.waitForTimeout(400);
ok(await panelOpen(), 'Q 打开喊话面板');

const presetCount = await page.locator('.bww-say-preset').count();
ok(presetCount === 6, `预设短语共 6 条（实际 ${presetCount}）`);

/* ---- 面板打开时：打字不该驱动角色走路 ---- */
const beforeTyping = await position();
await page.keyboard.type('wwww', { delay: 60 });
await page.waitForTimeout(600);
const afterTyping = await position();
const typedDist = Math.hypot(afterTyping.x - beforeTyping.x, afterTyping.z - beforeTyping.z);
ok(typedDist < 0.5, `面板输入时不走路（位移 ${typedDist.toFixed(2)}m）`);

// 清掉输入框里打进去的内容，避免影响后面的预设点击
await page.locator('.bww-say-input').fill('');

/* ---- 点预设短语 → 气泡出现 ---- */
await page.locator('.bww-say-preset').first().click();
await page.waitForTimeout(600);
const afterSay = await bubbles();
ok(afterSay.length === 1, `发出后出现 1 个气泡（实际 ${afterSay.length}）`);
ok((afterSay[0] ?? '').includes('见过主公'), `气泡内容正确（${afterSay[0] ?? '空'}）`);

const selfMarked = await page.evaluate(
  () => document.querySelectorAll('#bww-speech .bww-speech.is-me').length,
);
ok(selfMarked === 1, `自己的话被标记为自己的（is-me ${selfMarked} 个）`);

// 面板与气泡同时在场时截图
await page.screenshot({ path: 'F:/blue-whale/tmp/shot-say-panel.png' });

/* ---- Esc 收起，且收起后能正常走路 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(350);
ok(!(await panelOpen()), 'Esc 收起喊话面板');
await page.screenshot({ path: 'F:/blue-whale/tmp/shot-say-bubble.png' });

// 挪到御道上再测：初始出身点在外城外，朝前走两步就撞外墙，量不出"能不能走"
await page.evaluate(() => window.__bwwWarp(0, 78));
await page.waitForTimeout(400);
const beforeWalk = await position();
await page.keyboard.down('w');
await page.waitForTimeout(2500);
await page.keyboard.up('w');
await page.waitForTimeout(300);
const afterWalk = await position();
const walkDist = Math.hypot(afterWalk.x - beforeWalk.x, afterWalk.z - beforeWalk.z);
ok(walkDist > 1.5, `收起后 WASD 恢复行走（位移 ${walkDist.toFixed(2)}m）`);

/* ---- 气泡过期 ---- */
await page.waitForTimeout(7200);
const expired = await bubbles();
ok(expired.length === 0, `气泡 7 秒后自动消失（剩余 ${expired.length}）`);

/* ---- 自由输入 ---- */
await page.keyboard.press('KeyQ');
await page.waitForTimeout(350);
await page.locator('.bww-say-input').fill('测试一句自由输入');
await page.locator('.bww-say-send').click();
await page.waitForTimeout(500);
const typed = await bubbles();
ok((typed[0] ?? '').includes('测试一句自由输入'), `自由输入也能发出（${typed[0] ?? '空'}）`);

ok(errors.length === 0, `无页面异常（${errors.slice(0, 2).join(' | ')}）`);

await browser.close();
console.log(`\n${failed === 0 ? '全部通过' : '存在失败'}：${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
