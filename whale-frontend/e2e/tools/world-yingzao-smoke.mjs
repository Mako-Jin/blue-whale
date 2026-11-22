// 营造坊项目台账（S6 / T6.22）冒烟：内城十二坊的第十块屏风页。
//
// 与工部/吏部/礼部/兵部/杏坛/将作的分工：工部验"车间在不在跑"、吏部验"人有没有到岗"、
// 礼部验"客户"、兵部验"哨探"、杏坛验"人有没有在长本事"、将作验"器物"，
// 这一条验**工程节奏**——有多少项工程在同时开工、有多少活拖过期限、有多少活没人接手。
//
// 九件事：
//   ① 概览四张牌走 /pm/overview，四张都是计数（没有比率牌），
//      "逾期未完成"是唯一带重语气的牌——有活拖期亮红灯、清空了亮绿灯；
//   ② 类型分布画成**柱图不是折线**：营造的账是存量不是流量，五种类型码值固定；
//   ③ 项目台账**有搜索框也有状态筛选**，关键字参数是 `keyword`——
//      项目查询是少数后端就有合并关键字的（编码与名称一起模糊匹配）；
//   ④ 类型与可见性列用**列级字典**（SCRUM/KANBAN、PRIVATE/TEAM/PUBLIC），
//      页面级字典留给状态列（ACTIVE/ARCHIVED）——两套码空间同屏，只有列级覆盖分得开；
//   ⑤ 列表行即详情（不配 detailPath）：`ProjectVO` 平铺，行内字段够详情用；
//   ⑥ 已归档给灰灯——归档是沉淀不是事故；
//   ⑦ 负责人列不摆：leadUserId 是用户 ID，屏风拿不到用户名，摆出来是一串数字；
//   ⑧ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十六块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="yingzao"] ';

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
/* 与 PmOverviewProvider 的实际下发一致：四张 KPI（全是计数）、
   一张五类工作项柱图、四条告警、一个项目台账入口 */
const OVERVIEW = {
  service: 'pm',
  nodeKey: 'yingzao-bureau',
  title: '营造',
  subtitle: '工程调度 · 三本账',
  generatedAt: '2026-11-22T06:45:00+08:00',
  state: 'live',
  kpis: [
    { key: 'projects', label: '在建项目', value: 7, format: 'count', tone: 'flat', hint: '归档 2 个' },
    { key: 'sprints', label: '活跃迭代', value: 2, format: 'count', tone: 'flat', hint: '未启动 3 · 已关闭 15' },
    { key: 'items-open', label: '未完成工作项', value: 60, format: 'count', tone: 'flat', hint: '待办 35 · 进行中 25' },
    { key: 'items-overdue', label: '逾期未完成', value: 6, format: 'count', tone: 'bad', hint: '高优先级 2' },
  ],
  charts: [
    {
      key: 'item-types',
      title: '未完成工作项类型分布',
      type: 'bar',
      format: 'count',
      categories: ['史诗', '需求', '任务', '缺陷', '子任务'],
      series: [{ name: '未完成件数', values: [5, 20, 30, 8, 2] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '有 6 件工作项拖过期限仍未完成，调度要介入', count: 6 },
    { level: 'warning', text: '有 3 件未完成的活没派负责人，没人接手就一直挂着', count: 3 },
    { level: 'info', text: '有 3 个迭代还没启动，启动前工作项只排不办', count: null },
    { level: 'info', text: '有 2 个项目已归档，其工作项不再更新', count: null },
  ],
  links: [{ key: 'projects', label: '项目台账', path: '/pm/v1/project/page', permission: 'sys:pm:project:list' }],
  notes: ['口径：在建项目 = status ACTIVE；活跃迭代 = ACTIVE；未完成 = 状态分类非 DONE（各项目自定义状态按分类归并）；逾期 = 截止日早于今天且未完成'],
};

/**
 * 9 个项目：状态两态、类型两态、可见性三态全覆盖。第 8、9 个归档。
 * 6 行/页 → 2 页。
 */
const PROJECTS = [
  { id: 8001, key: 'DEMO', name: '示例项目', projectType: 'SCRUM', visibility: 'TEAM', description: '内置示例项目', status: 'ACTIVE', createTime: '2026-01-15T09:00:00' },
  { id: 8002, key: 'MALL', name: '商城小程序', projectType: 'SCRUM', visibility: 'TEAM', description: '商城二期的工程节奏', status: 'ACTIVE', createTime: '2026-02-01T10:00:00' },
  { id: 8003, key: 'KANBAN-OPS', name: '运维工单看板', projectType: 'KANBAN', visibility: 'TEAM', description: '值班工单流转', status: 'ACTIVE', createTime: '2026-02-20T11:00:00' },
  { id: 8004, key: 'TWIN', name: '三维孪生城', projectType: 'SCRUM', visibility: 'PUBLIC', description: '3D 城联机与告警', status: 'ACTIVE', createTime: '2026-03-05T14:00:00' },
  { id: 8005, key: 'PAY', name: '支付对账改造', projectType: 'KANBAN', visibility: 'PRIVATE', description: '对账文件链路', status: 'ACTIVE', createTime: '2026-03-22T09:30:00' },
  { id: 8006, key: 'CRM-UP', name: '客户中台升级', projectType: 'SCRUM', visibility: 'TEAM', description: '赢单口径统一', status: 'ACTIVE', createTime: '2026-04-10T15:00:00' },
  { id: 8007, key: 'IOT', name: '设备哨探接入', projectType: 'KANBAN', visibility: 'TEAM', description: '设备心跳与指令', status: 'ACTIVE', createTime: '2026-05-06T08:45:00' },
  { id: 8008, key: 'OA-OLD', name: '旧 OA 迁移', projectType: 'SCRUM', visibility: 'TEAM', description: '已迁完，留档', status: 'ARCHIVED', createTime: '2025-06-01T09:00:00' },
  { id: 8009, key: 'MES-V1', name: '一期产线系统', projectType: 'SCRUM', visibility: 'TEAM', description: '一期交付后归档', status: 'ARCHIVED', createTime: '2025-03-15T10:00:00' },
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

/** 记录列表真实发出的查询参数：关键字必须叫 keyword，状态叫 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/pm/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   营造坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/pm/v1/project/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/pm/v1/project/page**', async (route) => {
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
  /* 与后端同口径：keyword 按编码或名称模糊匹配，status 精确匹配（`ProjectQueryRequest`） */
  const matched = PROJECTS.filter((row) => {
    const hitStatus = !status || row.status === status;
    const hitKeyword = !keyword || row.name.includes(keyword) || row.key.includes(keyword.toUpperCase());
    return hitStatus && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是项目台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingzao"]');
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
check('营造坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 yingzao', mounted?.id === 'yingzao', mounted?.id);
check('大屏规格（六列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是营造坊 · 项目台账', String(mounted?.title).includes('项目台账'), mounted?.title);
check('入口标签来自概览 links（项目台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '项目台账', mounted?.tabs);

/* ---- 2. 概览走 /pm/overview ---- */
check('概览请求打到 /pm/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/pm/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（项目/迭代/未完成/逾期）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（工作项类型分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警（逾期 + 无主 + 未启动迭代 + 归档）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yingzao"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在建项目 7 个、灰灯（只报数不喊事）', kpiCards[0]?.value === '7' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('活跃迭代的提示语写出节奏（未启动 3 · 已关闭 15）',
  String(kpiCards[1]?.label).includes('未启动 3 · 已关闭 15'), kpiCards[1]?.label);
check('未完成 60 件亮灰灯（活没做完是常态，语气留给逾期牌）',
  kpiCards[2]?.value === '60' && kpiCards[2]?.tone === 'flat', kpiCards[2]);
check('未完成牌拆待办与进行中两段（待办 35 · 进行中 25）',
  String(kpiCards[2]?.label).includes('待办 35 · 进行中 25'), kpiCards[2]?.label);
/* 关键断言：逾期未完成是四张牌里唯一带重语气的——有活拖期红灯，不是黄灯 */
check('逾期 6 件亮红灯', kpiCards[3]?.value === '6' && kpiCards[3]?.tone === 'bad', kpiCards[3]);

/* ---- 3. 类型分布柱图：五种类型、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yingzao"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  /* 柱图的标签/数值都在行内（.bwsp-barlabel / .bwsp-barval）；
     .bwsp-barlabels 是折线图的横轴，柱图里没有 */
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  const values = [...(scope?.querySelectorAll('.bwsp-barval') ?? [])].map((span) => span.textContent);
  return {
    rows,
    labels,
    values,
    lineCount: scope?.querySelectorAll('svg.bwsp-line').length ?? 0,
  };
});
check('类型分布画成柱图（不是折线）', barChart.rows.length === 5 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是史诗/需求/任务/缺陷/子任务（服务端定的标签，屏风不译码）',
  barChart.labels.join(',') === '史诗,需求,任务,缺陷,子任务', barChart.labels);
check('柱值按计数排版（5/20/30/8/2，最大值是任务）',
  barChart.values.join(',') === '5,20,30,8,2', barChart.values);

/* ---- 4. 有搜索框、也有状态筛选，关键字参数是 keyword（后端有合并关键字） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yingzao"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    statusHidden: statusEl ? statusEl.hidden : null,
    statusValues: statusEl ? [...statusEl.options].map((option) => option.value) : [],
  };
});
check('有搜索框（后端 keyword 同时模糊匹配编码与名称）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（项目编码 / 名称）',
  String(filters?.placeholder).includes('项目编码'), filters?.placeholder);
check('有状态筛选（分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项是在建 / 已归档两态',
  filters?.statusValues.join(',') === ',ACTIVE,ARCHIVED', filters?.statusValues);

/* ---- 5. 首屏列表：9 个分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 9 个 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yingzao"] .bwsp-table tbody tr')].map((tr) => ({
    key: tr.children[0]?.textContent ?? '',
    name: tr.children[1]?.textContent ?? '',
    projectType: tr.children[2]?.textContent ?? '',
    visibility: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    leadCell: tr.children[5]?.textContent ?? '',
  })),
);
let rows = await readRows();
check('项目编码原样显示', rows[0]?.key === 'DEMO', rows[0]?.key);
check('项目名原样显示', rows[0]?.name === '示例项目', rows[0]?.name);
check('类型列按列级字典译成中文（SCRUM → Scrum）', rows[0]?.projectType === 'Scrum', rows[0]?.projectType);
check('第三行类型是看板（KANBAN → 看板，不蹭页面级状态字典）',
  rows[2]?.projectType === '看板', rows[2]?.projectType);
check('可见性列按列级字典译成中文（TEAM → 团队）', rows[0]?.visibility === '团队', rows[0]?.visibility);
check('ACTIVE 译成"在建"并打绿灯',
  rows[0]?.status === '在建' && rows[0]?.statusTone === 'good', rows[0]);
check('负责人列不摆（leadUserId 是用户 ID，屏风拿不到用户名）',
  !rows[0]?.leadCell.includes('1') || rows[0]?.leadCell.length <= 20, rows[0]?.leadCell);
await page.screenshot({ path: `${OUT}/60-yingzao-list.png` });

/* ---- 6. 先走到屏风跟前再进：营造司在城南侧（z=+19.67，将作的对面），
   出生点镜头朝北看不见它——将作坊冒烟可以原地点，这里必须先走。

   屏风的世界位姿直接从 CSS3D 变换矩阵里解：matrix3d 是列主序，
   第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴（即屏风正面法线，
   与 `districtScreenPose` 的 front 同口径）。站到法线外侧 4.5 米，
   相机 yaw = atan2(nx, nz)（view dir = −(sin yaw, cos yaw)）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingzao"]');
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
  const el = document.querySelector('[data-screen-id="yingzao"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="yingzao"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="yingzao"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'yingzao', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/61b-yingzao-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=商城 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('商城');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
const lastSearch = queried.filter((item) => item.keyword === '商城').pop();
check('关键字按 keyword=商城 发给服务端（合并关键字，不是单字段）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 1 个（商城小程序）',
  probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('命中的是名字里真有"商城"的项目',
  rows[0]?.name === '商城小程序', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=ARCHIVED，只剩两个归档 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('ARCHIVED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
const lastStatus = queried.filter((item) => item.status === 'ARCHIVED').pop();
check('状态按 status=ARCHIVED 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('归档只剩 2 个', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('两个归档是旧 OA 迁移与一期产线系统',
  rows.map((row) => row.name).join('|') === '旧 OA 迁移|一期产线系统', rows.map((row) => row.name));
check('归档给灰灯（沉淀不是事故，不亮红灯）',
  rows.every((row) => row.status === '已归档' && row.statusTone === 'flat'), rows);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 3 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
check('第 2 页剩 3 个', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是设备哨探接入（page 参数真的生效了）',
  rows[0]?.name === '设备哨探接入', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（ProjectVO 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 7 个字段用列表行数据（含描述 / 可见性）',
  probe?.detail.fields === 7 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/62-yingzao-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingzao'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingzao"]');
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
await page.route('**/pm/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/pm/v1/project/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingzao"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="yingzao"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('yingzao');
  const empty = document.querySelector('[data-screen-id="yingzao"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="yingzao"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/63-yingzao-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
