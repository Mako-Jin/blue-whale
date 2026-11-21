// 度支殿支付单（S5 / T5.3）冒烟：屏风里是"钱"的真业务页。
//
// 与前三块屏风的分工：
//   户部（T4.5）验列表该有的交互（筛选/分页/详情/分录）；
//   数枢殿（T5.1）验各殿连通状态（live/stale/degraded/denied）；
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   这条验度支殿自己的三件事：① 概览四张 KPI 按"钱"的口径（收款/成交/待支付/退款）；
//   ② **接口支持 keyword，屏风就必须给搜索框**（上一条的反面，两条一起才是判据）；
//   ③ 待支付单在列表里是黄灯——它不是历史状态，是还没做完的事。
//
// 数据来源：page.route 拦 /pay/overview 与 /pay/order/page，
// 返回**与后端同形状**的信封（{code,message,data}）。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着四块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="duzhi"] ';

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
  service: 'pay',
  nodeKey: 'duzhi-hall',
  title: '度支殿',
  subtitle: '支付结算 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'paid', label: '今日收款', value: 12345.67, format: 'money', tone: 'good', hint: '已到账', display: '¥12,345.67' },
    { key: 'paidOrders', label: '今日成交', value: 4, format: 'count', tone: 'flat', hint: '今日建单 7' },
    { key: 'pending', label: '待支付', value: 2, format: 'count', tone: 'warn', hint: '其中超时 1' },
    { key: 'refund', label: '今日退款', value: 920, format: 'money', tone: 'warn', hint: '成功退款' },
  ],
  charts: [
    {
      key: 'paid-trend',
      title: '近 7 日收款',
      type: 'line',
      format: 'money',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '收款', values: [3200, 4100, 2800, 5600, 7200, 6800, 12345.67] }],
    },
    {
      key: 'channel-mix',
      title: '今日渠道构成',
      type: 'bar',
      format: 'money',
      categories: ['ALIPAY', 'WECHAT'],
      series: [{ name: '金额', values: [8769.67, 3576] }],
    },
  ],
  alerts: [{ level: 'critical', text: '今日支付失败 6 笔，请查渠道与失败流水', count: 6 }],
  links: [{ key: 'pay-orders', label: '支付单管理', path: '/pay/order/page', permission: 'sys:pay:list' }],
  notes: ['口径：收款按支付时间、退款按成功退款流水、待支付含超时未支付，均限本租户'],
};

/** 9 张支付单：2 待支付 / 4 已支付 / 2 已退款 / 1 已取消 */
const ORDERS = [
  { id: 9101, orderNo: 'PO20261121001', businessKey: 'MALL-20261121-001', amount: 199.0, currency: 'CNY', channel: 'ALIPAY', status: 'CREATED', payTime: null, channelTradeNo: null, refundAmount: 0, createTime: '2026-11-21T09:12:00' },
  { id: 9102, orderNo: 'PO20261121002', businessKey: 'MALL-20261121-002', amount: 88.5, currency: 'CNY', channel: 'WECHAT', status: 'CREATED', payTime: null, channelTradeNo: null, refundAmount: 0, createTime: '2026-11-21T09:05:30' },
  { id: 9103, orderNo: 'PO20261121003', businessKey: 'MALL-20261121-003', amount: 1200.0, currency: 'CNY', channel: 'ALIPAY', status: 'PAID', payTime: '2026-11-21T08:40:12', channelTradeNo: 'ALI-88213', refundAmount: 0, createTime: '2026-11-21T08:38:00' },
  { id: 9104, orderNo: 'PO20261121004', businessKey: 'MALL-20261121-004', amount: 3456.0, currency: 'CNY', channel: 'WECHAT', status: 'PAID', payTime: '2026-11-21T08:12:00', channelTradeNo: 'WX-77120', refundAmount: 0, createTime: '2026-11-21T08:10:00' },
  { id: 9105, orderNo: 'PO20261121005', businessKey: 'MALL-20261120-014', amount: 500.0, currency: 'CNY', channel: 'BALANCE', status: 'PAID', payTime: '2026-11-21T07:50:00', channelTradeNo: 'BAL-3091', refundAmount: 0, createTime: '2026-11-21T07:49:00' },
  { id: 9106, orderNo: 'PO20261120006', businessKey: 'MALL-20261120-006', amount: 800.0, currency: 'CNY', channel: 'ALIPAY', status: 'REFUNDED', payTime: '2026-11-20T18:20:00', channelTradeNo: 'ALI-88190', refundAmount: 800, createTime: '2026-11-20T18:18:00' },
  { id: 9107, orderNo: 'PO20261120007', businessKey: 'MALL-20261120-007', amount: 66.0, currency: 'CNY', channel: 'MOCK', status: 'PAID', payTime: '2026-11-20T15:02:00', channelTradeNo: 'MOCK-1', refundAmount: 0, createTime: '2026-11-20T15:01:00' },
  { id: 9108, orderNo: 'PO20261119008', businessKey: 'MALL-20261119-008', amount: 30.0, currency: 'CNY', channel: 'WECHAT', status: 'CANCELLED', payTime: null, channelTradeNo: null, refundAmount: 0, createTime: '2026-11-19T20:30:00' },
  { id: 9109, orderNo: 'PO20261119009', businessKey: 'MALL-20261119-009', amount: 120.0, currency: 'CNY', channel: 'WECHAT', status: 'REFUNDED', payTime: '2026-11-19T11:00:00', channelTradeNo: 'WX-77001', refundAmount: 120, createTime: '2026-11-19T10:58:00' },
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

await page.route('**/pay/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/pay/order/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const status = (url.searchParams.get('status') ?? '').trim();
  const keyword = url.searchParams.get('keyword');
  queried.push({ current, size, status, keyword, path: url.pathname });
  const matched = ORDERS.filter((row) => {
    if (status && row.status !== status) return false;
    if (keyword) {
      const hit = row.orderNo.includes(keyword) || String(row.businessKey ?? '').includes(keyword);
      if (!hit) return false;
    }
    return true;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是支付单 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="duzhi"]');
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
check('度支殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是度支殿 · 支付单', String(mounted?.title).includes('支付单'), mounted?.title);
check('入口标签来自概览 links（支付单管理）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '支付单管理', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI + 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（收款/成交/待支付/退款）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日收款 + 今日渠道）', probe?.overview.charts === 2, probe?.overview.charts);

/* ---- 3. 接口支持 keyword：屏风必须有搜索框（T5.2 的反面判据） ---- */
const searchState = await page.evaluate(() => {
  const input = document.querySelector('[data-screen-id="duzhi"] .bwsp-search');
  if (!input) return null;
  return { hidden: input.hidden, placeholder: input.placeholder };
});
check('有搜索框（支付单接口支持 keyword）', searchState?.hidden === false, searchState);
check('搜索框提示写的是真字段（单号 / 业务标识）',
  String(searchState?.placeholder).includes('单号'), searchState?.placeholder);

/* ---- 4. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏请求只带分页参数（无 keyword 空参）',
  queried[0]?.current === 1 && queried[0]?.size === 6 && queried[0]?.keyword === null, queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="duzhi"] .bwsp-table tbody tr')].map((tr) => ({
    orderNo: tr.children[0]?.textContent ?? '',
    business: tr.children[1]?.textContent ?? '',
    amount: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    tone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    created: tr.children[4]?.textContent ?? '',
  })),
);
check('状态列是中文徽标（待支付/已支付/已退款/已取消）',
  rows.length === 6 && rows.every((row) => ['待支付', '已支付', '已退款', '已取消'].includes(row.status)),
  rows.map((r) => r.status));
check('待支付是黄灯、已支付是绿灯（待支付不是历史状态，是还没做完的事）',
  rows[0]?.tone === 'warn' && rows[2]?.tone === 'good', rows.slice(0, 3).map((r) => [r.status, r.tone]));
check('金额列带 ¥ 与千分位',
  rows[2]?.amount?.startsWith('¥') && rows[2]?.amount?.includes(','), rows.map((r) => r.amount));
check('建单时间到分钟', rows.every((row) => /^\d{2}-\d{2} \d{2}:\d{2}$/.test(row.created)), rows.map((r) => r.created));

await page.screenshot({ path: `${OUT}/20-duzhi-list.png` });

/* ---- 5. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="duzhi"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="duzhi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="duzhi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'duzhi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6. 状态筛选：status 参数真的发给服务端 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('CREATED');
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
const lastCreated = queried.filter((item) => item.status === 'CREATED').pop();
check('状态筛选参数发给服务端', Boolean(lastCreated), queried.slice(-3));
check('筛待支付只剩 2 条', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(600);

/* ---- 7. 关键字搜索：keyword 参数真的发给服务端，且结果真被筛过 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('MALL-20261121-003');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
const lastKeyword = queried.filter((item) => item.keyword === 'MALL-20261121-003').pop();
check('关键字参数发给服务端', Boolean(lastKeyword), queried.slice(-3));
check('关键字确实筛掉其它行（1 条）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(800);

/* ---- 8. 分页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(500);

/* ---- 9. 点行看详情（同一份快照，不再二次请求） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').nth(2).click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是单号', probe?.detail.title === 'PO20261121003', probe?.detail.title);
check('详情 10 个字段（含渠道流水号/已退金额）', probe?.detail.fields === 10, probe?.detail);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="duzhi"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的渠道流水号没被吞掉', detailText.some((text) => text.includes('ALI-88213')), detailText.slice(0, 10));
check('详情里的建单时间也是到分钟',
  detailText.some((text) => /\d{2}-\d{2} \d{2}:\d{2}/.test(text)), detailText.slice(0, 10));
await page.screenshot({ path: `${OUT}/21-duzhi-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('duzhi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="duzhi"]');
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

/* ---- 11. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/pay/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/pay/order/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="duzhi"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="duzhi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('duzhi');
  const empty = document.querySelector('[data-screen-id="duzhi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="duzhi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/22-duzhi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
