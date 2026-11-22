// 盟府合同台账（S6 / T6.25）冒烟：内城十二坊的第十一块屏风页。
//
// 与前几坊的分工：将作验"器物"、营造验"工程节奏"，这一条验**纸面上的承诺**——
// 合同金额、状态、到期。金额是盟府独有的维度：别的坊数件数，这里数钱。
//
// 九件事：
//   ① 概览四张牌走 /contract/overview：生效中 / 在途 / 本月到期 / 已过期仍生效，
//      "已过期仍生效"是唯一带重语气的牌——到期了还没人动亮红灯、清空了亮绿灯；
//   ② 金额分布画成**柱图不是折线**：盟府的账是钱账，五类金额码值固定；
//      柱值走 money 口径（¥ + 万折算），不是裸数字；
//   ③ 合同台账**没有搜索框**：code 与 title 是两个独立条件（后端无合并
//      keyword），硬拼一个关键字要么只匹配编号要么只匹配标题，宁可不摆；
//   ④ 类别列用**列级字典**（PURCHASE/SALES/SERVICE/LEASE/OTHER），
//      页面级字典留给状态列（十一态）——两套码空间同屏，只有列级覆盖分得开；
//   ⑤ 列表行即详情（不配 detailPath）：ContractVO 行内带回 parties/versions/flows，
//      详情字段用行数据；
//   ⑥ 状态灯：生效=绿、审批/签署=黄、归档/草稿/撤回=灰、驳回/终止/作废=红——
//      归档是沉淀不是事故，驳回是"这版没成得重来"；
//   ⑦ 金额走 money 口径：1280000 → ¥128.0万，8500 → ¥8,500（不猜币种）；
//   ⑧ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十七块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="mengfu"] ';

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
/* 与 CtOverviewProvider 的实际下发一致：四张 KPI、一张五类金额柱图、
   五条告警（过期 + 到期 + 外币 + 起草 + 驳回）、一个合同台账入口 */
const OVERVIEW = {
  service: 'contract',
  nodeKey: 'mengfu-hall',
  title: '盟府',
  subtitle: '合同印章 · 契约台账',
  generatedAt: '2026-11-22T06:45:00+08:00',
  state: 'live',
  kpis: [
    { key: 'effective', label: '生效中合同', value: 40, format: 'count', tone: 'flat', hint: '已归档 30 · 已终止 5' },
    { key: 'in-flight', label: '在途合同', value: 26, format: 'count', tone: 'flat', hint: '审批中 5 · 签署中 6' },
    { key: 'expiring', label: '本月到期', value: 6, format: 'count', tone: 'warn', hint: '30 天续签窗口，提前谈' },
    { key: 'expired-active', label: '已过期仍生效', value: 2, format: 'count', tone: 'bad', hint: '要么续签要么终止，不能悬着' },
  ],
  charts: [
    {
      key: 'effective-money',
      title: '生效中合同金额分布',
      type: 'bar',
      format: 'money',
      categories: ['采购', '销售', '服务', '租赁', '其他'],
      series: [{ name: '金额（CNY）', values: [1280000, 2360000, 860000, 0, 0] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '有 2 份合同已过期仍挂着生效，续签或终止得有人拍板', count: 2 },
    { level: 'warning', text: '有 6 份合同 30 天内到期，续签要提前谈', count: 6 },
    { level: 'info', text: '有 1 份外币生效合同未计入金额图（不同币种不相加）', count: 1 },
    { level: 'info', text: '有 3 份合同起草后一直没提交，纸面上还什么都没有', count: 3 },
    { level: 'info', text: '有 3 份合同被驳回或撤回，这版没成得重来', count: 3 },
  ],
  links: [{ key: 'contracts', label: '合同台账', path: '/contract/v1/page', permission: 'sys:contract:list' }],
  notes: ['口径：在途 = 审批中 + 已批待签 + 签署中 + 已签待生效；本月到期与 2D 台账 expiringSoon 同为 30 天窗口；已过期仍生效 = 到期日早于今天但状态仍是 EFFECTIVE；金额图只合计 CNY'],
};

/**
 * 9 份合同：状态六态全覆盖（生效 ×4、签署、审批、草稿、归档、终止），
 * 类别五类全覆盖。6 行/页 → 2 页。
 */
const CONTRACTS = [
  { id: 9001, code: 'CT-2026-001', title: '钢材年度采购框架', category: 'PURCHASE', amount: 1280000, currency: 'CNY', status: 'EFFECTIVE', effectiveDate: '2026-01-01', expireDate: '2026-12-31', daysToExpire: 39, remark: '季度结算' },
  { id: 9002, code: 'CT-2026-002', title: '园区云计算服务', category: 'SERVICE', amount: 860000, currency: 'CNY', status: 'EFFECTIVE', effectiveDate: '2026-02-01', expireDate: '2027-01-31', daysToExpire: 70, remark: '' },
  { id: 9003, code: 'CT-2026-003', title: '整机组销售总包', category: 'SALES', amount: 2360000, currency: 'CNY', status: 'EFFECTIVE', effectiveDate: '2026-03-15', expireDate: '2026-12-15', daysToExpire: 23, remark: '分三期交付' },
  { id: 9004, code: 'CT-2026-004', title: '仓库场地租赁', category: 'LEASE', amount: 0, currency: 'CNY', status: 'EFFECTIVE', effectiveDate: '2026-04-01', expireDate: '2026-06-30', daysToExpire: -30, remark: '已过期待续签' },
  { id: 9005, code: 'CT-2026-005', title: '海外经销代理', category: 'SALES', amount: 95000, currency: 'USD', status: 'EFFECTIVE', effectiveDate: '2026-05-01', expireDate: '2027-04-30', daysToExpire: 160, remark: '外币合同' },
  { id: 9006, code: 'CT-2026-006', title: '设备安装分包', category: 'SERVICE', amount: 8500, currency: 'CNY', status: 'SIGNING', effectiveDate: null, expireDate: null, daysToExpire: null, remark: '双方用印中' },
  { id: 9007, code: 'CT-2026-007', title: '年度审计服务', category: 'SERVICE', amount: 120000, currency: 'CNY', status: 'APPROVING', effectiveDate: null, expireDate: null, daysToExpire: null, remark: '审批中' },
  { id: 9008, code: 'CT-2026-008', title: '新仓储意向', category: 'LEASE', amount: 300000, currency: 'CNY', status: 'DRAFT', effectiveDate: null, expireDate: null, daysToExpire: null, remark: '起草未提交' },
  { id: 9009, code: 'CT-2025-018', title: '旧系统运维合同', category: 'OTHER', amount: 450000, currency: 'CNY', status: 'TERMINATED', effectiveDate: '2025-07-01', expireDate: '2026-06-30', daysToExpire: null, remark: '提前终止' },
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

/** 记录列表真实发出的查询参数：这里必须**没有** keyword（后端无合并关键字） */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/contract/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   盟府不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/contract/v1/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page') || url.pathname.endsWith('/overview')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/contract/v1/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    status,
    keys: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：status 精确匹配（`ContractQueryRequest`） */
  const matched = CONTRACTS.filter((row) => !status || row.status === status);
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是合同台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="mengfu"]');
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
check('盟府屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 mengfu', mounted?.id === 'mengfu', mounted?.id);
check('大屏规格（六列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是盟府 · 合同台账', String(mounted?.title).includes('合同台账'), mounted?.title);
check('入口标签来自概览 links（合同台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '合同台账', mounted?.tabs);

/* ---- 2. 概览走 /contract/overview ---- */
check('概览请求打到 /contract/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/contract/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（生效/在途/到期/过期）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（生效合同金额分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('五条告警（过期 + 到期 + 外币 + 起草 + 驳回）', probe?.overview.alerts === 5, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="mengfu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('生效中 40 份、灰灯（只报数不喊事）', kpiCards[0]?.value === '40' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('生效牌写出沉淀（已归档 30 · 已终止 5）',
  String(kpiCards[0]?.label).includes('已归档 30 · 已终止 5'), kpiCards[0]?.label);
check('在途 26 份亮灰灯并拆审批与签署两段',
  kpiCards[1]?.value === '26' && kpiCards[1]?.tone === 'flat'
    && String(kpiCards[1]?.label).includes('审批中 5 · 签署中 6'), kpiCards[1]);
check('本月到期 6 份亮黄灯（续签要提前谈）', kpiCards[2]?.value === '6' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
/* 关键断言：已过期仍生效是四张牌里唯一带重语气的——到期了还没人动红灯 */
check('已过期仍生效 2 份亮红灯', kpiCards[3]?.value === '2' && kpiCards[3]?.tone === 'bad', kpiCards[3]);
check('过期牌提示语写明处置（要么续签要么终止）',
  String(kpiCards[3]?.label).includes('要么续签要么终止'), kpiCards[3]?.label);

/* ---- 3. 金额分布柱图：五类金额、不是折线、柱值是 money 口径 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="mengfu"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  const values = [...(scope?.querySelectorAll('.bwsp-barval') ?? [])].map((span) => span.textContent);
  return {
    rows,
    labels,
    values,
    lineCount: scope?.querySelectorAll('svg.bwsp-line').length ?? 0,
  };
});
check('金额分布画成柱图（不是折线）', barChart.rows.length === 5 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是采购/销售/服务/租赁/其他（服务端定的标签，屏风不译码）',
  barChart.labels.join(',') === '采购,销售,服务,租赁,其他', barChart.labels);
check('柱值按金额折算（¥128.0万 / ¥236.0万 / ¥86.0万 / ¥0 / ¥0）',
  barChart.values.join(',') === '¥128.0万,¥236.0万,¥86.0万,¥0,¥0', barChart.values);

/* ---- 4. 没有搜索框（code/title 是两个独立条件），状态筛选给全 ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="mengfu"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    statusHidden: statusEl ? statusEl.hidden : null,
    statusValues: statusEl ? [...statusEl.options].map((option) => option.value) : [],
  };
});
check('不配搜索框（后端 code/title 是两个独立条件，合并关键字不存在）',
  filters?.searchHidden === true, filters);
check('有状态筛选（分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项覆盖七个常用态',
  filters?.statusValues.join(',') === ',EFFECTIVE,SIGNING,APPROVING,DRAFT,ARCHIVED,TERMINATED', filters?.statusValues);

/* ---- 5. 首屏列表：9 份分 2 页，只带分页参数（不发明 keyword） ---- */
check('首屏 6 行 / 共 9 份 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6 && queried[0]?.status === null
    && queried[0].keys.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="mengfu"] .bwsp-table tbody tr')].map((tr) => ({
    code: tr.children[0]?.textContent ?? '',
    title: tr.children[1]?.textContent ?? '',
    category: tr.children[2]?.textContent ?? '',
    amount: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    expire: tr.children[5]?.textContent ?? '',
  })),
);
let rows = await readRows();
check('合同编号原样显示', rows[0]?.code === 'CT-2026-001', rows[0]?.code);
check('标题原样显示', rows[0]?.title === '钢材年度采购框架', rows[0]?.title);
check('类别列按列级字典译成中文（PURCHASE → 采购）', rows[0]?.category === '采购', rows[0]?.category);
check('第二行类别是服务（SERVICE → 服务，不蹭页面级状态字典）',
  rows[1]?.category === '服务', rows[1]?.category);
check('金额走 money 口径（1280000 → ¥128.0万）', rows[0]?.amount === '¥128.0万', rows[0]?.amount);
check('小金额不折万（8500 → ¥8,500）', rows[5]?.amount === '¥8,500', rows[5]?.amount);
check('EFFECTIVE 译成"生效中"并打绿灯',
  rows[0]?.status === '生效中' && rows[0]?.statusTone === 'good', rows[0]);
check('到期日按 date 口径显示（只留年月日）', rows[0]?.expire === '2026-12-31', rows[0]?.expire);
await page.screenshot({ path: `${OUT}/70-mengfu-list.png` });

/* ---- 6. 先走到屏风跟前再进：盟府在内城西北角（x=-53.74, z=+53.74），
   出生点镜头朝北看不见它——必须先走。

   屏风的世界位姿直接从 CSS3D 变换矩阵里解：matrix3d 是列主序，
   第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴（即屏风正面法线，
   与 `districtScreenPose` 的 front 同口径）。站到法线外侧 4.5 米，
   相机 yaw = atan2(nx, nz)（view dir = −(sin yaw, cos yaw)）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="mengfu"]');
  const t = el?.parentElement?.style.transform || el?.style.transform || '';
  const m = t.match(/matrix3d\(([^)]+)\)/);
  if (!m) return null;
  const v = m[1].split(',').map(Number);
  const zx = v[8];
  const zz = v[10];
  const len = Math.hypot(zx, zz) || 1;
  return { x: v[12], z: v[14], nx: zx / len, nz: zz / len };
});
check('能从 CSS3D 变换里解出屏风位姿', Boolean(pose) && Number.isFinite(pose?.x), pose);
await page.evaluate(({ pose }) => {
  const px = pose.x + 4.5 * pose.nx;
  const pz = pose.z + 4.5 * pose.nz;
  window.__bwwWalkTo(px, pz);
  window.__bwwLook(Math.atan2(pose.nx, pose.nz), 0.18, 9);
}, { pose });
await page.waitForTimeout(9000);
/* 不用 locator.click：走到跟前时屏风可能落在小地图面板底下（hit-target 被拦），
   而且屏风带 3D 变换，包围盒中心未必落在元素上。点击入口是 screen.el 上的
   click 监听（与将作坊同一套），直接派发 click 事件走同一逻辑。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="mengfu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="mengfu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="mengfu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'mengfu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/71b-mengfu-engaged.png` });

/* ---- 7. 状态筛选：发 status=EFFECTIVE，只剩四份生效 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('EFFECTIVE');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
const lastStatus = queried.filter((item) => item.status === 'EFFECTIVE').pop();
check('状态按 status=EFFECTIVE 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('生效只剩 5 份（含 1 份外币）', probe?.list.total === 5 && probe?.list.rows === 5, probe?.list);
rows = await readRows();
check('四份生效合同里有过期的（第 4 份 daysToExpire 已是负数）',
  rows.some((row) => row.code === 'CT-2026-004'), rows.map((row) => row.code));
check('生效四份全部绿灯',
  rows.every((row) => row.status === '生效中' && row.statusTone === 'good'), rows);
await page.locator(SCOPE + '.bwsp-status').selectOption('TERMINATED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
rows = await readRows();
check('终止态剩 1 份并打红灯（提前终止不是沉淀是事故）',
  probe?.list.total === 1 && rows[0]?.status === '已终止' && rows[0]?.statusTone === 'bad', rows[0]);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 8. 分页：第 2 页 3 份 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
check('第 2 页剩 3 份', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是年度审计服务（page 参数真的生效了）',
  rows[0]?.title === '年度审计服务', rows[0]?.title);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（ContractVO 行内带回分录）',
  detailHits.length === 0, detailHits);
check('详情 10 个字段用列表行数据（含币种 / 距到期）',
  probe?.detail.fields === 10 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/72-mengfu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('mengfu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="mengfu"]');
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
await page.route('**/contract/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/contract/v1/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="mengfu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="mengfu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('mengfu');
  const empty = document.querySelector('[data-screen-id="mengfu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="mengfu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/73-mengfu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
