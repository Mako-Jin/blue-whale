// 兵部坊设备台账（S6 / T6.13）冒烟：内城十二坊的第四块屏风页。
//
// 与工部/吏部/礼部的分工：工部验"车间在不在跑"、吏部验"人有没有到岗"、礼部验"客户"，
// 这一条验**哨探**——多少台设备、几个在线、近一天收上来多少条、有没有指令发出去没下文。
//
// 六件事：
//   ① 概览四张牌走 /iot/overview，在线率必须是**比率**（0.8 → 80.0%），
//      且提示语要写出分子分母（8 / 10 在线）——只知道"80%"没法判断是 8/10 还是 800/1000；
//   ② 设备台账**有搜索框**（`IotDeviceController#page` 真收 keyword），
//      但**没有状态筛选**——设备分页接口不收 status，配下拉就是发一个后端忽略的参数；
//   ③ 「在线」列是 0/1 数字码，靠**列级字典**译成在线/离线；状态列是另一套码值（active/disabled）。
//      两套混用会把在线显示成英文 1，而且不报错；
//   ④ 列表行即详情（不配 detailPath）：`/iot/v1/devices/page` 返回的 DeviceVO 已含产品名与两个时间；
//   ⑤ 近 7 日遥测画成折线：7 根横条会把台账挤到只剩 3 行可见，断流那天线落到基线；
//   ⑥ 接口全挂时不白屏：标 stale + 给一句人话。
//
// 数据来源：page.route 拦 /iot/overview 与 /iot/v1/devices/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十四块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="bingbu"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s6-shots';
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
/* 与 IotOverviewProvider 的实际下发一致：四张 KPI（在线率是比率）、
   一张近 7 日遥测柱图、三条告警、一个设备台账入口 */
const OVERVIEW = {
  service: 'iot',
  nodeKey: 'bingbu-hall',
  title: '兵部',
  subtitle: '物联感知 · 近 7 日',
  generatedAt: '2026-11-21T22:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'devices', label: '设备总数', value: 12, format: 'count', tone: 'flat', hint: '启用 10 台' },
    { key: 'online-rate', label: '在线率', value: 0.8, format: 'percent', tone: 'warn', hint: '8 / 10 在线' },
    { key: 'telemetry-24h', label: '近 24h 上报', value: 500, format: 'count', tone: 'good', hint: '全设备合计' },
    { key: 'pending-cmd', label: '待回指令', value: 6, format: 'count', tone: 'warn', hint: '已发未回执' },
  ],
  charts: [
    {
      key: 'telemetry-trend',
      title: '近 7 日遥测上报',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '上报条数', values: [40, 42, 38, 45, 50, 0, 12] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 2 台启用设备不在线', count: 2 },
    { level: 'warning', text: '有 6 条指令已发出但未收到回执', count: 6 },
    { level: 'info', text: '全部 3 条规则都已停用，遥测不再触发告警', count: null },
  ],
  links: [{ key: 'devices', label: '设备台账', path: '/iot/v1/devices/page', permission: 'sys:iot:device' }],
  notes: ['口径：设备与遥测取自 iot_device / iot_telemetry 同源表，在线率 = 启用中在线 ÷ 启用中（停用不计入分母）'],
};

/**
 * 8 台设备：在线 1/0 与状态 active/disabled 两组码值都覆盖，
 * 且第 5、6 行故意让 lastReportTime 为 null（新装未上报的设备，列里该显示破折号）。
 * 6 行/页 → 2 页
 */
const DEVICES = [
  { id: 9001, deviceName: 'temp-sensor-001', nickname: '温度传感器A', productId: 51, productCode: 'cold-gw', productName: '冷链网关', online: 1, status: 'active', lastOnlineTime: '2026-11-21T21:58:00', lastReportTime: '2026-11-21T21:58:00', createTime: '2026-03-01T09:12:00' },
  { id: 9002, deviceName: 'th-probe-002', nickname: '温湿度探头B', productId: 51, productCode: 'cold-gw', productName: '冷链网关', online: 1, status: 'active', lastOnlineTime: '2026-11-21T21:57:00', lastReportTime: '2026-11-21T21:57:00', createTime: '2026-03-02T10:00:00' },
  { id: 9003, deviceName: 'door-east-003', nickname: '门磁-东门', productId: 52, productCode: 'sec-node', productName: '安防节点', online: 0, status: 'active', lastOnlineTime: '2026-11-19T08:12:00', lastReportTime: '2026-11-19T08:12:00', createTime: '2026-04-11T11:00:00' },
  { id: 9004, deviceName: 'meter-ws1-004', nickname: '电表-车间1', productId: 53, productCode: 'energy', productName: '能源表计', online: 1, status: 'active', lastOnlineTime: '2026-11-21T21:55:00', lastReportTime: '2026-11-21T21:55:00', createTime: '2026-05-08T14:20:00' },
  { id: 9005, deviceName: 'pump-vib-005', nickname: '水泵振动传感器', productId: 54, productCode: 'monitor', productName: '设备监测', online: 1, status: 'active', lastOnlineTime: '2026-11-21T21:40:00', lastReportTime: null, createTime: '2026-09-19T10:40:00' },
  { id: 9006, deviceName: 'smoke-wh-006', nickname: '烟感-仓库', productId: 52, productCode: 'sec-node', productName: '安防节点', online: 0, status: 'disabled', lastOnlineTime: '2026-08-02T09:00:00', lastReportTime: null, createTime: '2026-02-19T10:40:00' },
  { id: 9007, deviceName: 'level-tank3-007', nickname: '液位计-3号罐', productId: 53, productCode: 'energy', productName: '能源表计', online: 1, status: 'active', lastOnlineTime: '2026-11-21T20:00:00', lastReportTime: '2026-11-21T20:00:00', createTime: '2026-10-30T16:05:00' },
  { id: 9008, deviceName: 'legacy-gw-008', nickname: '旧版网关（停用）', productId: 51, productCode: 'cold-gw', productName: '冷链网关', online: 0, status: 'disabled', lastOnlineTime: '2026-06-01T09:00:00', lastReportTime: '2026-06-01T09:00:00', createTime: '2025-12-22T08:30:00' },
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
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});

/** 记录列表真实发出的查询参数：关键字必须叫 keyword，且不该出现 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/iot/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   兵部坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/iot/v1/devices/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/iot/v1/devices/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const productId = url.searchParams.get('productId');
  queried.push({
    current,
    size,
    keyword,
    productId,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：keyword 按设备名 / 昵称模糊匹配（`IotDeviceService#page`） */
  const matched = DEVICES.filter((row) => {
    const hitProduct = productId === null || productId === '' || String(row.productId) === productId;
    const hitKeyword = !keyword
      || row.deviceName.includes(keyword)
      || (row.nickname ?? '').includes(keyword);
    return hitProduct && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是设备台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="bingbu"]');
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  return {
    size: el.dataset.size,
    title: el.querySelector('.bws-title')?.textContent ?? '',
    w: Math.round(rect.width),
    h: Math.round(rect.height),
    hasPage: Boolean(el.querySelector('.bwsp')),
    tabs: [...el.querySelectorAll('.bwsp-tab')].map((tab) => tab.textContent),
    id: el.dataset.screenId,
  };
});
check('兵部坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 bingbu', mounted?.id === 'bingbu', mounted?.id);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是兵部坊 · 设备台账', String(mounted?.title).includes('设备台账'), mounted?.title);
check('入口标签来自概览 links（设备台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '设备台账', mounted?.tabs);

/* ---- 2. 概览走 /iot/overview（不是别的殿的路径） ---- */
check('概览请求打到 /iot/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/iot/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('bingbu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（设备/在线率/上报/待回指令）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（近 7 日遥测）', probe?.overview.charts === 1, probe?.overview.charts);
check('三条告警（离线 + 指令 + 规则全停）', probe?.overview.alerts === 3, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="bingbu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('设备总数按计数排版（12）', kpiCards[0]?.value === '12', kpiCards[0]);
/* 关键断言：契约给的是比率 0.8。若 provider 把 80 直接发过来，这一屏会显示 8000.0%。 */
check('在线率按比率渲染（0.8 → 80.0%，不是 8000%）', kpiCards[1]?.value === '80.0%', kpiCards[1]);
check('在线率未达 90% 亮黄灯（不是绿灯）', kpiCards[1]?.tone === 'warn', kpiCards[1]);
check('提示语写出分子分母（8 / 10 在线）',
  String(kpiCards[1]?.label).includes('8 / 10 在线'), kpiCards[1]?.label);
check('近 24h 上报 500 条并亮绿灯', kpiCards[2]?.value === '500' && kpiCards[2]?.tone === 'good', kpiCards[2]);
check('待回指令 6 条并亮黄灯', kpiCards[3]?.value === '6' && kpiCards[3]?.tone === 'warn', kpiCards[3]);

/* ---- 3. 近 7 日遥测折线：7 个点，断流那天落到基线 ---- */
/* 屏风只有 620px 高：7 根横条要吃掉近 180px，会把下面的台账挤到只剩 3 行可见，
   所以趋势用折线（时间序列本来也该用线）。断言跟着渲染器走：`.bwsp-line` 的
   `points` 是 `x,y` 对，y=30 是基线（值 0），y 越小值越大，最高点 y=4（满量程）。 */
const trend = await page.evaluate(() => {
  const svg = document.querySelector('[data-screen-id="bingbu"] .bwsp-linewrap svg.bwsp-line');
  const line = svg?.querySelector('polyline');
  const labels = [
    ...document.querySelectorAll('[data-screen-id="bingbu"] .bwsp-linewrap .bwsp-barlabels span'),
  ].map((span) => span.textContent);
  const points = (line?.getAttribute('points') ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((pair) => pair.split(',').map(Number));
  return { labels, points, rows: document.querySelectorAll('[data-screen-id="bingbu"] .bwsp-barrow').length };
});
check('趋势画成折线（不是 7 根横条）', trend.points.length === 7 && trend.rows === 0, trend);
check('横轴是月日（11-15 … 11-21）',
  trend.labels.join(',') === '11-15,11-16,11-17,11-18,11-19,11-20,11-21', trend.labels);
const trendYs = trend.points.map((pair) => pair[1]);
check('断流那天（11-20）落到基线 y=30，没有被跳过', trendYs[5] === 30, trendYs);
check('上报最多的那天（11-19，50 条）画在最高处 y=4', trendYs[4] === 4, trendYs);

/* ---- 4. 有搜索框、没有状态筛选（设备分页接口不收 status） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="bingbu"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    statusHidden: statusEl ? statusEl.hidden : null,
    statusOptions: statusEl ? statusEl.options.length : 0,
  };
});
check('有搜索框（后端关键字参数是 keyword）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（设备名 / 昵称）',
  String(filters?.placeholder).includes('设备名'), filters?.placeholder);
check('状态筛选整块收起（设备接口不收 status，不做假控件）',
  filters?.statusHidden === true, filters);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / productId 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.productId === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="bingbu"] .bwsp-table tbody tr')].map((tr) => ({
    device: tr.children[0]?.textContent ?? '',
    product: tr.children[1]?.textContent ?? '',
    online: tr.children[2]?.textContent ?? '',
    onlineTone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    report: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
let rows = await readRows();
check('设备名原样显示（deviceName）', rows[0]?.device === 'temp-sensor-001', rows[0]?.device);
check('产品名来自 join 后的 productName', rows[0]?.product === '冷链网关', rows[0]?.product);
/* 关键断言：online 是数字 1，列级字典必须把它译成"在线"。写成页面级字典时这里会吐英文 1。 */
check('在线列数字 1 译成"在线"并打绿灯（列级字典）',
  rows[0]?.online === '在线' && rows[0]?.onlineTone === 'good', rows[0]);
check('在线列数字 0 译成"离线"并打黄灯', rows[2]?.online === '离线' && rows[2]?.onlineTone === 'warn', rows[2]);
check('状态 active 译成"启用"（页面级字典，与在线是两套码值）',
  rows[0]?.status === '启用' && rows[0]?.statusTone === 'good', rows[0]);
check('最近上报只显示月日时分（11-21 21:58）', rows[0]?.report === '11-21 21:58', rows[0]?.report);
check('从未上报时显示破折号而不是 null', rows[4]?.report === '—', rows[4]?.report);
check('禁用设备的状态译成"禁用"（中性灰）',
  rows[5]?.status === '禁用' && rows[5]?.statusTone === 'flat', rows[5]);
await page.screenshot({ path: `${OUT}/40-bingbu-list.png` });

/* ---- 6. 进入屏风（贴脸态）：筛选、翻页、点行都要"在屏风里"才生效 ----
   `screenpage.ts` 里 statusSel / pager / row 的事件都带 `if (!engaged()) return;`，
   所以在 3D 里隔空点搜索框是没反应的——必须先点屏风进去。 */
await page.locator('[data-screen-id="bingbu"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="bingbu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="bingbu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'bingbu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 贴脸态才是真正要读的那一屏（3D 里那块屏只有 1/6 屏宽）：版式问题只在这里看得出来 */
await page.screenshot({ path: `${OUT}/40b-bingbu-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=液位 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('液位');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('bingbu'));
const lastSearch = queried.filter((item) => item.keyword === '液位').pop();
check('关键字按 keyword=液位 发给服务端', Boolean(lastSearch), queried.slice(-3));
check('搜索后只剩 1 条（液位计-3号罐）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('搜索出来的正是 level-tank3-007', rows[0]?.device === 'level-tank3-007', rows[0]?.device);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 分页：第 2 页 2 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('bingbu'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是液位计（page 参数真的生效了）', rows[0]?.device === 'level-tank3-007', rows[0]?.device);
check('已停用的旧网关仍按"禁用"译（不是空白）', rows[1]?.status === '禁用', rows[1]);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('bingbu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（列表行就是详情）', detailHits.length === 0, detailHits);
check('详情 9 个字段用列表行数据（含昵称/产品编码/两个时间）',
  probe?.detail.fields === 9 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/41-bingbu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('bingbu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="bingbu"]');
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
await page.route('**/iot/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/iot/v1/devices/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="bingbu"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="bingbu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('bingbu');
  const empty = document.querySelector('[data-screen-id="bingbu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="bingbu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/42-bingbu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
