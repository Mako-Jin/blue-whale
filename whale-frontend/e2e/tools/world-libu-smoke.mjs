// 吏部坊考勤日报（S6 / T6.7–T6.8）冒烟：内城十二坊的第二块屏风页。
//
// 与工部坊的分工：工部验"车间在不在跑"，这一条验**人**——在册、缺卡、迟到。
// 五件事：
//   ① 概览四张牌里"在职人数"来自人事档案（另一上下文的口径），缺
//      `sys:oa:hr:profile:list` 时应整块裁掉，而不是发下来让前端隐藏；
//   ② 考勤页**没有搜索框**——`AttendanceDailyQuery` 只有 userId/status/dateFrom/dateTo，
//      没有 keyword。给它配搜索框就是发一个后端会默默忽略的参数名；
//   ③ 日状态是五个枚举（NORMAL/LATE/EARLY/LATE_AND_EARLY/ABSENT），靠页面级字典
//      译中文并打灯；「迟到+早退」是**一档**，不是两盏灯；
//   ④ 列表行即详情（不配 detailPath）：`/oa/attendance/daily/page` 返回的就是整行明细，
//      这个模块根本没有 `/daily/{id}` 详情接口；
//   ⑤ 接口全挂时不白屏：标 stale + 给一句人话。
//
// 数据来源：page.route 拦 /oa/overview 与 /oa/attendance/daily/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十二块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="libu"] ';

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
/* 与 OaOverviewProvider 的实际下发一致：四个 KPI（一档是另一上下文的口径）、
   一张人次图、两条告警、一个入口 */
const OVERVIEW = {
  service: 'oa',
  nodeKey: 'libu-hall',
  title: '吏部',
  subtitle: '人事考课 · 2026-11-01 ~ 2026-11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'headcount', label: '在职人数', value: 113, format: 'count', tone: 'flat', hint: '按人事档案', display: '113' },
    { key: 'absent', label: '今日缺卡', value: 2, format: 'count', tone: 'bad', hint: '今日在册 3 人', display: '2' },
    { key: 'late', label: '今日迟到', value: 1, format: 'count', tone: 'warn', hint: '迟到分钟 > 0 的人数', display: '1' },
    { key: 'work-days', label: '本月出勤', value: 44, format: 'count', tone: 'flat', hint: '人次（每日汇总累计）', display: '44' },
  ],
  charts: [
    {
      key: 'abnormal',
      title: '本月考勤异常（人次）',
      type: 'bar',
      format: 'count',
      categories: ['迟到', '早退', '缺卡'],
      series: [{ name: '人次', values: [2, 4, 3] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '今日 2 人缺卡', count: 2 },
    { level: 'info', text: '今日 1 人迟到', count: 1 },
  ],
  links: [
    { key: 'attendance', label: '考勤日报', path: '/oa/attendance/daily/page', permission: 'sys:oa:attendance:daily:list' },
  ],
  notes: ['口径：考勤日报 oa_att_daily，与 2D 考勤月报同一条 SQL'],
};

/**
 * 8 行考勤日汇总：五个状态全覆到（NORMAL / LATE / EARLY / LATE_AND_EARLY / ABSENT），
 * 够验字典、打灯与分页（6 行/页 → 2 页）
 */
const DAILY = [
  { id: 9201, userId: 11, userName: '舰长', workDate: '2026-11-21', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: '2026-11-21T08:52:00', lastOut: '2026-11-21T18:06:00', lateMinutes: 0, earlyMinutes: 0, absent: 0, status: 'NORMAL', remark: null },
  { id: 9202, userId: 12, userName: '副舰长', workDate: '2026-11-21', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: '2026-11-21T09:12:00', lastOut: '2026-11-21T18:02:00', lateMinutes: 12, earlyMinutes: 0, absent: 0, status: 'LATE', remark: null },
  { id: 9203, userId: 13, userName: '轮机长', workDate: '2026-11-21', groupId: 2, shiftCode: 'NIGHT', shiftStart: '21:00:00', shiftEnd: '06:00:00', firstIn: '2026-11-21T20:58:00', lastOut: '2026-11-22T05:35:00', lateMinutes: 0, earlyMinutes: 25, absent: 0, status: 'EARLY', remark: '机舱检修提前离岗' },
  { id: 9204, userId: 14, userName: '水手长', workDate: '2026-11-21', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: '2026-11-21T09:08:00', lastOut: '2026-11-21T17:45:00', lateMinutes: 8, earlyMinutes: 15, absent: 0, status: 'LATE_AND_EARLY', remark: null },
  { id: 9205, userId: 15, userName: '舵手', workDate: '2026-11-21', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: null, lastOut: null, lateMinutes: 0, earlyMinutes: 0, absent: 1, status: 'ABSENT', remark: '全天无打卡记录' },
  { id: 9206, userId: 16, userName: '声呐员', workDate: '2026-11-21', groupId: 2, shiftCode: 'NIGHT', shiftStart: '21:00:00', shiftEnd: '06:00:00', firstIn: '2026-11-21T20:47:00', lastOut: '2026-11-22T06:03:00', lateMinutes: 0, earlyMinutes: 0, absent: 0, status: 'NORMAL', remark: null },
  { id: 9207, userId: 17, userName: '舵信员', workDate: '2026-11-20', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: '2026-11-20T09:05:00', lastOut: '2026-11-20T18:01:00', lateMinutes: 5, earlyMinutes: 0, absent: 0, status: 'LATE', remark: null },
  { id: 9208, userId: 18, userName: '舱段兵', workDate: '2026-11-20', groupId: 1, shiftCode: 'DAY', shiftStart: '09:00:00', shiftEnd: '18:00:00', firstIn: '2026-11-20T08:55:00', lastOut: '2026-11-20T18:10:00', lateMinutes: 0, earlyMinutes: 0, absent: 0, status: 'NORMAL', remark: null },
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

/** 记录列表真实发出的查询参数：不许出现 keyword，也不许带空 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/oa/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   吏部坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/oa/attendance/daily/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/oa/attendance/daily/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 AttendanceDailyQuery 只有 userId / status / dateFrom / dateTo，没有 keyword
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    status,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = DAILY.filter((row) => status === null || status === '' || row.status === status);
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是考勤日报页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="libu"]');
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
check('吏部坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 libu（探针与 e2e 都靠它定位）', mounted?.id === 'libu', mounted?.id);
check('大屏规格（六列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是吏部坊 · 考勤日报', String(mounted?.title).includes('考勤日报'), mounted?.title);
check('入口标签来自概览 links（考勤日报）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '考勤日报', mounted?.tabs);

/* ---- 2. 概览走 /oa/overview（不是别的殿的路径） ---- */
check('概览请求打到 /oa/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/oa/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('libu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在职/缺卡/迟到/出勤）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（本月考勤异常）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（缺卡 + 迟到）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="libu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在职人数按档案口径（113 = 120 档案 − 7 离职）', kpiCards[0]?.value === '113', kpiCards[0]);
/* 渲染器把 hint 拼在标签后面（"在职人数 · 按人事档案"），没有单独的 hint 元素 */
check('在职人数的标签写清口径"按人事档案"（不是"在职员工"）',
  String(kpiCards[0]?.label).includes('按人事档案'), kpiCards[0]?.label);
check('今日缺卡亮红灯', kpiCards[1]?.tone === 'bad' && kpiCards[1]?.value === '2', kpiCards[1]);
check('今日迟到亮黄灯', kpiCards[2]?.tone === 'warn' && kpiCards[2]?.value === '1', kpiCards[2]);
/* 关键断言：本月出勤的单位是**人次**，不是人数。44 人次的提示语必须自己说清，
   否则读牌的人会以为这个月只有 44 个人上过班。 */
check('本月出勤的单位写清是"人次"（不能被读成 44 个人）',
  String(kpiCards[3]?.label).includes('人次'), kpiCards[3]?.label);

/* ---- 3. 横向条形图三个类目（迟到/早退/缺卡） ---- */
/* 屏风是 1000×620 的横屏，渲染器把 bar 画成**横向**条（`.bwsp-barlabel` 逐行），
   不是竖柱图那个 `.bwsp-barlabels` 横轴。断言要跟着渲染器的实际结构走。 */
const chartRows = await page.evaluate(() =>
  [...(document.querySelector('[data-screen-id="libu"]')?.querySelectorAll('.bwsp-barrow') ?? [])]
    .map((row) => ({
      label: row.querySelector('.bwsp-barlabel')?.textContent ?? '',
      value: row.querySelector('.bwsp-barval')?.textContent ?? '',
    })),
);
check('条形图三个类目（迟到/早退/缺卡）',
  chartRows.map((row) => row.label).join(',') === '迟到,早退,缺卡', chartRows);
check('条形图数值与概览同源（2/4/3 人次）',
  chartRows.map((row) => row.value).join(',') === '2,4,3', chartRows);

/* ---- 4. 没有搜索框：考勤查询没有 keyword，就不该摆一个假搜索框 ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="libu"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const statusEl = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    status: statusEl ? [...statusEl.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('没有搜索框（AttendanceDailyQuery 无 keyword，不发明参数名）', filters?.searchHidden === true, filters);
check('状态筛选六项用后端原值（NORMAL…ABSENT），全部在前',
  filters?.status.join(',') ===
    ':全部状态,NORMAL:正常,LATE:迟到,EARLY:早退,LATE_AND_EARLY:迟到+早退,ABSENT:缺卡',
  filters?.status);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 status 空值，也不夹带 keyword）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="libu"] .bwsp-table tbody tr')].map((tr) => ({
    date: tr.children[0]?.textContent ?? '',
    name: tr.children[1]?.textContent ?? '',
    shift: tr.children[2]?.textContent ?? '',
    late: tr.children[3]?.textContent ?? '',
    early: tr.children[4]?.textContent ?? '',
    status: tr.children[5]?.textContent ?? '',
    statusTone: tr.children[5]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('考勤日期只到日（不把时间戳原样砸在列表上）', /^\d{4}-\d{2}-\d{2}$/.test(rows[0]?.date ?? ''), rows[0]?.date);
check('姓名原样显示', rows[0]?.name === '舰长', rows[0]?.name);
check('班次原样显示（DAY / NIGHT 是班次编码，不猜中文）', rows[0]?.shift === 'DAY', rows[0]?.shift);
check('NORMAL 译成"正常"并打绿灯', rows[0]?.status === '正常' && rows[0]?.statusTone === 'good', rows[0]);
check('LATE 译成"迟到"并打黄灯', rows[1]?.status === '迟到' && rows[1]?.statusTone === 'warn', rows[1]);
check('迟到分钟按原值排版（12）', rows[1]?.late === '12', rows[1]?.late);
check('EARLY 译成"早退"（早退 25 分钟）', rows[2]?.status === '早退' && rows[2]?.early === '25', rows[2]);
check('LATE_AND_EARLY 是一档"迟到+早退"，不是两盏灯',
  rows[3]?.status === '迟到+早退' && rows[3]?.statusTone === 'warn', rows[3]);
check('ABSENT 译成"缺卡"并打红灯（这是必须有人处理的一行）',
  rows[4]?.status === '缺卡' && rows[4]?.statusTone === 'bad', rows[4]);
await page.screenshot({ path: `${OUT}/20-libu-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="libu"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="libu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="libu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'libu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 状态筛选：发 status=ABSENT ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('ABSENT');
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu'));
const lastFilter = queried.filter((item) => item.status === 'ABSENT').pop();
check('状态按 status=ABSENT 发给服务端', Boolean(lastFilter), queried.slice(-3));
check('筛选时也不夹带 keyword', lastFilter ? !lastFilter.names.includes('keyword') : false, lastFilter?.names);
check('筛选后只剩 1 条（舵手）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
const absentRow = await page.evaluate(() =>
  document.querySelector('[data-screen-id="libu"] .bwsp-table tbody tr')?.children[1]?.textContent ?? '',
);
check('筛选出来的正是缺卡的那一行', absentRow === '舵手', absentRow);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(700);

/* ---- 8. 分页：第 2 页 2 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（OA 没有 /oa/attendance/daily/{id}）', detailHits.length === 0, detailHits);
check('详情 10 个字段用列表行数据（含打卡时刻与备注）',
  probe?.detail.fields === 10 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/21-libu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('libu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="libu"]');
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
await page.route('**/oa/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/oa/attendance/daily/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="libu"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="libu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('libu');
  const empty = document.querySelector('[data-screen-id="libu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="libu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/22-libu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
