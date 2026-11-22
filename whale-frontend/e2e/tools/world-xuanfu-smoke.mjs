// 宣抚坊党员台账（S6 / T6.28）冒烟：内城十二坊的第十二块屏风页。
//
// 与前几坊的分工：杏坛验"人有没有在长本事"、吏部验"人有没有到岗"，
// 这一条验**组织生活正不正常**——党费收得齐不齐、三会一课有没有按期开展、
// 组织生活有多少人到场。比率牌是本坊的主角（三张比率 + 一张计数）。
//
// 九件事：
//   ① 概览四张牌走 /party/overview，三张比率牌全部 /100 进契约
//      （MetricSnapshot#rate 是百分数，直接塞会显示 9400.0%）；
//   ② 党员年龄结构画成**柱图不是折线**：党务的账是存量不是流量，
//      六段年龄分桶在服务端定死，屏风不译码；
//   ③ 党员台账**有搜索框**（keyword 合并关键字：编号与姓名一起模糊匹配），
//      也有状态筛选（ACTIVE/TRANSFERRED/SUSPENDED）；
//   ④ 政治面貌列用**列级字典**（FULL_MEMBER/PROBATIONARY），
//      页面级字典留给状态列——两套码空间同屏，只有列级覆盖分得开；
//   ⑤ 预备党员黄灯：不是事故，是"转正手续得有人办"的提醒；
//   ⑥ 列表行即详情（不配 detailPath）：MemberView 平铺，行内字段够详情用；
//   ⑦ 党龄列走 count 口径右对齐；
//   ⑧ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十八块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="xuanfu"] ';

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
/* 与 PartyOverviewProvider 的实际下发一致：四张 KPI（计数 + 三比率）、
   一张六段年龄柱图、五条告警、一个党员台账入口。
   比率全部除过 100：契约 PERCENT 吃 0.96 这种比率。 */
const OVERVIEW = {
  service: 'party',
  nodeKey: 'xuanfu-court',
  title: '宣抚',
  subtitle: '党务党建 · 党务看板',
  generatedAt: '2026-11-22T06:45:00+08:00',
  state: 'live',
  kpis: [
    { key: 'members', label: '在册党员', value: 42, format: 'count', tone: 'flat', hint: '待转正预备党员 3 人' },
    { key: 'fee-rate', label: '党费收缴', value: 0.965, format: 'percent', tone: 'good', hint: '实收 96500/100000' },
    { key: 'activity-hold', label: '三会一课', value: 0.88, format: 'percent', tone: 'warn', hint: '已开展 22/25' },
    { key: 'attendance', label: '组织生活参会', value: 0.76, format: 'percent', tone: 'warn', hint: '实到 380/500' },
  ],
  charts: [
    {
      key: 'age-structure',
      title: '党员年龄结构',
      type: 'bar',
      format: 'count',
      categories: ['30 岁以下党员', '30–39 岁党员', '40–49 岁党员', '50–59 岁党员', '60 岁及以上党员', '年龄未知党员'],
      series: [{ name: '党员数', values: [8, 12, 10, 7, 4, 1] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '三会一课开展率仅 88%，组织生活欠账', count: null },
    { level: 'warning', text: '有 2 个支部到了换届期，到期不换要出问题', count: null },
    { level: 'info', text: '有 3 名预备党员到了转正期，转正得有人办手续', count: null },
  ],
  links: [{ key: 'members', label: '党员台账', path: '/party/v1/member/page', permission: 'sys:party:member:list' }],
  notes: ['口径：收缴率 = 实收 ÷ 应收（96500/100000），开展率 = 已开展 ÷ 应开展（22/25），参会率 = 实到 ÷ 应到（380/500）'],
};

/**
 * 9 名党员：状态三态、面貌两态全覆盖。第 8、9 名转出。
 * 6 行/页 → 2 页。
 */
const MEMBERS = [
  { id: 10001, memberNo: 'DY-0001', memberName: '张三', branchName: '机关第一支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 12, employeeNo: 'E001', education: '本科', status: 'ACTIVE', phone: '13800000001' },
  { id: 10002, memberNo: 'DY-0002', memberName: '李四', branchName: '机关第一支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 8, employeeNo: 'E002', education: '硕士', status: 'ACTIVE', phone: '13800000002' },
  { id: 10003, memberNo: 'DY-0003', memberName: '王五', branchName: '生产支部', politicalStatus: 'PROBATIONARY', partyAgeYears: 1, employeeNo: 'E003', education: '大专', status: 'ACTIVE', phone: '13800000003' },
  { id: 10004, memberNo: 'DY-0004', memberName: '赵六', branchName: '生产支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 20, employeeNo: 'E004', education: '本科', status: 'ACTIVE', phone: '13800000004' },
  { id: 10005, memberNo: 'DY-0005', memberName: '钱七', branchName: '研发支部', politicalStatus: 'PROBATIONARY', partyAgeYears: 0, employeeNo: 'E005', education: '博士', status: 'ACTIVE', phone: '13800000005' },
  { id: 10006, memberNo: 'DY-0006', memberName: '孙八', branchName: '研发支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 5, employeeNo: 'E006', education: '本科', status: 'ACTIVE', phone: '13800000006' },
  { id: 10007, memberNo: 'DY-0007', memberName: '周九', branchName: '机关第二支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 30, employeeNo: 'E007', education: '中专', status: 'ACTIVE', phone: '13800000007' },
  { id: 10008, memberNo: 'DY-0008', memberName: '吴十', branchName: '机关第二支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 15, employeeNo: 'E008', education: '本科', status: 'TRANSFERRED', phone: '13800000008' },
  { id: 10009, memberNo: 'DY-0009', memberName: '郑一', branchName: '退休支部', politicalStatus: 'FULL_MEMBER', partyAgeYears: 42, employeeNo: 'E009', education: '大专', status: 'TRANSFERRED', phone: '13800000009' },
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

await page.route('**/party/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   宣抚坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/party/v1/member/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/party/v1/member/page**', async (route) => {
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
  /* 与后端同口径：keyword 按编号或姓名模糊匹配，status 精确匹配（`MemberQuery`） */
  const matched = MEMBERS.filter((row) => {
    const hitStatus = !status || row.status === status;
    const hitKeyword = !keyword || row.memberName.includes(keyword)
      || row.memberNo.includes(keyword.toUpperCase());
    return hitStatus && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是党员台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xuanfu"]');
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
check('宣抚坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 xuanfu', mounted?.id === 'xuanfu', mounted?.id);
check('大屏规格（六列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是宣抚坊 · 党员台账', String(mounted?.title).includes('党员台账'), mounted?.title);
check('入口标签来自概览 links（党员台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '党员台账', mounted?.tabs);

/* ---- 2. 概览走 /party/overview ---- */
check('概览请求打到 /party/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/party/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在册/收缴/三会一课/参会）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（党员年龄结构）', probe?.overview.charts === 1, probe?.overview.charts);
check('三条告警（开展欠账 + 换届到期 + 待转正）', probe?.overview.alerts === 3, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="xuanfu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在册 42 人、灰灯（只报数不喊事）', kpiCards[0]?.value === '42' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('在册牌写出待转正数（待转正预备党员 3 人）',
  String(kpiCards[0]?.label).includes('待转正预备党员 3 人'), kpiCards[0]?.label);
/* 关键断言：三张比率牌按 96.5% / 88.0% / 76.0% 显示——/100 做对了，不是 9400.0% */
check('党费收缴 96.5% 绿灯（≥95）', kpiCards[1]?.value === '96.5%' && kpiCards[1]?.tone === 'good', kpiCards[1]);
check('三会一课 88.0% 黄灯（70–90 之间）', kpiCards[2]?.value === '88.0%' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('组织生活参会 76.0% 黄灯（60–85 之间）', kpiCards[3]?.value === '76.0%' && kpiCards[3]?.tone === 'warn', kpiCards[3]);

/* ---- 3. 年龄结构柱图：六段分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="xuanfu"]');
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
check('年龄结构画成柱图（不是折线）', barChart.rows.length === 6 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是服务端定的六段分桶标签（屏风不译码）',
  barChart.labels.length === 6 && barChart.labels[0]?.includes('30'), barChart.labels);
check('柱值按计数排版（8/12/10/7/4/1）',
  barChart.values.join(',') === '8,12,10,7,4,1', barChart.values);

/* ---- 4. 有搜索框、也有状态筛选，关键字参数是 keyword（后端有合并关键字） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="xuanfu"]');
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
check('有搜索框（后端 keyword 同时模糊匹配编号与姓名）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（党员编号 / 姓名）',
  String(filters?.placeholder).includes('党员编号'), filters?.placeholder);
check('有状态筛选（分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项是在册 / 已转出 / 停籍三态',
  filters?.statusValues.join(',') === ',ACTIVE,TRANSFERRED,SUSPENDED', filters?.statusValues);

/* ---- 5. 首屏列表：9 人分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 9 人 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="xuanfu"] .bwsp-table tbody tr')].map((tr) => ({
    no: tr.children[0]?.textContent ?? '',
    name: tr.children[1]?.textContent ?? '',
    branch: tr.children[2]?.textContent ?? '',
    political: tr.children[3]?.textContent ?? '',
    politicalTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    age: tr.children[4]?.textContent ?? '',
    status: tr.children[5]?.textContent ?? '',
    statusTone: tr.children[5]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
let rows = await readRows();
check('党员编号原样显示', rows[0]?.no === 'DY-0001', rows[0]?.no);
check('姓名原样显示', rows[0]?.name === '张三', rows[0]?.name);
check('支部名原样显示（服务端带出，屏风不查用户表）', rows[0]?.branch === '机关第一支部', rows[0]?.branch);
check('政治面貌按列级字典译成中文（FULL_MEMBER → 正式党员）',
  rows[0]?.political === '正式党员' && rows[0]?.politicalTone === 'flat', rows[0]);
check('预备党员黄灯（不是事故，是转正要有人办的提醒）',
  rows[2]?.political === '预备党员' && rows[2]?.politicalTone === 'warn', rows[2]);
check('党龄按 count 口径显示', rows[0]?.age === '12', rows[0]?.age);
check('ACTIVE 译成"在册"并打绿灯',
  rows[0]?.status === '在册' && rows[0]?.statusTone === 'good', rows[0]);
await page.screenshot({ path: `${OUT}/80-xuanfu-list.png` });

/* ---- 6. 先走到屏风跟前再进：宣抚院在城南侧（z=+73.41，盟府的邻坊），
   出生点镜头朝北看不见它——必须先走。

   屏风的世界位姿直接从 CSS3D 变换矩阵里解：matrix3d 是列主序，
   第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴（即屏风正面法线，
   与 `districtScreenPose` 的 front 同口径）。站到法线外侧 4.5 米，
   相机 yaw = atan2(nx, nz)（view dir = −(sin yaw, cos yaw)）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xuanfu"]');
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
  const el = document.querySelector('[data-screen-id="xuanfu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="xuanfu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="xuanfu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'xuanfu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/81b-xuanfu-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=吴 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('吴');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
const lastSearch = queried.filter((item) => item.keyword === '吴').pop();
check('关键字按 keyword=吴 发给服务端（合并关键字，不是单字段）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 1 人（吴十）',
  probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('命中的是名字里真有"吴"的党员',
  rows[0]?.name === '吴十', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=TRANSFERRED，只剩两个转出 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('TRANSFERRED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
const lastStatus = queried.filter((item) => item.status === 'TRANSFERRED').pop();
check('状态按 status=TRANSFERRED 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('转出只剩 2 人', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('两个转出是吴十与郑一',
  rows.map((row) => row.name).join('|') === '吴十|郑一', rows.map((row) => row.name));
check('转出给灰灯（人事流动不是事故，不亮红灯）',
  rows.every((row) => row.status === '已转出' && row.statusTone === 'flat'), rows);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 3 人 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
check('第 2 页剩 3 人', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是周九（page 参数真的生效了）',
  rows[0]?.name === '周九', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（MemberView 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 10 个字段用列表行数据（含入党日期 / 联系电话）',
  probe?.detail.fields === 10 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/82-xuanfu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('xuanfu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xuanfu"]');
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
await page.route('**/party/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/party/v1/member/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="xuanfu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="xuanfu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('xuanfu');
  const empty = document.querySelector('[data-screen-id="xuanfu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="xuanfu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/83-xuanfu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
