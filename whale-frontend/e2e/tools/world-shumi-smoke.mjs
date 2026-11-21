// 枢密殿登录历史（S5 / T5.2）冒烟：屏风里是"账号与会话"的真业务页。
//
// 与前两条屏风冒烟的分工：
//   户部（T4.5）验"一张列表页该有的交互"（筛选/分页/详情/分录）；
//   数枢殿（T5.1）验"每座殿的连通状态是真的"（live/stale/degraded/denied）；
//   这条验枢密殿自己的两件事：① 概览四张 KPI 与两张图来自同一次聚合；
//   ② 登录历史只发接口真支持的参数——**接口没有 keyword，屏风就不给搜索框**。
//   第二件事是"按了没反应"这类问题的源头：给一个框、发一个后端会忽略的参数，
//   看起来能用，实际什么都没筛。
//
// 数据来源：page.route 拦 /auth/overview 与 /login-history/v1/page，
// 返回**与后端同形状**的信封（{code,message,data}）。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里同时挂着户部台账、数枢殿总控与这块，
   '.bwsp-status' 这类选择器会同时命中三张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="shumi"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s5-shots';
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

/* ---- 后端同形状的假数据 ---- */
const OVERVIEW = {
  service: 'auth',
  nodeKey: 'shumi-hall',
  title: '枢密殿',
  subtitle: '身份与授权 · 11-21',
  generatedAt: '2026-11-21T08:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'users', label: '启用账号', value: 42, format: 'count', tone: 'flat' },
    { key: 'sessions', label: '在用会话', value: 9, format: 'count', tone: 'flat' },
    { key: 'logins', label: '今日登录', value: 18, format: 'count', tone: 'good', hint: '成功' },
    { key: 'failures', label: '今日失败', value: 25, format: 'count', tone: 'warn', hint: '次' },
  ],
  charts: [
    {
      key: 'login-trend',
      title: '近 7 日登录（成功 / 失败）',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [
        { name: '成功', values: [12, 15, 9, 20, 18, 22, 18] },
        { name: '失败', values: [1, 2, 0, 3, 1, 4, 25] },
      ],
    },
    {
      key: 'today-mix',
      title: '今日登录结果构成',
      type: 'bar',
      format: 'count',
      categories: ['成功', '失败', '登出'],
      series: [{ name: '次数', values: [18, 25, 4] }],
    },
  ],
  alerts: [{ level: 'critical', text: '今日登录失败 25 次，疑似撞库，请查登录历史', count: 25 }],
  links: [{ key: 'login-history', label: '登录历史', path: '/login-history/v1/page', permission: 'sys:login:history:list' }],
  notes: ['口径：启用账号（未删除且启用）、在用会话（未撤销且未过期）、登录结果按事件时间统计，均限本租户'],
};

/** 9 条登录历史：前 2 条失败（首行同一 IP 连撞），用来验徽标颜色与排序无关 */
const HISTORY = [
  { id: 9001, tenantId: 7, userId: 11, username: 'qa_admin', loginType: 'PASSWORD', loginResult: 'FAILURE', failureReason: '凭据错误', ip: '10.0.0.31', userAgent: 'Edge/131', loginTime: '2026-11-21T07:59:12', deviceFingerprint: 'fp-91' },
  { id: 9002, tenantId: 7, userId: 11, username: 'qa_admin', loginType: 'PASSWORD', loginResult: 'FAILURE', failureReason: '凭据错误', ip: '10.0.0.31', userAgent: 'Edge/131', loginTime: '2026-11-21T07:58:40', deviceFingerprint: 'fp-91' },
  { id: 9003, tenantId: 7, userId: 11, username: 'qa_admin', loginType: 'PASSWORD', loginResult: 'SUCCESS', failureReason: null, ip: '10.0.0.31', userAgent: 'Edge/131', loginTime: '2026-11-21T07:58:02', deviceFingerprint: 'fp-91' },
  { id: 9004, tenantId: 7, userId: 12, username: 'qa_verify_1114', loginType: 'PASSWORD', loginResult: 'SUCCESS', failureReason: null, ip: '10.0.0.44', userAgent: 'Chrome/130', loginTime: '2026-11-21T07:31:00', deviceFingerprint: 'fp-44' },
  { id: 9005, tenantId: 7, userId: 13, username: 'zhangsan', loginType: 'SOCIAL', loginResult: 'SUCCESS', failureReason: null, ip: '10.0.0.58', userAgent: 'WeChat/8.0', loginTime: '2026-11-20T18:02:11', deviceFingerprint: 'fp-58' },
  { id: 9006, tenantId: 7, userId: 13, username: 'zhangsan', loginType: 'PASSWORD', loginResult: 'LOGOUT', failureReason: null, ip: '10.0.0.58', userAgent: 'WeChat/8.0', loginTime: '2026-11-20T17:40:00', deviceFingerprint: 'fp-58' },
  { id: 9007, tenantId: 7, userId: 14, username: 'lisi', loginType: 'PASSWORD', loginResult: 'SUCCESS', failureReason: null, ip: '10.0.0.61', userAgent: 'Edge/131', loginTime: '2026-11-20T09:12:03', deviceFingerprint: 'fp-61' },
  { id: 9008, tenantId: 7, userId: 15, username: 'wangwu', loginType: 'OAUTH2', loginResult: 'SUCCESS', failureReason: null, ip: '10.0.0.72', userAgent: 'curl/8.6', loginTime: '2026-11-19T22:05:00', deviceFingerprint: null },
  { id: 9009, tenantId: 7, userId: 16, username: 'zhaoliu', loginType: 'PASSWORD', loginResult: 'FAILURE', failureReason: '账号已锁定', ip: '10.0.0.88', userAgent: 'Edge/131', loginTime: '2026-11-19T08:20:00', deviceFingerprint: 'fp-88' },
];

const json = (data) => ({ code: 0, message: 'ok', data });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error' && !msg.text().startsWith('Failed to load resource')) {
    errors.push(msg.text());
  }
});
page.on('pageerror', (err) => errors.push(`PAGEERROR ${err.message}`));

await page.addInitScript(() => {
  try {
    window.localStorage.setItem('bww.avatar.v1', JSON.stringify({ name: '舰长' }));
    // 没令牌时屏风页连请求都不发（直接显示"未登录"），这里给一个 debug 令牌
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});

/** 记录屏风页真实发出的查询参数：只发接口支持的参数，不发明参数 */
const queried = [];

await page.route('**/auth/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/login-history/v1/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const result = (url.searchParams.get('result') ?? '').trim();
  const keyword = url.searchParams.get('keyword');
  queried.push({ current, size, result, keyword, path: url.pathname });
  const matched = HISTORY.filter((row) => (result ? row.loginResult === result : true));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是登录历史 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shumi"]');
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  return {
    size: el.dataset.size,
    title: el.querySelector('.bws-title')?.textContent ?? '',
    subtitle: el.querySelector('.bws-sub')?.textContent ?? '',
    w: Math.round(rect.width),
    h: Math.round(rect.height),
    hasPage: Boolean(el.querySelector('.bwsp')),
    tabs: [...el.querySelectorAll('.bwsp-tab')].map((tab) => tab.textContent),
  };
});
check('枢密殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是枢密殿 · 登录历史', String(mounted?.title).includes('登录历史'), mounted?.title);
check('入口标签来自概览 links（登录历史）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '登录历史', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI + 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('shumi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（账号/会话/成功/失败）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日趋势 + 今日构成）', probe?.overview.charts === 2, probe?.overview.charts);

/* ---- 3. 接口没有 keyword：屏风就不该有搜索框 ---- */
const searchHidden = await page.evaluate(() => {
  const input = document.querySelector('[data-screen-id="shumi"] .bwsp-search');
  return input ? input.hidden : null;
});
check('没有关键字搜索框（登录历史接口不支持 keyword）', searchHidden === true, searchHidden);

/* ---- 4. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('列表请求只带接口支持的参数（无 keyword）',
  queried[0]?.current === 1 && queried[0]?.size === 6 && queried[0]?.keyword === null, queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shumi"] .bwsp-table tbody tr')].map((tr) => ({
    user: tr.children[0]?.textContent ?? '',
    result: tr.children[1]?.textContent ?? '',
    tone: tr.children[1]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    type: tr.children[2]?.textContent ?? '',
    ip: tr.children[3]?.textContent ?? '',
    time: tr.children[4]?.textContent ?? '',
  })),
);
check('结果列是中文徽标（成功/失败/登出）',
  rows.length === 6 && rows.every((row) => ['成功', '失败', '登出'].includes(row.result)), rows.map((r) => r.result));
check('失败标红（值班先看这个）', rows[0]?.tone === 'bad' && rows[2]?.tone === 'good', rows.slice(0, 3));
check('时间列到分钟（同一天多条时只给日期等于没给）',
  rows.every((row) => /^\d{2}-\d{2} \d{2}:\d{2}$/.test(row.time)), rows.map((r) => r.time));

await page.screenshot({ path: `${OUT}/10-shumi-list.png` });

/* ---- 5. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="shumi"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="shumi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="shumi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'shumi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6. 状态筛选：result 参数真的发给服务端 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('FAILURE');
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shumi'));
const lastResult = queried.filter((item) => item.result === 'FAILURE').pop();
check('结果筛选参数发给服务端', Boolean(lastResult), queried.slice(-3));
check('筛失败只剩 3 条', probe?.list.total === 3 && probe?.list.rows === 3, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(600);

/* ---- 7. 分页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shumi'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(400);

/* ---- 8. 点行看详情（同一份快照，不再二次请求） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shumi'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是账号', probe?.detail.title === 'qa_admin', probe?.detail.title);
check('详情 8 个字段（含失败原因/客户端/指纹）', probe?.detail.fields === 8, probe?.detail);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shumi"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的失败原因没被吞掉', detailText.some((text) => text.includes('凭据错误')), detailText.slice(0, 8));
check('详情里的时间也是到分钟',
  detailText.some((text) => /\d{2}-\d{2} \d{2}:\d{2}/.test(text)), detailText.slice(0, 8));
await page.screenshot({ path: `${OUT}/11-shumi-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(400);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shumi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 9. Esc 退出（顺带验 S2 那个"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shumi"]');
  const rect = el?.getBoundingClientRect();
  return {
    engaged: document.body.dataset.screenEngaged ?? null,
    focus: el?.dataset.focus ?? null,
    w: rect ? Math.round(rect.width) : 0,
    h: rect ? Math.round(rect.height) : 0,
  };
});
check('Esc 退出屏风', exited.engaged === null, exited);
check('退出后恢复非贴脸态', exited.focus === 'false', exited.focus);
check('退出后回到 3D 尺寸（不是糊满整个镜头）', exited.w > 0 && exited.w < 800 && exited.h < 600, exited);

/* ---- 10. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/auth/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/login-history/v1/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="shumi"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="shumi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('shumi');
  const empty = document.querySelector('[data-screen-id="shumi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="shumi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/12-shumi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
