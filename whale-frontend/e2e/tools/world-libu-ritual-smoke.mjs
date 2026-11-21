// 礼部坊客户台账（S6 / T6.10–T6.11）冒烟：内城十二坊的第三块屏风页。
//
// 与工部/吏部的分工：工部验"车间在不在跑"、吏部验"人有没有到岗"，这一条验**客户**——
// 本月卖了多少、离目标还差多少、哪些客户该跟。五件事：
//   ① 概览四张牌里"目标达成"必须是**比率**（0.94 → 94.0%）。后端给的是百分数 94，
//      provider 负责除 100；这里断的是屏风上的字，正是最容易漏掉的那一步；
//   ② 客户台账**有搜索框**（`CustomerPageParams.keyword` 真支持），且发出去的参数名
//      必须就是 `keyword`；等级列与状态列是**两套码值**，靠列级字典分开译；
//   ③ 等级要兼容存量 `KEY / NORMAL / POTENTIAL`——不映射就会在列里看到英文 KEY；
//   ④ 列表行即详情（不配 detailPath）：`/crm/customer/page` 返回的已是整行明细；
//   ⑤ 接口全挂时不白屏：标 stale + 给一句人话。
//
// 数据来源：page.route 拦 /crm/overview 与 /crm/customer/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十三块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="libu-ritual"] ';

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
/* 与 CrmOverviewProvider 的实际下发一致：四张 KPI（达成率是比率）、
   一张目标/赢单/管道柱图、两条告警、一个客户台账入口 */
const OVERVIEW = {
  service: 'crm',
  nodeKey: 'libu-ritual-hall',
  title: '礼部',
  subtitle: '客户经营 · 2026-11',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'month-won', label: '本月赢单', value: 120000, format: 'money', tone: 'flat', hint: '目标 ¥40.0万' },
    { key: 'target-rate', label: '目标达成', value: 0.94, format: 'percent', tone: 'warn', hint: '赢单 ÷ 目标' },
    { key: 'pipeline', label: '加权管道', value: 180000, format: 'money', tone: 'flat', hint: '阶段胜率折算' },
    { key: 'risk', label: '风险商机', value: 3, format: 'count', tone: 'warn', hint: '已过关闭日' },
  ],
  charts: [
    {
      key: 'month-target',
      title: '本月目标 / 赢单 / 管道',
      type: 'bar',
      format: 'money',
      categories: ['目标', '已赢单', '加权管道'],
      series: [{ name: '金额', values: [400000, 120000, 180000] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 3 个商机已过预计关闭日', count: 3 },
    { level: 'info', text: '按加权管道测算，本月目标还差 ¥10.0万', count: null },
  ],
  links: [
    { key: 'customers', label: '客户台账', path: '/crm/customer/page', permission: 'sys:crm:customer:list' },
  ],
  notes: ['口径：CRM 销售看板 /crm/analytics/dashboard，与 2D 同源；数据范围按角色（管理员看全租户，其余看本人）'],
};

/**
 * 8 位客户：等级覆盖 A/B/C/D 与三个存量码（KEY/NORMAL/POTENTIAL），
 * 状态覆盖 ACTIVE/INACTIVE/MERGED；6 行/页 → 2 页
 */
const CUSTOMERS = [
  { id: 7001, customerCode: 'CUS0001', name: '蓝鲸重工', shortName: '蓝鲸', status: 'ACTIVE', level: 'A', industry: '装备制造', source: '展会', scale: '500-999人', ownerId: 900000101, address: '青岛', website: 'lanjing.example', lastFollowUpAt: '2026-11-20T10:00:00', createTime: '2026-03-01T09:12:00' },
  { id: 7002, customerCode: 'CUS0002', name: '海图科技', shortName: '海图', status: 'ACTIVE', level: 'B', industry: '软件', source: '官网', scale: '100-499人', ownerId: 900000101, address: '深圳', website: null, lastFollowUpAt: '2026-11-18T15:30:00', createTime: '2026-04-11T11:00:00' },
  { id: 7003, customerCode: 'CUS0003', name: '潮汐能源', shortName: '潮汐', status: 'ACTIVE', level: 'KEY', industry: '新能源', source: '转介绍', scale: '1000人以上', ownerId: 900000102, address: '上海', website: null, lastFollowUpAt: '2026-11-21T08:45:00', createTime: '2026-01-08T14:20:00' },
  { id: 7004, customerCode: 'CUS0004', name: '深蓝物流', shortName: '深蓝', status: 'INACTIVE', level: 'C', industry: '物流', source: '电话', scale: '100-499人', ownerId: 900000102, address: '宁波', website: null, lastFollowUpAt: '2026-09-02T09:00:00', createTime: '2025-12-19T10:40:00' },
  { id: 7005, customerCode: 'CUS0005', name: '鲸落文化', shortName: '鲸落', status: 'ACTIVE', level: 'D', industry: '文化传媒', source: '自然流量', scale: '20-99人', ownerId: 900000103, address: '杭州', website: null, lastFollowUpAt: null, createTime: '2026-10-30T16:05:00' },
  { id: 7006, customerCode: 'CUS0006', name: '星链航海', shortName: '星链', status: 'ACTIVE', level: 'NORMAL', industry: '航运', source: '合作伙伴', scale: '500-999人', ownerId: 900000103, address: '大连', website: null, lastFollowUpAt: '2026-11-15T13:10:00', createTime: '2026-06-22T08:30:00' },
  { id: 7007, customerCode: 'CUS0007', name: '珊瑚材料', shortName: '珊瑚', status: 'ACTIVE', level: 'POTENTIAL', industry: '新材料', source: '展会', scale: '100-499人', ownerId: 900000103, address: '厦门', website: null, lastFollowUpAt: '2026-11-19T17:20:00', createTime: '2026-08-05T10:00:00' },
  { id: 7008, customerCode: 'CUS0008', name: '老客户甲', shortName: '老甲', status: 'MERGED', level: 'B', industry: '贸易', source: '历史导入', scale: '20-99人', ownerId: 900000101, address: '天津', website: null, lastFollowUpAt: '2026-05-01T09:00:00', createTime: '2025-11-11T09:00:00' },
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

/** 记录列表真实发出的查询参数：关键字必须叫 keyword，状态必须叫 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/crm/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   礼部坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/crm/customer/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/crm/customer/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    keyword,
    status,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = CUSTOMERS.filter((row) => {
    const hitStatus = status === null || status === '' || row.status === status;
    const hitKeyword = !keyword || row.name.includes(keyword) || row.customerCode.includes(keyword);
    return hitStatus && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是客户台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="libu-ritual"]');
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
check('礼部坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 libu-ritual（与吏部的 libu 区分）', mounted?.id === 'libu-ritual', mounted?.id);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是礼部坊 · 客户台账', String(mounted?.title).includes('客户台账'), mounted?.title);
check('入口标签来自概览 links（客户台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '客户台账', mounted?.tabs);

/* ---- 2. 概览走 /crm/overview（不是别的殿的路径） ---- */
check('概览请求打到 /crm/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/crm/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（赢单/达成/管道/风险）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（目标 / 赢单 / 管道）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（风险商机 + 预测缺口）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="libu-ritual"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('本月赢单按金额排版（12 万 → ¥12.0万）', kpiCards[0]?.value === '¥12.0万', kpiCards[0]);
/* 关键断言：后端给的是百分数 94，契约要比率 0.94。倍数写错这一屏会显示 9400.0%。 */
check('目标达成按比率渲染（0.94 → 94.0%，不是 9400%）', kpiCards[1]?.value === '94.0%', kpiCards[1]);
check('目标达成未满 100% 亮黄灯（不是绿灯）', kpiCards[1]?.tone === 'warn', kpiCards[1]);
check('加权管道按金额排版（18 万 → ¥18.0万）', kpiCards[2]?.value === '¥18.0万', kpiCards[2]);
check('风险商机三件并亮黄灯', kpiCards[3]?.value === '3' && kpiCards[3]?.tone === 'warn', kpiCards[3]);
check('本月赢单的提示语写清目标（读牌的人要知道分母）',
  String(kpiCards[0]?.label).includes('¥40.0万'), kpiCards[0]?.label);

/* ---- 3. 横向条形图三个类目（目标 / 赢单 / 管道） ---- */
/* 屏风是 1000×620 的横屏，渲染器把 bar 画成**横向**条（`.bwsp-barrow` 逐行），
   不是竖柱图那个 `.bwsp-barlabels` 横轴。断言要跟着渲染器的实际结构走。 */
const chartRows = await page.evaluate(() =>
  [...(document.querySelector('[data-screen-id="libu-ritual"]')?.querySelectorAll('.bwsp-barrow') ?? [])]
    .map((row) => ({
      label: row.querySelector('.bwsp-barlabel')?.textContent ?? '',
      value: row.querySelector('.bwsp-barval')?.textContent ?? '',
    })),
);
check('条形图三个类目（目标/已赢单/加权管道）',
  chartRows.map((row) => row.label).join(',') === '目标,已赢单,加权管道', chartRows);
check('条形图金额与 KPI 同源（40/12/18 万）',
  chartRows.map((row) => row.value).join(',') === '¥40.0万,¥12.0万,¥18.0万', chartRows);

/* ---- 4. 有搜索框：CustomerPageParams 真支持 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="libu-ritual"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    status: statusEl ? [...statusEl.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('有搜索框（后端关键字参数是 keyword）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（客户编码 / 名称）',
  String(filters?.placeholder).includes('客户编码'), filters?.placeholder);
check('状态筛选四项用后端原值（ACTIVE…MERGED），全部在前',
  filters?.status.join(',') === ':全部状态,ACTIVE:有效,INACTIVE:停用,MERGED:已合并',
  filters?.status);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 status / keyword 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.status === null && queried[0]?.keyword === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="libu-ritual"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    level: tr.children[1]?.textContent ?? '',
    levelTone: tr.children[1]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    industry: tr.children[2]?.textContent ?? '',
    follow: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
let rows = await readRows();
check('客户名原样显示', rows[0]?.name === '蓝鲸重工', rows[0]?.name);
check('等级 A 译成"A 重点"并打绿灯（列级字典）', rows[0]?.level === 'A 重点' && rows[0]?.levelTone === 'good', rows[0]);
check('等级 B 译成"B 普通"（中性灰）', rows[1]?.level === 'B 普通' && rows[1]?.levelTone === 'flat', rows[1]);
check('存量码 KEY 也译成"A 重点"（不显示英文 KEY）', rows[2]?.level === 'A 重点', rows[2]);
check('状态 ACTIVE 译成"有效"并打绿灯（页面级字典）', rows[0]?.status === '有效' && rows[0]?.statusTone === 'good', rows[0]);
check('状态 INACTIVE 译成"停用"（中性灰，不是红灯）', rows[3]?.status === '停用' && rows[3]?.statusTone === 'flat', rows[3]);
/* 第 6 行是存量等级码 NORMAL：译成"C 潜力"而不是原样吐英文 */
check('存量码 NORMAL 译成"C 潜力"', rows[5]?.level === 'C 潜力', rows[5]);
check('最近跟进只显示月日时分（11-20 10:00）', rows[0]?.follow === '11-20 10:00', rows[0]?.follow);
check('没有跟进记录时显示破折号而不是 null', rows[4]?.follow === '—', rows[4]?.follow);
await page.screenshot({ path: `${OUT}/30-libu-ritual-list.png` });

/* ---- 6. 进入屏风（贴脸态）：筛选、翻页、点行都要"在屏风里"才生效 ----
   `screenpage.ts` 里 statusSel / pager / row 的事件都带 `if (!engaged()) return;`，
   所以在 3D 里隔空点下拉是没反应的——必须先点屏风进去。 */
await page.locator('[data-screen-id="libu-ritual"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="libu-ritual"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="libu-ritual"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'libu-ritual', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 贴脸态才是真正要读的那一屏（3D 里那块屏只有 1/6 屏宽）：版式问题只在这里看得出来 */
await page.screenshot({ path: `${OUT}/30b-libu-ritual-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=蓝鲸 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('蓝鲸');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
const lastSearch = queried.filter((item) => item.keyword === '蓝鲸').pop();
check('关键字按 keyword=蓝鲸 发给服务端', Boolean(lastSearch), queried.slice(-3));
check('搜索后只剩 1 条（蓝鲸重工）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('搜索出来的正是蓝鲸重工', rows[0]?.name === '蓝鲸重工', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=MERGED ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('MERGED');
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
const lastFilter = queried.filter((item) => item.status === 'MERGED').pop();
check('状态按 status=MERGED 发给服务端', Boolean(lastFilter), queried.slice(-3));
check('筛选后只剩 1 条（老客户甲）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('合并客户译成"已合并"（不是英文 MERGED）', rows[0]?.status === '已合并', rows[0]);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(700);

/* ---- 9. 分页：第 2 页 2 条，且存量等级码在第二页也能译 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
/* 第 2 页是第 7、8 条：珊瑚材料（存量码 POTENTIAL）、老客户甲（已合并） */
check('存量码 POTENTIAL 译成"D 观察"并打黄灯', rows[0]?.level === 'D 观察' && rows[0]?.levelTone === 'warn', rows[0]);
check('已合并客户的等级仍按 B 译（等级与状态是两套码值）', rows[1]?.level === 'B 普通', rows[1]);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（列表行就是详情）', detailHits.length === 0, detailHits);
check('详情 13 个字段用列表行数据（含编码/负责人/地址）',
  probe?.detail.fields === 13 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/31-libu-ritual-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu-ritual'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="libu-ritual"]');
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
await page.route('**/crm/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/crm/customer/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="libu-ritual"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="libu-ritual"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('libu-ritual');
  const empty = document.querySelector('[data-screen-id="libu-ritual"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="libu-ritual"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/32-libu-ritual-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
