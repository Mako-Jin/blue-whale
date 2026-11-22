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
 *   8. 问话链路（P3）：未登录判 offline → 走近出现 F 提示 → F 开面板 → 发问必有回应 → Esc 收起 → T 唤小鲸宝
 *
 * 需要 ?debug=1 才暴露 window.__bwwDebug / __bwwWarp / __bwwNpcAt / __bwwChatLog（见 city.ts 末尾）。
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

/* 2. 首访自动弹面板 + 预览画布
   等元素而不是写死 4.2s：3D 首帧要编译着色器，机器忙时 4.2s 会偶发不够。 */
await page.locator('#bww-char-panel.bww-open').waitFor({ state: 'attached', timeout: 15000 }).catch(() => {});
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

/* 6. 传送门面板
   E 键自 S2 起"屏风优先于传送门"：出生点正对告示屏，直接按 E 会进屏风而不是开面板。
   先走到城内空处（远离任何屏风）再按 E，才测得到传送门面板本身。 */
await page.evaluate(() => window.__bwwWarp(0, 90));
await page.waitForTimeout(600);
await page.keyboard.press('KeyE');
await page.waitForTimeout(1200);
const portalItems = await page.evaluate(() => {
  const panel = document.getElementById('bww-portal-panel');
  const cs = getComputedStyle(panel);
  const items = Array.from(document.querySelectorAll('.bww-portal-item'));
  return {
    total: items.length,
    opened: panel.classList.contains('bww-open'),
    visible: cs.visibility === 'visible',
    clickable: cs.pointerEvents === 'auto',
    // 卡片自身不因动画而隐藏：不透明度由面板过渡，卡片不该另有一套显隐
    cardsHidden: items.filter((el) => getComputedStyle(el).visibility !== 'visible').length,
  };
});
// S1 城郭扩容后是 十二坊 + 数枢殿 = 13 项；写死 9 的那版一直在报"实际 13"。
ok(portalItems.total === 13, `传送门 13 项（实际 ${portalItems.total}）`);
/* 判"打开"要看类名/可见性/可点击，不看 opacity：
   软件渲染（swiftshader）下 CSS 过渡能拖到 3s 以上，按 opacity 判会把"已打开"误判成"不可见"。 */
ok(portalItems.opened && portalItems.visible && portalItems.clickable,
  `传送门面板已打开可点（open=${portalItems.opened} vis=${portalItems.visible} click=${portalItems.clickable}）`);
/* 卡片可见性轮询等，不写死等待时长：软件渲染下 CSS 过渡能拖到 3s 以上，
   写死 1.2s 会把"还在过渡"误判成"卡片不可见"。 */
let cardsHidden = portalItems.cardsHidden;
if (cardsHidden > 0) {
  await page
    .waitForFunction(
      () => [...document.querySelectorAll('.bww-portal-item')].every((el) => getComputedStyle(el).visibility === 'visible'),
      null,
      { timeout: 6000 },
    )
    .catch(() => {});
  cardsHidden = await page.evaluate(
    () => [...document.querySelectorAll('.bww-portal-item')].filter((el) => getComputedStyle(el).visibility !== 'visible').length,
  );
}
ok(cardsHidden === 0, `13 张卡片本身可见（隐藏 ${cardsHidden} 张）`);
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
// 内城墙在 z=58（几何扩容后；旧断言按 46 写，起点 51 其实已经在城里，等于没测门洞）。
await page.evaluate(() => window.__bwwWarp(0, 62)); // 内城门外，门洞 |x|<6.5
/* 走 14 秒而不是 9 秒：软件渲染下主循环被压到 ~3fps（屏风逐坊增挂，宣抚院
   落成后又慢了一截），而 dt 上限 0.05s，9 秒实际只前进 ~4 米——
   正好停在 z=58.04 这条门线上，"能不能穿门"就成了掷硬币。
   这里给足余量，测的仍是"门洞可穿"，不是"帧率够不够高"。 */
await walk(14000);
const throughGate = await page.evaluate(() => window.__bwwDebug());
ok(throughGate.z < 58, `正：沿御道穿过内城门（z 62 → ${throughGate.z}）`);

await page.evaluate(() => window.__bwwWarp(20, 62)); // 同一高度但贴墙
await walk(6000);
const blocked = await page.evaluate(() => window.__bwwDebug());
ok(blocked.z > 59, `负：贴墙处被城墙挡住（z 62 → ${blocked.z}）`);

/* 8. 城民问话（P3）：断言的是"接线对不对"，不是"AI 答得好不好"
      本用例不带 token → 世界之脑必须判 offline（不发必然 403 的请求），
      F 键走真实邻近判定（先 warp 到 NPC 身旁，不直接调 open），
      发问后必须"有回应"（离线回落本地台词 + 一条说明），不能留空气泡。 */
ok(blocked.brainMode === 'offline', `未登录时判为 offline（实际 ${blocked.brainMode}）`);
ok(Boolean(blocked.brainReason), `offline 带人话原因（${blocked.brainReason}）`);

const guardPos = await page.evaluate(() => window.__bwwNpcAt('guard-wei'));
await page.evaluate(([x, z]) => window.__bwwWarp(x, z + 2.2), [guardPos.x, guardPos.z]);
/* 等 1500ms 而不是 420ms：低帧率下一帧可能隔 300–500ms，邻近检测跑在帧上，
   420ms 可能只赶上一次检测。给足余量，测的仍是"走近会提示"。 */
await page.waitForTimeout(1500);
const hintShown = await page.evaluate(() =>
  document.getElementById('bww-talk-hint')?.classList.contains('bww-show'),
);
ok(hintShown === true, '走近城民弹出「按 F 交谈」提示');

await page.keyboard.press('KeyF');
await page.waitForTimeout(900);
const opened = await page.evaluate(() => ({
  chat: window.__bwwDebug().chat,
  title: document.querySelector('#bww-chat-panel .bww-chat-name')?.textContent,
  badge: document.querySelector('#bww-chat-panel .bww-chat-badge')?.textContent,
}));
ok(opened.chat.open === true && opened.chat.persona === 'guard-wei', 'F 打开卫临的问话面板');
ok(opened.title === '卫临', `面板标题取 NPC 名（实际 ${opened.title}）`);
ok(opened.badge === 'AI 未接入', `降级徽标可见（实际 ${opened.badge}）`);

await page.locator('.bww-chat-input').fill('今天户部有几张异常工单？');
await page.locator('.bww-chat-send').click();
await page.waitForTimeout(900);
const msgs = await page.evaluate(() => window.__bwwChatLog());
ok(msgs.length >= 3, `对话留痕：开场白 + 问 + 答（实际 ${msgs.length} 条）`);
ok(msgs.some((m) => m.who === 'me' && m.text.includes('异常工单')), '玩家问话入列');
const answer = msgs.filter((m) => m.who === 'npc').pop();
ok(Boolean(answer && answer.text.trim()), `离线也有回应（${answer?.text?.slice(0, 18) ?? '空'}…）`);
const noteSeen = await page.evaluate(
  () => document.querySelector('#bww-chat-panel .bww-chat-note')?.textContent ?? '',
);
ok(noteSeen.includes('中枢') || noteSeen.includes('接入'), `面板说明降级原因（${noteSeen.slice(0, 22)}…）`);

// Esc 必须整块收起（输入框有焦点时也一样，否则用户被困在面板里）
await page.keyboard.press('Escape');
await page.waitForTimeout(320);
const closed = await page.evaluate(() => window.__bwwDebug().chat);
ok(closed.open === false, 'Esc 关闭问话面板');

await page.keyboard.press('KeyT');
await page.waitForTimeout(360);
const petTitle = await page.evaluate(
  () => document.querySelector('#bww-chat-panel .bww-chat-name')?.textContent,
);
ok(petTitle === '小鲸宝', `T 唤出小鲸宝（实际 ${petTitle}）`);
await page.keyboard.press('Escape');
await page.waitForTimeout(200);

await browser.close();

console.log(failures.length ? `\n${failures.length} 项失败` : '\n全部通过');
process.exit(failures.length ? 1 : 0);
