// 工部坊生产工单（S6 / T6.4–T6.5）冒烟：内城十二坊的第一块屏风页。
//
// 与皇城九殿的分工：皇城验的是"中枢还在不在跑"（登录、支付、任务、流程…），
// 这一条验的是**车间的事**，四件事：
//   ① 概览四张 KPI 里有一张百分比牌——达成率必须按**比率**下发（0.9），
//      前端排版成 90.0%；若服务端直接发 90，就会显示 9000.0%。
//      这条断言写死了"90.0%"这个字符串，就是为了让"发百分数"的写法当场失败；
//   ② 关键字参数名是 `code`（不是 keyword）——MES 工单查询只有
//      code/name/erpOrderNo/itemCode/status/priority 六个条件，**没有 keyword**；
//   ③ 工单状态是七个枚举（RECEIVED…CANCELLED），靠页面级字典译中文并打灯；
//   ④ 列表行即详情（不配 detailPath）：点行不发二次请求，详情字段用列表行数据。
//
// 数据来源：page.route 拦 /mes/overview 与 /mes/v1/work-order/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十一块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="gongbu"] ';

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
/* 达成率传比率 0.9（不是 90）：与 MesOverviewProvider 的实际口径一致 */
const OVERVIEW = {
  service: 'mes',
  nodeKey: 'gongbu-hall',
  title: '工部',
  subtitle: '制造执行 · 2026-11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'completed', label: '今日完成', value: 1080, format: 'count', tone: 'flat', hint: '合格数', display: '1,080' },
    { key: 'achievement', label: '达成率', value: 0.9, format: 'percent', tone: 'flat', hint: '目标 1200', display: '90.0%' },
    { key: 'exceptions', label: '未关闭', value: 5, format: 'count', tone: 'warn', hint: '异常 / NCR', display: '5' },
    { key: 'faults', label: '故障设备', value: 2, format: 'count', tone: 'bad', hint: '共 48 台', display: '2' },
  ],
  charts: [
    {
      key: 'output',
      title: '今日 目标 / 完成 / 不良',
      type: 'bar',
      format: 'count',
      categories: ['目标', '完成', '不良'],
      series: [{ name: '数量', values: [1200, 1080, 12] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '2 台设备处于故障状态', count: 2 },
    { level: 'warning', text: '有 3 条未关闭生产异常', count: 3 },
    { level: 'warning', text: '有 2 张 NCR 未关闭', count: 2 },
    { level: 'info', text: '1 个样本命中 SPC 规则', count: 1 },
  ],
  links: [
    { key: 'work-orders', label: '生产工单', path: '/mes/v1/work-order/page', permission: 'sys:mes:workorder:list' },
  ],
  notes: ['口径：MES 综合看板快照'],
};

/** 8 张工单：状态覆盖七个枚举中的五个，够验字典与分页 */
const ORDERS = [
  { id: 9101, code: 'WO-2026-1101', name: '主机架总装', status: 'IN_PROGRESS', erpOrderNo: 'PO-2026-0041', itemCode: 'M-1001', itemName: '鲸骨主机架', plannedQuantity: 200, uom: '台', routingVersion: 'R3', priority: 'URGENT', plannedStart: '2026-11-21T08:00:00', plannedEnd: '2026-11-23T18:00:00' },
  { id: 9102, code: 'WO-2026-1102', name: '鳍片冲压', status: 'PAUSED', erpOrderNo: 'PO-2026-0042', itemCode: 'M-1002', itemName: '尾鳍片', plannedQuantity: 1500, uom: '件', routingVersion: 'R2', priority: 'NORMAL', plannedStart: '2026-11-21T09:00:00', plannedEnd: '2026-11-22T18:00:00' },
  { id: 9103, code: 'WO-2026-1103', name: '喷水孔钻孔', status: 'COMPLETED', erpOrderNo: 'PO-2026-0043', itemCode: 'M-1003', itemName: '喷水孔板', plannedQuantity: 800, uom: '件', routingVersion: 'R1', priority: 'NORMAL', plannedStart: '2026-11-20T08:00:00', plannedEnd: '2026-11-21T12:00:00' },
  { id: 9104, code: 'WO-2026-1104', name: '尾鳍打磨', status: 'DISPATCHED', erpOrderNo: 'PO-2026-0044', itemCode: 'M-1004', itemName: '尾鳍', plannedQuantity: 60, uom: '件', routingVersion: 'R2', priority: 'NORMAL', plannedStart: '2026-11-22T08:00:00', plannedEnd: '2026-11-24T18:00:00' },
  { id: 9105, code: 'WO-2026-1105', name: '舱壁焊接', status: 'RECEIVED', erpOrderNo: 'PO-2026-0045', itemCode: 'M-1005', itemName: '耐压舱壁', plannedQuantity: 24, uom: '件', routingVersion: 'R4', priority: 'URGENT', plannedStart: '2026-11-22T09:00:00', plannedEnd: '2026-11-26T18:00:00' },
  { id: 9106, code: 'WO-2026-1106', name: '声呐罩成形', status: 'CANCELLED', erpOrderNo: 'PO-2026-0039', itemCode: 'M-1006', itemName: '声呐罩', plannedQuantity: 12, uom: '件', routingVersion: 'R1', priority: 'NORMAL', plannedStart: '2026-11-19T08:00:00', plannedEnd: '2026-11-20T18:00:00' },
  { id: 9107, code: 'WO-2026-1107', name: '推进轴校直', status: 'IN_PROGRESS', erpOrderNo: 'PO-2026-0046', itemCode: 'M-1007', itemName: '推进轴', plannedQuantity: 8, uom: '根', routingVersion: 'R5', priority: 'NORMAL', plannedStart: '2026-11-21T07:00:00', plannedEnd: '2026-11-24T18:00:00' },
  { id: 9108, code: 'WO-2026-1108', name: '压载泵装配', status: 'CLOSED', erpOrderNo: 'PO-2026-0040', itemCode: 'M-1008', itemName: '压载泵', plannedQuantity: 30, uom: '台', routingVersion: 'R2', priority: 'NORMAL', plannedStart: '2026-11-18T08:00:00', plannedEnd: '2026-11-20T18:00:00' },
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

/** 记录屏风页真实发出的查询参数：关键字必须是 code，不是 keyword */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/mes/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   工部坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/mes/v1/work-order/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/mes/v1/work-order/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 MesWorkOrderQueryRequest 的编码条件叫 code（模糊），没有 keyword
  const code = url.searchParams.get('code');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    code,
    status,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = ORDERS.filter((row) => {
    const byCode = code === null || code === '' || row.code.includes(code);
    const byStatus = status === null || status === '' || row.status === status;
    return byCode && byStatus;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是生产工单页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="gongbu"]');
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
check('工部坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 gongbu（探针与 e2e 都靠它定位）', mounted?.id === 'gongbu', mounted?.id);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是工部坊 · 生产工单', String(mounted?.title).includes('生产工单'), mounted?.title);
check('入口标签来自概览 links（生产工单）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '生产工单', mounted?.tabs);

/* ---- 2. 概览走 /mes/overview（不是别的殿的路径） ---- */
check('概览请求打到 /mes/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/mes/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（完成/达成率/未关闭/故障设备）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（今日目标/完成/不良）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警都下发（故障/异常/NCR/SPC，根因不同）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="gongbu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('今日完成按千分位排版', kpiCards[0]?.value === '1,080', kpiCards[0]?.value);
/* 关键断言：0.9 的比率排成 90.0%。若服务端发的是 90，这里会是 9000.0%。 */
check('达成率按比率排版成 90.0%（不是 9000.0%）', kpiCards[1]?.value === '90.0%', kpiCards[1]?.value);
check('达成率亮中性灯（90% 未跌破 95%，但也谈不上达标）', kpiCards[1]?.tone === 'flat', kpiCards[1]?.tone);
check('未关闭亮黄灯', kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('故障设备亮红灯（停机要找设备科）', kpiCards[3]?.tone === 'bad' && kpiCards[3]?.value === '2', kpiCards[3]);

/* ---- 3. 横向条形图三个类目（目标/完成/不良） ---- */
/* 屏风是 1000×620 的横屏，渲染器把 bar 画成**横向**条（`.bwsp-barlabel` 逐行），
   不是竖柱图那个 `.bwsp-barlabels` 横轴。断言要跟着渲染器的实际结构走。 */
const chartRows = await page.evaluate(() =>
  [...(document.querySelector('[data-screen-id="gongbu"]')?.querySelectorAll('.bwsp-barrow') ?? [])]
    .map((row) => ({
      label: row.querySelector('.bwsp-barlabel')?.textContent ?? '',
      value: row.querySelector('.bwsp-barval')?.textContent ?? '',
    })),
);
check('条形图三个类目（目标/完成/不良）',
  chartRows.map((row) => row.label).join(',') === '目标,完成,不良', chartRows);
check('条形图数值与概览同源（1,200 / 1,080 / 12）',
  chartRows.map((row) => row.value).join(',') === '1,200,1,080,12', chartRows);

/* ---- 4. 有搜索框：关键字参数是 code，不是 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="gongbu"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    searchPlaceholder: input?.placeholder ?? '',
    status: statusEl ? [...statusEl.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('有搜索框（工单接口支持 code 模糊匹配）', filters?.searchHidden === false, filters);
check('搜索框提示语是"搜索工单编码…"（说清搜的是哪个字段）',
  String(filters?.searchPlaceholder).includes('工单编码'), filters?.searchPlaceholder);
check('状态筛选七项用后端原值（RECEIVED…CANCELLED），全部在前',
  filters?.status.join(',') ===
    ':全部状态,RECEIVED:已接收,DISPATCHED:已派工,IN_PROGRESS:生产中,PAUSED:暂停,COMPLETED:已完工,CLOSED:已关闭,CANCELLED:已取消',
  filters?.status);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 code/status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.code === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="gongbu"] .bwsp-table tbody tr')].map((tr) => ({
    code: tr.children[0]?.textContent ?? '',
    item: tr.children[1]?.textContent ?? '',
    qty: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    start: tr.children[4]?.textContent ?? '',
  })),
);
check('工单编码原样显示', rows[0]?.code === 'WO-2026-1101', rows[0]?.code);
check('物料名原样显示（列表不带物料编码，编码在详情）', rows[0]?.item === '鲸骨主机架', rows[0]?.item);
check('计划量按千分位排版', rows[1]?.qty === '1,500', rows[1]?.qty);
check('IN_PROGRESS 译成"生产中"并打绿灯', rows[0]?.status === '生产中' && rows[0]?.statusTone === 'good', rows[0]);
check('PAUSED 译成"暂停"并打黄灯（暂停不是已完结）', rows[1]?.status === '暂停' && rows[1]?.statusTone === 'warn', rows[1]);
check('计划开工只到日（不把时间戳原样砸在列表上）', /^\d{4}-\d{2}-\d{2}$/.test(rows[0]?.start ?? ''), rows[0]?.start);
await page.screenshot({ path: `${OUT}/10-gongbu-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="gongbu"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="gongbu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="gongbu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'gongbu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 关键字搜索：发 code，不发 keyword ---- */
await page.locator(SCOPE + '.bwsp-search').fill('1102');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
const lastSearch = queried.filter((item) => item.code === '1102').pop();
check('关键字按 code=1102 发给服务端（不是自造 keyword 参数）', Boolean(lastSearch), queried.slice(-3));
check('搜索时也不夹带 keyword', lastSearch ? !lastSearch.names.includes('keyword') : false, lastSearch?.names);
check('筛选后只剩 1 条（WO-2026-1102）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(700);

/* ---- 8. 状态筛选：发 status=PAUSED ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('PAUSED');
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
check('状态按 status=PAUSED 发给服务端', queried.some((item) => item.status === 'PAUSED'), queried.slice(-3));
check('筛选后只剩暂停的 1 条', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(700);

/* ---- 9. 分页：第 2 页 2 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口，详情用列表行数据 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（没有 /mes/v1/work-order/{id} 请求）', detailHits.length === 0, detailHits);
check('详情 12 个字段用列表行数据（工单无分录，不额外请求）',
  probe?.detail.fields === 12 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/11-gongbu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('gongbu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="gongbu"]');
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

/* ---- 12. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/mes/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/mes/v1/work-order/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="gongbu"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="gongbu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('gongbu');
  const empty = document.querySelector('[data-screen-id="gongbu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="gongbu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/12-gongbu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
