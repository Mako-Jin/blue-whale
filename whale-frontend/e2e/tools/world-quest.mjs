/**
 * 城民委托冒烟 —— 接活、走到地方、回去复命，整条闭环真按键盘鼠标走一遍。
 *
 * <p>为什么不能只调 `__bwwQuestAccept()`：任务最容易坏的地方不是状态机算错，
 * 而是**入口没接上**——头顶不挂叹号、面板里没有卡片、按钮点下去没反应，
 * 这三种都不报错，玩家只会觉得"这城没什么可做的"。所以 [2][3][8] 全部走真按键与真点击。</p>
 *
 * <p>[4] 是负向断言：没接的委托，走到目标点也不该推进。少了它，
 * "tick 对所有任务无条件生效"这种 bug 会被绿色测试完全掩盖。</p>
 *
 * 用法：BASE=http://127.0.0.1:8125/world/ node tools/world-quest.mjs
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
  // 与 world-path 一致：不强制 swiftshader，软渲 2fps 会让"等它走完"变成长跑
  args: ['--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
page.on('pageerror', (error) => errors.push(String(error)));

await page.addInitScript(() => {
  // 只备形象档案；委托存档必须留给 localStorage 自己长（[9] 要验持久化）
  localStorage.setItem(
    'bww.avatar.v1',
    JSON.stringify({
      name: '赶路人',
      height: 1,
      build: 1,
      faceWidth: 1,
      skin: '#e8c4a0',
      robe: '#2b4a63',
      hair: '#15110f',
    }),
  );
});

async function goto() {
  await page.goto(`${BASE}?debug=1`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => typeof window.__bwwQuests === 'function', { timeout: 30000 });
  // 开场 loading 1.6s 才退场，太早点屏幕会点到遮罩上
  await page.waitForTimeout(2200);
}

const snap = () => page.evaluate(() => window.__bwwQuests());
const hud = () => page.evaluate(() => window.__bwwQuestHud());
const marks = () => page.evaluate(() => window.__bwwQuestMarks());
const chatState = () => page.evaluate(() => window.__bwwDebug().chat);
const toast = () =>
  page.evaluate(() => {
    const el = document.getElementById('bww-toast');
    return el
      ? { shown: el.classList.contains('bww-show'), text: (el.textContent ?? '').replace(/\s+/g, ' ') }
      : { shown: false, text: '' };
  });
const byId = async (id) => (await snap()).find((q) => q.id === id);
const npcPos = async (id) => (await page.evaluate(() => window.__bwwNpcs())).find((n) => n.id === id);

const warp = async (x, z) => {
  await page.evaluate(([px, pz]) => window.__bwwWarp(px, pz), [x, z]);
  await page.waitForTimeout(340);
};

/** 走到某位城民身边按 F；他会巡逻，所以贴着他当前位置落下，失败再试 */
async function talkTo(id, tries = 3) {
  for (let i = 0; i < tries; i += 1) {
    const npc = await npcPos(id);
    if (!npc) {
      return { open: false, persona: null };
    }
    await warp(npc.x + 1.0, npc.z + 1.0);
    await page.keyboard.press('KeyF');
    await page.waitForTimeout(420);
    const state = await chatState();
    if (state.open) {
      return state;
    }
  }
  return { open: false, persona: null };
}

const questCard = () =>
  page.evaluate(() => {
    const el = document.querySelector('.bww-chat-quest');
    const btn = el?.querySelector('.bww-quest-act');
    return {
      shown: Boolean(el?.classList.contains('bww-show')),
      text: (el?.textContent ?? '').replace(/\s+/g, ' ').trim(),
      btn: btn?.textContent ?? '',
    };
  });

const firstNpcLine = () =>
  page.evaluate(
    () => document.querySelector('.bww-chat-msg.is-npc .bww-chat-body')?.textContent ?? '',
  );

/* ---------------------------------------------------------------- [1] 初始态 */
console.log('\n[1] 初始态');
await goto();
await page.evaluate(() => window.__bwwQuestReset());
await page.waitForTimeout(200);

const init = await snap();
ok(init.length === 3, `城里挂着三件委托（${init.length}）`);
ok(
  init.every((q) => q.state === 'available'),
  '初始都是可接状态',
);
const hud0 = await hud();
ok(hud0.visible === false, '没接活时追踪面板不占地方');

/* ------------------------------------------------------------ [2] 头顶标记 */
console.log('\n[2] 头顶委托标记');
const marks0 = await marks();
const markOf = (list, id) => list.find((m) => m.id === id);
ok(markOf(marks0, 'guard-han')?.text === '!', '韩五头顶挂金色叹号');
ok(markOf(marks0, 'clerk-shen')?.text === '!', '沈文昭头顶也挂叹号');
ok(markOf(marks0, 'guard-wei')?.text === '!', '卫临头顶也挂叹号');
ok(
  marks0.filter((m) => m.mark !== null).length === 3,
  `只有三位委托人挂标记（实际 ${marks0.filter((m) => m.mark !== null).length} 位）`,
);

/* -------------------------------------------------- [3] 打开面板见到委托卡 */
console.log('\n[3] 按 F 搭话，面板里出现委托');
const chat1 = await talkTo('guard-han');
ok(chat1.open === true && chat1.persona === 'guard-han', '按 F 与韩五搭上话');
const card1 = await questCard();
ok(card1.shown === true, '面板里出现委托卡');
ok(card1.text.includes('认得这城'), '委托卡写着任务名「认得这城」');
ok(card1.btn === '接下此事', `按钮是「接下此事」（实际「${card1.btn}」）`);

/* ----------------------------------------- [4] 负向：没接就走到地方不推进 */
console.log('\n[4] 负向：没接的委托走到地方也不推进');
await page.keyboard.press('Escape');
await page.waitForTimeout(220);
await warp(26.02, 62.82);
await page.waitForTimeout(500);
const idle = await byId('han-city-tour');
ok(idle.state === 'available', '走到户部牌坊，未接的委托仍是可接状态');
ok(idle.steps.every((s) => s === false), '三步一步都没被记上');
const hudIdle = await hud();
ok(hudIdle.visible === false, '追踪面板仍不显示（没接活就不该有面板）');

/* ------------------------------------------------------------ [5] 接取 */
console.log('\n[5] 接下委托');
const chat2 = await talkTo('guard-han');
ok(chat2.open === true, '回来再与韩五搭话');
await page.click('.bww-chat-quest .bww-quest-act');
await page.waitForTimeout(360);
const accepted = await byId('han-city-tour');
ok(accepted.state === 'active', '接下后状态变「进行中」');
const hud1 = await hud();
ok(hud1.visible === true, '追踪面板出现了');
ok(hud1.text.includes('认得这城'), '面板写着任务名');
ok(hud1.text.includes('户部'), `面板写着第一处该去哪儿（${hud1.text}）`);
const marks1 = await marks();
ok(markOf(marks1, 'guard-han')?.mark === 'active', '韩五头顶改挂进行中标记');
const toast1 = await toast();
ok(toast1.text.includes('接下委托'), `接取有明确回执（${toast1.text}）`);

/* ------------------------------------------------ [6] 走到第一处目标点 */
console.log('\n[6] 走到第一处目标');
await page.keyboard.press('Escape');
await page.waitForTimeout(220);
await warp(26.02, 62.82);
await page.waitForTimeout(600);
const step1 = await byId('han-city-tour');
ok(step1.steps[0] === true, '走到户部牌坊，第一处达成');
ok(step1.steps.filter(Boolean).length === 1, '只记了这一处（没有被顺带刷满）');
const hud2 = await hud();
ok(hud2.text.includes('1/3'), `面板进度 1/3（${hud2.text}）`);
const toast2 = await toast();
ok(toast2.text.includes('户部'), `达成回执说清了是哪一处（${toast2.text}）`);

/* -------------------------------------- [7] 走完剩下两处 → 可回禀 */
console.log('\n[7] 走完剩下两处');
await warp(-26.02, 62.82);
await page.waitForTimeout(520);
await warp(26.02, -62.82);
await page.waitForTimeout(650);
const ready = await byId('han-city-tour');
ok(ready.state === 'ready', '三处走完，状态变「可回禀」');
ok(ready.steps.every(Boolean), '三步全部记下');
const hud3 = await hud();
ok(hud3.text.includes('3/3'), `面板进度 3/3（${hud3.text}）`);
ok(hud3.text.includes('复命'), '面板提示回去复命');
const marks3 = await marks();
ok(markOf(marks3, 'guard-han')?.mark === 'ready', '韩五头顶变成金色问号');

/* ---------------------------------------------------- [8] 回去复命 */
console.log('\n[8] 回去复命');
const chat3 = await talkTo('guard-han');
ok(chat3.open === true, '回到韩五身边再按 F');
const card3 = await questCard();
ok(card3.btn === '回禀', `可交时按钮变「回禀」（实际「${card3.btn}」）`);
ok(card3.text.includes('可回禀'), '卡片标着「可回禀」');
const greeting = await firstNpcLine();
ok(greeting.includes('三处都去过了'), `开场白直接是交付那句（${greeting.slice(0, 18)}…）`);
await page.click('.bww-chat-quest .bww-quest-act');
await page.waitForTimeout(420);
const done = await byId('han-city-tour');
ok(done.state === 'done', '交付后状态「已完成」');
const hud4 = await hud();
ok(hud4.visible === false, '交付后追踪面板收起');
const toast3 = await toast();
ok(toast3.text.includes('委托已成'), `交付有回执（${toast3.text}）`);
const marks4 = await marks();
ok(markOf(marks4, 'guard-han')?.mark === null, '交付后头顶标记摘掉');

/* ------------------------------------------------ [9] 刷新后仍在 */
console.log('\n[9] 刷新页面，进度不丢');
await page.keyboard.press('Escape');
await page.waitForTimeout(220);
await goto();
const afterReload = await byId('han-city-tour');
ok(afterReload.state === 'done', '刷新后仍是「已完成」（存档生效）');
const hud5 = await hud();
ok(hud5.visible === false, '刷新后没把已交付的委托重新挂到面板');
const others = (await snap()).filter((q) => q.id !== 'han-city-tour');
ok(
  others.every((q) => q.state === 'available'),
  '另外两件委托不受影响（存档按 id 分开）',
);

/* ------------------------------------------------------ [10] 收尾 */
console.log('\n[10] 无异常');
ok(errors.length === 0, `页面无未捕获异常${errors.length ? `：${errors[0]}` : ''}`);

console.log(`\n通过 ${passed} · 失败 ${failed}`);
await browser.close();
process.exit(failed === 0 ? 0 : 1);
