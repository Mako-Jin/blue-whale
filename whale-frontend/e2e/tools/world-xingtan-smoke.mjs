// 杏坛坊课程目录（S6 / T6.16）冒烟：内城十二坊的第五块屏风页。
//
// 与工部/吏部/礼部/兵部的分工：工部验"车间在不在跑"、吏部验"人有没有到岗"、
// 礼部验"客户"、兵部验"哨探"，这一条验**教化**——有多少该读书的人、
// 几个人真的开始读了、开始读的人里几个读完、考试过了几个。
//
// 七件事：
//   ① 概览四张牌走 /lms/overview，三个比率必须是**比率**（0.63 → 63.0%），
//      且提示语要写出分子分母——覆盖对在职人数、完成对已学人数，两个分母不同；
//   ② 课程目录**有搜索框也有状态筛选**（`CourseController#page` 真收 keyword / status），
//      与吏部坊"无 keyword 就不给框"、兵部坊"接口不收 status 就不给下拉"形成对照；
//   ③ 状态是 DRAFT / PUBLISHED / ARCHIVED 三态：草稿与归档都给灰灯，
//      只有"已发布"亮绿灯——那才意味着现在真的有人在学；
//   ④ 列表行即详情（不配 detailPath）：`CourseDetailView` 把课程头包在 `course` 里、
//      章节与课件是两层嵌套数组，而屏风详情只认平铺键值对；硬配上去会得到一张空详情页；
//   ⑤ 趋势画成折线且**两条序列**（学习人数 / 完成人数）：同量纲才能放一张图，
//      两线的间距就是"开始了但没学完"的人；窗口只取最近 14 个统计日，30 点会糊成一条；
//   ⑥ 折线必须横向铺满（viewBox 100×34 配 830×48，默认等比缩放只占中间 17%）；
//   ⑦ 接口全挂时不白屏：标 stale + 给一句人话。
//
// 数据来源：page.route 拦 /lms/overview 与 /lms/v1/course/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十五块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="xingtan"] ';

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
/* 与 LmsOverviewProvider 的实际下发一致：四张 KPI（三个比率）、
   一张 14 个统计日的双序列折线、三条告警、一个课程目录入口 */
const DAYS = ['11-08', '11-09', '11-10', '11-11', '11-12', '11-13', '11-14', '11-15',
  '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'];
const LEARNERS = [520, 528, 531, 540, 548, 556, 562, 570, 578, 590, 601, 612, 621, 630];
const COMPLETED = [380, 385, 391, 398, 402, 410, 415, 421, 430, 441, 450, 458, 466, 473];

const OVERVIEW = {
  service: 'lms',
  nodeKey: 'xingtan-hall',
  title: '杏坛',
  subtitle: '学习教化 · 近 14 个统计日',
  generatedAt: '2026-11-21T22:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'learners', label: '在册学员', value: 1001, format: 'count', tone: 'flat', hint: '已开始学 630 人' },
    { key: 'coverage', label: '学习覆盖', value: 0.63, format: 'percent', tone: 'warn', hint: '已学 630/1001' },
    { key: 'completion', label: '学习完成', value: 0.75, format: 'percent', tone: 'warn', hint: '学完 473/630' },
    { key: 'pass-rate', label: '考试通过', value: 0.9, format: 'percent', tone: 'good', hint: '通过 90/100' },
  ],
  charts: [
    {
      key: 'learn-trend',
      title: '学习与完成人数走势',
      type: 'line',
      format: 'count',
      categories: DAYS,
      series: [
        { name: '学习人数', values: LEARNERS },
        { name: '完成人数', values: COMPLETED },
      ],
    },
  ],
  alerts: [
    { level: 'warning', text: '学习覆盖率仅 63%，多数人还没开始学', count: null },
    { level: 'warning', text: '已开始学的人里只有 75% 学完，课程可能太长或没人催', count: null },
    { level: 'info', text: '今日还没有看板快照，这一屏是实时算的（不会写库）', count: null },
  ],
  links: [{ key: 'courses', label: '课程目录', path: '/lms/v1/course/page', permission: 'sys:lms:course:list' }],
  notes: ['口径：覆盖 = 已学 ÷ 在职（630/1001），完成 = 学完 ÷ 已学（473/630），通过 = 通过 ÷ 参考（90/100）；学分累计 120 分，18 人获得'],
};

/**
 * 8 门课：三种状态都覆盖，且第 3、5 门是草稿、第 6 门已归档。
 * 5 行/页 → 2 页（双序列折线多一行图例，5 行才不会把最后一行压到分页栏下）
 */
const COURSES = [
  { id: 7001, courseCode: 'SEC-BASIC', title: '信息安全基础', category: '安全', lecturer: '王教习', description: '口令、钓鱼与终端安全', status: 'PUBLISHED', createTime: '2026-03-01T09:12:00' },
  { id: 7002, courseCode: 'ONBOARD-01', title: '新员工入职引导', category: '制度', lecturer: '李司业', description: '组织架构、考勤与报销入口', status: 'PUBLISHED', createTime: '2026-03-08T10:20:00' },
  { id: 7003, courseCode: 'FIN-EXP-02', title: '财务报销规范', category: '财务', lecturer: '赵讲师', description: '发票要求与审批链路', status: 'DRAFT', createTime: '2026-09-19T14:40:00' },
  { id: 7004, courseCode: 'MES-LEAD-03', title: '班组长现场管理', category: '生产', lecturer: '周工', description: '班前会、异常上报与交接班', status: 'PUBLISHED', createTime: '2026-05-11T08:00:00' },
  { id: 7005, courseCode: 'CRM-NEG-04', title: '客户谈判技巧', category: '经营', lecturer: '陈博士', description: '报价、让步与异议处理', status: 'DRAFT', createTime: '2026-10-30T16:05:00' },
  { id: 7006, courseCode: 'IOT-SAFE-05', title: '设备安全操作规程', category: '生产', lecturer: '孙工', description: '旧版规程（已下架）', status: 'ARCHIVED', createTime: '2025-08-02T09:00:00' },
  { id: 7007, courseCode: 'ENG-ADV-06', title: '商务英语进阶', category: '通识', lecturer: 'Anna', description: '邮件与会议表达', status: 'PUBLISHED', createTime: '2026-06-18T13:30:00' },
  { id: 7008, courseCode: 'LAW-PRIV-07', title: '数据合规与隐私', category: '制度', lecturer: '刘法务', description: '个人信息处理与留存期限', status: 'PUBLISHED', createTime: '2026-07-22T11:15:00' },
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

await page.route('**/lms/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   杏坛坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/lms/v1/course/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/lms/v1/course/page**', async (route) => {
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
  /* 与后端同口径：keyword 按课程名 / 编码模糊匹配，status 精确匹配（`CourseQuery`） */
  const matched = COURSES.filter((row) => {
    const hitStatus = !status || row.status === status;
    const hitKeyword = !keyword
      || row.title.includes(keyword)
      || (row.courseCode ?? '').includes(keyword);
    return hitStatus && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是课程目录页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xingtan"]');
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
check('杏坛坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 xingtan', mounted?.id === 'xingtan', mounted?.id);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是杏坛坊 · 课程目录', String(mounted?.title).includes('课程目录'), mounted?.title);
check('入口标签来自概览 links（课程目录）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '课程目录', mounted?.tabs);

/* ---- 2. 概览走 /lms/overview（不是 2D 的 /lms/analytics/overview） ---- */
check('概览请求打到 /lms/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/lms/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在册/覆盖/完成/通过）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（学习与完成走势）', probe?.overview.charts === 1, probe?.overview.charts);
check('三条告警（覆盖 + 完成 + 快照未生成）', probe?.overview.alerts === 3, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="xingtan"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
/* 计数走格式化层的既有口径：超过三位加千分位（与契约 SDK 的 formatValue 同口径） */
check('在册学员按计数排版（1001 → 1,001）', kpiCards[0]?.value === '1,001', kpiCards[0]);
/* 关键断言：契约给的是比率 0.63。若 provider 把 63 直接发过来，这一屏会显示 6300.0%。 */
check('学习覆盖按比率渲染（0.63 → 63.0%，不是 6300%）', kpiCards[1]?.value === '63.0%', kpiCards[1]);
check('覆盖率未达 80% 亮黄灯（不是绿灯）', kpiCards[1]?.tone === 'warn', kpiCards[1]);
check('覆盖率的提示语写出分子分母（已学 630 / 在职 1001）',
  String(kpiCards[1]?.label).includes('已学 630/1001'), kpiCards[1]?.label);
check('完成率 0.75 → 75.0% 且亮黄灯', kpiCards[2]?.value === '75.0%' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('完成率的分母是"已学"而不是"在职"（学完 473 / 已学 630）',
  String(kpiCards[2]?.label).includes('学完 473/630'), kpiCards[2]?.label);
check('考试通过率 0.9 → 90.0% 且亮绿灯', kpiCards[3]?.value === '90.0%' && kpiCards[3]?.tone === 'good', kpiCards[3]);

/* ---- 3. 趋势折线：两条序列、14 个点、铺满整宽 ---- */
const trend = await page.evaluate(() => {
  const wrap = document.querySelector('[data-screen-id="xingtan"] .bwsp-linewrap');
  const svg = wrap?.querySelector('svg.bwsp-line');
  const lines = [...(svg?.querySelectorAll('polyline') ?? [])];
  const labels = [...(wrap?.querySelectorAll('.bwsp-barlabels span') ?? [])].map((span) => span.textContent);
  const points = lines.map((line) => (line.getAttribute('points') ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((pair) => pair.split(',').map(Number)));
  return {
    labels,
    count: lines.length,
    points,
    viewBox: svg?.getAttribute('viewBox') ?? '',
    aspect: svg?.getAttribute('preserveAspectRatio') ?? '',
    strokeScaling: lines[0]?.getAttribute('vector-effect') ?? '',
    svgWidth: svg ? Math.round(svg.getBoundingClientRect().width) : 0,
    wrapWidth: wrap ? Math.round(wrap.getBoundingClientRect().width) : 0,
    rows: document.querySelectorAll('[data-screen-id="xingtan"] .bwsp-barrow').length,
  };
});
check('趋势画成折线（不是横条）', trend.count === 2 && trend.rows === 0, { count: trend.count, rows: trend.rows });
check('横轴是 14 个统计日（11-08 … 11-21）',
  trend.labels.join(',') === DAYS.join(','), trend.labels);
check('两条序列各 14 个点（学习人数 / 完成人数）',
  trend.points.length === 2 && trend.points.every((line) => line.length === 14),
  trend.points.map((line) => line.length));
/* 关键断言：两条线的 x 都从 0 到 100，说明线是铺满 viewBox 的（不是挤在中间） */
check('两条线都从 x=0 铺到 x=100',
  trend.points.every((line) => line[0][0] === 0 && line[13][0] === 100),
  trend.points.map((line) => [line[0][0], line[13][0]]));
check('viewBox 拉伸（preserveAspectRatio=none）', trend.aspect === 'none', trend.aspect);
check('线宽不随拉伸变粗（non-scaling-stroke）', trend.strokeScaling === 'non-scaling-stroke', trend.strokeScaling);
check('折线横向铺满容器（不是居中一小段）',
  trend.wrapWidth > 0 && Math.abs(trend.svgWidth - trend.wrapWidth) <= 2,
  { svg: trend.svgWidth, wrap: trend.wrapWidth });
const firstLineY = trend.points[0]?.map((pair) => pair[1]);
/* 完成人数是学习人数的子集：同一量程下第二条线必须整体在第一条线下方（y 更大） */
check('完成人数线整体在不学习人数线下方（分母关系没写反）',
  Array.isArray(firstLineY)
    && trend.points[1].every((pair, index) => pair[1] >= firstLineY[index] - 0.01),
  { learners: firstLineY, completed: trend.points[1]?.map((pair) => pair[1]) });

/* ---- 4. 有搜索框、也有状态筛选（课程接口两个都收） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="xingtan"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    statusHidden: statusEl ? statusEl.hidden : null,
    statusValues: statusEl ? [...statusEl.options].map((option) => option.value) : [],
    statusLabels: statusEl ? [...statusEl.options].map((option) => option.textContent) : [],
  };
});
check('有搜索框（后端关键字参数是 keyword）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（课程名 / 编码）',
  String(filters?.placeholder).includes('课程名'), filters?.placeholder);
check('有状态筛选（课程分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项是 DRAFT / PUBLISHED / ARCHIVED 三态',
  filters?.statusValues.join(',') === ',DRAFT,PUBLISHED,ARCHIVED', filters?.statusValues);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 5 行 / 共 8 条 / 2 页', probe?.list.rows === 5 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 5
    && queried[0]?.keyword === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="xingtan"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    category: tr.children[1]?.textContent ?? '',
    lecturer: tr.children[2]?.textContent ?? '',
    created: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
let rows = await readRows();
check('课程名原样显示', rows[0]?.title === '信息安全基础', rows[0]?.title);
check('分类原样显示（自由文本，不做下拉）', rows[0]?.category === '安全', rows[0]?.category);
check('讲师原样显示', rows[0]?.lecturer === '王教习', rows[0]?.lecturer);
check('创建时间只显示月日时分（03-01 09:12）', rows[0]?.created === '03-01 09:12', rows[0]?.created);
check('PUBLISHED 译成"已发布"并打绿灯',
  rows[0]?.status === '已发布' && rows[0]?.statusTone === 'good', rows[0]);
check('DRAFT 译成"草稿"并打灰灯（还没打算开课，不是问题）',
  rows[2]?.status === '草稿' && rows[2]?.statusTone === 'flat', rows[2]);
await page.screenshot({ path: `${OUT}/50-xingtan-list.png` });

/* ---- 6. 进入屏风（贴脸态）：筛选、翻页、点行都要"在屏风里"才生效 ----
   `screenpage.ts` 里 statusSel / pager / row 的事件都带 `if (!engaged()) return;`，
   所以在 3D 里隔空点搜索框是没反应的——必须先点屏风进去。 */
await page.locator('[data-screen-id="xingtan"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="xingtan"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="xingtan"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'xingtan', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 贴脸态才是真正要读的那一屏（3D 里那块屏只有 1/6 屏宽）：版式问题只在这里看得出来 */
await page.screenshot({ path: `${OUT}/50b-xingtan-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=安全 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('安全');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
const lastSearch = queried.filter((item) => item.keyword === '安全').pop();
check('关键字按 keyword=安全 发给服务端', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 2 条（信息安全基础 + 设备安全操作规程）',
  probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('命中的是名字里真有"安全"的两门课',
  rows.map((row) => row.title).join('|') === '信息安全基础|设备安全操作规程', rows.map((row) => row.title));
check('已归档的那门课仍按"已归档"译（灰灯，不是空白）',
  rows[1]?.status === '已归档' && rows[1]?.statusTone === 'flat', rows[1]);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=DRAFT，只剩两门草稿 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('DRAFT');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
const lastStatus = queried.filter((item) => item.status === 'DRAFT').pop();
check('状态按 status=DRAFT 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('草稿只剩 2 条', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('两门草稿是财务报销规范与客户谈判技巧',
  rows.map((row) => row.title).join('|') === '财务报销规范|客户谈判技巧', rows.map((row) => row.title));

/* ---- 9. 分页：清掉筛选后第 2 页 2 条 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
check('第 2 页剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是设备安全操作规程（page 参数真的生效了）',
  rows[0]?.title === '设备安全操作规程', rows[0]?.title);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（CourseDetailView 是嵌套结构，屏风详情只认平铺）',
  detailHits.length === 0, detailHits);
check('详情 7 个字段用列表行数据（含课程编码与简介）',
  probe?.detail.fields === 7 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/51-xingtan-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xingtan'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 5, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xingtan"]');
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
await page.route('**/lms/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/lms/v1/course/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="xingtan"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="xingtan"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('xingtan');
  const empty = document.querySelector('[data-screen-id="xingtan"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="xingtan"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/52-xingtan-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
