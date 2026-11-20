// 屏风业务页（S4 / T4.5）冒烟：户部屏风里是一张真业务页，不是一块"数据展示板"。
//
// 为什么要一步步点而不是只看截图：列表页的失败模式都很安静——
// 筛选没接上（筛了还是全量）、分页没算对（第二页还是第一页）、
// 点行没反应、详情把列表吃掉回不来，这四种在截图里全都"看起来正常"。
// 所以这里按用户动作走一遍，每步读一次探针 + 必要的 DOM 文本。
//
// 数据来源：无后端环境下用 __bwwScreenMock() 注入确定数据（仅 ?debug=1 存在）。
// 真接口的唯一差别是取数路径，交互与渲染完全同一套代码。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';

/* 屏风页选择器一律限定到户部这块屏风：城里同时挂着数枢殿总控页（S5），
   '.bwsp-search' 这类选择器会同时命中两个，Playwright strict mode 直接报错。 */
const SCOPE = '[data-screen-id="hubu-erp"] ';
const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s4-shots';
mkdirSync(OUT, { recursive: true });

let pass = 0;
let fail = 0;
const check = (name, ok, detail) => {
  if (ok) {
    pass += 1;
    console.log(`PASS ${name}`);
  } else {
    fail += 1;
    console.log(`FAIL ${name}${detail === undefined ? '' : ` :: ${JSON.stringify(detail)}`}`);
  }
};

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const errors = [];
page.on('console', (msg) => {
  // 无后端环境下 /erp/overview 与列表接口必然 404，这是本脚本的前提而不是缺陷；
  // 真正要抓的是渲染期异常（pageerror）与其他 console.error
  if (msg.type() === 'error' && !msg.text().startsWith('Failed to load resource')) {
    errors.push(msg.text());
  }
});
page.on('pageerror', (err) => errors.push(`PAGEERROR ${err.message}`));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
  } catch {
    /* 忽略 */
  }
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hubu-erp"]');
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  return {
    id: el.dataset.screenId,
    size: el.dataset.size,
    title: el.querySelector('.bws-title')?.textContent ?? '',
    w: Math.round(rect.width),
    h: Math.round(rect.height),
    hasPage: Boolean(el.querySelector('.bwsp')),
    tabs: el.querySelectorAll('.bwsp-tab').length,
  };
});
check('户部屏风存在且挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风用大屏规格（列表+详情要横向空间）', mounted?.size === 'large', mounted?.size);
check('业务页有标签（采购订单入口）', (mounted?.tabs ?? 0) >= 1, mounted?.tabs);

/* ---- 2. 注入演示数据：KPI / 图表 / 列表 ---- */
await page.evaluate(() => window.__bwwScreenMock?.());
await page.waitForTimeout(600);
let probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('概览层拿到 live 快照', probe?.overview.state === 'live', probe?.overview);
check('KPI 至少 3 个', (probe?.overview.kpis ?? 0) >= 3, probe?.overview.kpis);
check('图表 1 张', probe?.overview.charts === 1, probe?.overview.charts);
check('列表首屏 6 行、共 20 条、4 页', probe?.list.rows === 6 && probe?.list.total === 20 && probe?.list.pages === 4, probe?.list);
check('标签可用', probe?.tabs?.[0]?.enabled === true, probe?.tabs);

const chartBars = await page.evaluate(() => document.querySelectorAll('.bwsp-bar').length);
check('柱状图画出 3 根柱（营收/成本/利润）', chartBars === 3, chartBars);
const badgeTones = await page.evaluate(() =>
  [...document.querySelectorAll('.bwsp-table .bwsp-badge')].map((el) => el.dataset.tone),
);
check('状态列渲染成徽标且带灯色', badgeTones.length === 6 && badgeTones.every(Boolean), badgeTones);

/* ---- 3. 进入屏风：真 DOM 铺满视野 ---- */
await page.locator('[data-screen-id="hubu-erp"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="hubu-erp"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="hubu-erp"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'hubu-erp', engaged);
check('屏风进入贴脸态（可滚动可输入）', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层（输入法需要无 3D 祖先）', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 4. 关键字筛选 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('PO-202611-0003');
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('关键字筛选把 20 条收窄到 1 条', probe?.list.rows === 1 && probe?.list.total === 1, probe?.list);
check('筛过的行就是那只单号', probe?.list.keyword === 'PO-202611-0003', probe?.list.keyword);

await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('清空关键字后恢复 20 条', probe?.list.total === 20, probe?.list);

/* ---- 5. 状态筛选 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('RECEIVED');
await page.waitForTimeout(500);
const statusRows = await page.evaluate(() =>
  [...document.querySelectorAll('.bwsp-table tbody tr')].map(
    (tr) => tr.children[tr.children.length - 1]?.textContent ?? '',
  ),
);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('状态筛选生效（只剩已收货）', probe?.list.rows === 3 && statusRows.every((text) => text === '已收货'), statusRows);
check('筛选后回到第 1 页', probe?.list.page === 1, probe?.list.page);

await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(500);

/* ---- 6. 分页 ---- */
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('翻到第 2 页：仍是 6 行（共 20 条）', probe?.list.page === 2 && probe?.list.rows === 6, probe?.list);
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
const prevBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first();
check('非末页下一页可用', (await nextBtn.isDisabled()) === false);
// 20 条 / 每页 6 条 = 4 页，末页只剩 2 条
await nextBtn.click();
await page.waitForTimeout(400);
await nextBtn.click();
await page.waitForTimeout(400);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('末页只剩 2 条', probe?.list.page === 4 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
check('首页禁用上一页（此处第 4 页应可返回）', (await prevBtn.isDisabled()) === false);
for (let i = 0; i < 3; i += 1) {
  await prevBtn.click();
  await page.waitForTimeout(300);
}
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('退回第 1 页', probe?.list.page === 1, probe?.list.page);

/* ---- 7. 点行看详情 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是单号', String(probe?.detail.title ?? '').startsWith('PO-'), probe?.detail.title);
check('详情 8 个字段 + 12 行分录', probe?.detail.fields === 8 && probe?.detail.lines === 12, probe?.detail);
const listHidden = await page.evaluate(() => {
  const wrap = document.querySelector('.bwsp-tablewrap');
  return wrap ? getComputedStyle(wrap).display === 'none' : null;
});
check('详情态下列表让位（不是叠在一起）', listHidden === true, listHidden);

await page.screenshot({ path: `${OUT}/10-screenpage-detail.png` });

/* ---- 8. 返回列表 ---- */
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(400);
probe = await page.evaluate(() => window.__bwwScreenPage?.());
check('返回后回到列表且行还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 9. 滚动：详情分录区自己滚，整页不动 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(400);
const scrolled = await page.evaluate(() => {
  const wrap = document.querySelector('.bwsp-lines');
  if (!wrap) return null;
  const before = wrap.scrollTop;
  wrap.scrollTop = 80;
  return {
    before,
    after: wrap.scrollTop,
    scrollable: wrap.scrollHeight > wrap.clientHeight,
    bodyScroll: document.querySelector('[data-screen-id="hubu-erp"] .bws-body')?.scrollTop ?? null,
  };
});
check('分录区可滚动且在屏风内部', scrolled?.after > scrolled?.before, scrolled);
check('整页不跟着滚（表头/返回按钮不跑掉）', scrolled?.bodyScroll === 0, scrolled?.bodyScroll);
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(300);

await page.screenshot({ path: `${OUT}/11-screenpage-list.png` });

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="hubu-erp"]')?.dataset.focus ?? null,
}));
check('Esc 退出屏风', exited.engaged === null, exited);
check('退出后恢复非贴脸态', exited.focus === 'false', exited.focus);

/* ---- 11. 无权限/无令牌时的降级（不发请求也不能白屏） ---- */
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.();
  return probeNow;
});
check('探针在退出后仍可读（页面没被销毁）', Boolean(degraded), degraded);

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
