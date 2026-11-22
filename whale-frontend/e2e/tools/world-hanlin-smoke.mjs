// 翰林院空间台账（S6 / T6.31）冒烟：内城十二坊的第十三块屏风页。
//
// 与前几坊的分工：藏书殿验"文件本身存没存好"、通政殿验"消息送没送到"，
// 这一条验**写出来的内容走到了哪一步**——草稿、发布、审批、归档、
// 回收站这条流水线。计数牌是本坊的主角（四张全是计数，没有比率）。
//
// 九件事：
//   ① 概览四张牌走 /doc/overview，全部计数格式，待审批牌带语气；
//   ② 文稿状态分布画成**柱图不是折线**：翰林的账是存量不是流量，
//      四种状态分桶在服务端定死，屏风不译码；
//   ③ 空间台账**有搜索框**（keyword 合并关键字：名称与描述一起模糊匹配），
//      也有可见性筛选（PUBLIC/MEMBER）；
//   ④ 可见性列用**列级字典**（PUBLIC/MEMBER），页面级字典留给空间状态
//      （数字 1/0）——一个字符串一个数字，两套码空间同屏，只有列级覆盖分得开；
//   ⑤ 空间状态数字键查表：取值时 String(value) 后再查，'1' 正常绿灯 / '0' 归档灰灯；
//   ⑥ 列表行即详情（不配 detailPath）：DocSpaceVO 平铺，行内字段够详情用；
//   ⑦ 页面按空间树形组织：坊里只摆空间台账，不硬凑跨空间页面大表；
//   ⑧ 建时间走 date 口径（YYYY-MM-DD）；
//   ⑨ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十九块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="hanlin"] ';

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
/* 与 DocOverviewProvider 的实际下发一致：四张 KPI（全是计数）、
   一张四状态柱图、两条告警、一个空间台账入口。 */
const OVERVIEW = {
  service: 'doc',
  nodeKey: 'hanlin-academy',
  title: '翰林',
  subtitle: '文档知识 · 文书四本账',
  generatedAt: '2026-11-22T08:30:00+08:00',
  state: 'live',
  kpis: [
    { key: 'spaces', label: '文档空间', value: 8, format: 'count', tone: 'flat', hint: '归档 2 处' },
    { key: 'pages', label: '文稿页面', value: 30, format: 'count', tone: 'flat', hint: '已发布 14 · 草稿 9' },
    { key: 'pending', label: '待发布审批', value: 3, format: 'count', tone: 'warn', hint: '发布审批积压，要人去核' },
    { key: 'trash', label: '回收站', value: 2, format: 'count', tone: 'flat', hint: '2 棵删除子树待处置' },
  ],
  charts: [
    {
      key: 'page-status',
      title: '文稿状态分布',
      type: 'bar',
      format: 'count',
      categories: ['草稿', '已发布', '待审批', '已归档'],
      series: [{ name: '页面数', values: [9, 14, 3, 4] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 3 篇页面卡在发布审批，作者改不了也撤不回，需要人去核', count: null },
    { level: 'info', text: '回收站里有 2 棵删除子树，恢复或彻底清除前一直占着位置', count: null },
  ],
  links: [{ key: 'spaces', label: '空间台账', path: '/doc/v1/space/page', permission: 'sys:doc:space:list' }],
  notes: ['口径：正式页面 = 未入回收站（含目录节点）；回收站按棵数（整棵删除子树算一棵）；待审批 = 卡在发布审批的页面'],
};

/**
 * 8 个空间：可见性两态、状态两态全覆盖。第 7、8 个归档。
 * 6 行/页 → 2 页。
 */
const SPACES = [
  { id: 20001, spaceKey: 'BJ-01', name: '综合管理空间', visibility: 'PUBLIC', status: 1, description: '全员公告与制度', createTime: '2026-03-12 10:20:00', updateTime: '2026-09-01 09:00:00' },
  { id: 20002, spaceKey: 'BJ-02', name: '研发文档中心', visibility: 'MEMBER', status: 1, description: '设计稿与技术方案', createTime: '2026-03-15 14:00:00', updateTime: '2026-09-02 09:00:00' },
  { id: 20003, spaceKey: 'BJ-03', name: '产品需求库', visibility: 'PUBLIC', status: 1, description: '需求与评审记录', createTime: '2026-04-01 11:30:00', updateTime: '2026-09-03 09:00:00' },
  { id: 20004, spaceKey: 'BJ-04', name: '会议纪要归档', visibility: 'MEMBER', status: 1, description: '例会与专题会记录', createTime: '2026-04-10 16:00:00', updateTime: '2026-09-04 09:00:00' },
  { id: 20005, spaceKey: 'BJ-05', name: '制度文件库', visibility: 'PUBLIC', status: 1, description: '规章与流程', createTime: '2026-05-20 09:10:00', updateTime: '2026-09-05 09:00:00' },
  { id: 20006, spaceKey: 'BJ-06', name: '财务凭证附件', visibility: 'MEMBER', status: 0, description: '凭证影像留存', createTime: '2026-01-05 10:00:00', updateTime: '2026-07-01 09:00:00' },
  { id: 20007, spaceKey: 'BJ-07', name: '培训讲义库', visibility: 'PUBLIC', status: 1, description: '课程讲义与习题', createTime: '2026-06-01 08:45:00', updateTime: '2026-09-06 09:00:00' },
  { id: 20008, spaceKey: 'BJ-08', name: '旧版规范库', visibility: 'MEMBER', status: 0, description: '已废止的旧规范', createTime: '2025-12-01 10:00:00', updateTime: '2026-06-01 09:00:00' },
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

/** 记录列表真实发出的查询参数：关键字必须叫 keyword，可见性叫 visibility */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/doc/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   翰林坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/doc/v1/space/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/doc/v1/space/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const visibility = url.searchParams.get('visibility');
  queried.push({
    current,
    size,
    keyword,
    visibility,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：keyword 按名称或描述模糊匹配，visibility 精确匹配（`DocSpaceQueryRequest`） */
  const matched = SPACES.filter((row) => {
    const hitVisibility = !visibility || row.visibility === visibility;
    const hitKeyword = !keyword || row.name.includes(keyword) || row.description.includes(keyword);
    return hitVisibility && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是空间台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hanlin"]');
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
check('翰林院屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 hanlin', mounted?.id === 'hanlin', mounted?.id);
check('大屏规格（六列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是翰林院 · 空间台账', String(mounted?.title).includes('空间台账'), mounted?.title);
check('入口标签来自概览 links（空间台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '空间台账', mounted?.tabs);

/* ---- 2. 概览走 /doc/overview ---- */
check('概览请求打到 /doc/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/doc/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（空间/页面/待审批/回收站）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（文稿状态分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（审批积压 + 回收站积压）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="hanlin"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('空间 8 处、灰灯（只报数不喊事）', kpiCards[0]?.value === '8' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('空间牌写出归档数（归档 2 处）', String(kpiCards[0]?.label).includes('归档 2 处'), kpiCards[0]?.label);
check('页面 30 篇、灰灯', kpiCards[1]?.value === '30' && kpiCards[1]?.tone === 'flat', kpiCards[1]);
check('页面牌写出发布/草稿两段（已发布 14 · 草稿 9）',
  String(kpiCards[1]?.label).includes('已发布 14'), kpiCards[1]?.label);
check('待审批 3 篇、黄灯（有稿压着要人去核）', kpiCards[2]?.value === '3' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('回收站 2 棵、灰灯（按棵数不按页数）', kpiCards[3]?.value === '2' && kpiCards[3]?.tone === 'flat', kpiCards[3]);

/* ---- 3. 状态分布柱图：四段分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="hanlin"]');
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
check('状态分布画成柱图（不是折线）', barChart.rows.length === 4 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是服务端定的四段状态标签（屏风不译码）',
  barChart.labels.length === 4 && barChart.labels[0] === '草稿', barChart.labels);
check('柱值按计数排版（9/14/3/4）',
  barChart.values.join(',') === '9,14,3,4', barChart.values);

/* ---- 4. 有搜索框、也有可见性筛选，关键字参数是 keyword（后端有合并关键字） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="hanlin"]');
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
check('有搜索框（后端 keyword 同时模糊匹配名称与描述）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（空间名 / 描述）',
  String(filters?.placeholder).includes('空间名'), filters?.placeholder);
check('有可见性筛选（分页接口真收 visibility）', filters?.statusHidden === false, filters);
check('可见性筛选项是公开 / 成员可见两态',
  filters?.statusValues.join(',') === ',PUBLIC,MEMBER', filters?.statusValues);

/* ---- 5. 首屏列表：8 处分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 处 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / visibility 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.visibility === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="hanlin"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    key: tr.children[1]?.textContent ?? '',
    visibility: tr.children[2]?.textContent ?? '',
    visibilityTone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    description: tr.children[4]?.textContent ?? '',
    created: tr.children[5]?.textContent ?? '',
  })),
);
let rows = await readRows();
check('空间名原样显示', rows[0]?.name === '综合管理空间', rows[0]?.name);
check('编码原样显示', rows[0]?.key === 'BJ-01', rows[0]?.key);
check('可见性按列级字典译成中文（PUBLIC → 公开）',
  rows[0]?.visibility === '公开' && rows[0]?.visibilityTone === 'flat', rows[0]);
check('MEMBER 译成"成员可见"（不蹭状态字典）',
  rows[1]?.visibility === '成员可见', rows[1]);
check('状态数字键查表：1 → 正常绿灯',
  rows[0]?.status === '正常' && rows[0]?.statusTone === 'good', rows[0]);
check('归档空间：0 → 已归档灰灯（旧账不是事故）',
  rows[5]?.status === '已归档' && rows[5]?.statusTone === 'flat', rows[5]);
check('创建时间按 date 口径（YYYY-MM-DD）', rows[0]?.created === '2026-03-12', rows[0]?.created);
await page.screenshot({ path: `${OUT}/90-hanlin-list.png` });

/* ---- 6. 先走到屏风跟前再进：翰林院在城北侧（z=-73.41），出生点离得远，
   必须先走。屏风的世界位姿直接从 CSS3D 变换矩阵里解：matrix3d 是列主序，
   第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴（即屏风正面法线，
   与 `districtScreenPose` 的 front 同口径）。站到法线外侧 4.5 米，
   相机 yaw = atan2(nx, nz)（view dir = −(sin yaw, cos yaw)）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hanlin"]');
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
   click 监听（与前几坊同一套），直接派发 click 事件走同一逻辑。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hanlin"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="hanlin"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="hanlin"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'hanlin', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/91b-hanlin-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=财务 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('财务');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
const lastSearch = queried.filter((item) => item.keyword === '财务').pop();
check('关键字按 keyword=财务 发给服务端（合并关键字，不是单字段）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 1 处（财务凭证附件）',
  probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('命中的是名字里真有"财务"的空间',
  rows[0]?.name === '财务凭证附件', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 可见性筛选：发 visibility=MEMBER，只剩四处成员可见 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('MEMBER');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
const lastStatus = queried.filter((item) => item.visibility === 'MEMBER').pop();
check('可见性按 visibility=MEMBER 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('成员可见剩 4 处', probe?.list.total === 4 && probe?.list.rows === 4, probe?.list);
rows = await readRows();
check('命中的是成员可见的空间（含归档的财务凭证附件）',
  rows.every((row) => row.visibility === '成员可见'), rows.map((row) => row.name));
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 2 处 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
check('第 2 页剩 2 处', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是培训讲义库（page 参数真的生效了）',
  rows[0]?.name === '培训讲义库', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（DocSpaceVO 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 7 个字段用列表行数据（含创建/更新时间）',
  probe?.detail.fields === 7 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/92-hanlin-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('hanlin'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hanlin"]');
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
await page.route('**/doc/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/doc/v1/space/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="hanlin"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="hanlin"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('hanlin');
  const empty = document.querySelector('[data-screen-id="hanlin"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="hanlin"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/93-hanlin-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
