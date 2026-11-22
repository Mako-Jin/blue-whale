// 舆图司（S7 / T7.6）冒烟：外城九处的第六处，本坊特殊项是**殿前的地面舆图纹理**。
//
// 与邮驿/传信司的分工：那两处验"有多少信、多少会话"，这一处验**空间数据的家底**——
// 打点、围栏、解析缓存，以及最要命的一笔：今天的调用配额还剩多少。
//
// 十一件事：
//   ① 屏风挂的是围栏台账页；概览下发两个入口，第二个（供应商状态）没有页配置，
//      于是渲染成**禁用标签**——别假设"几个入口就配几个标签"；
//   ② 四张牌走 /map/overview：打点/围栏/缓存三张灰灯只报数，今日调用按配额语气
//      （三成绿、打满红）；
//   ③ 柱子是**近七日调用量**：存量不会掉，调用量掉到 0 时打点一根都不会少；
//   ④ 分页参数是 **page/size** 不是 current/size（FenceQuery 读 page）：发错了
//      服务端不报错、永远返回第 1 页，页码照样变——本文件的假接口按 page 真的切页，
//      所以"第 2 页换了行"同时证明参数名对得上；
//   ⑤ 围栏类型走列级字典（圆形绿/多边形灰），启用状态走页面级字典（键是字符串）；
//   ⑥ 多边形的半径为空，列的 emptyText 显示"多边形"，不是裸的破折号；
//   ⑦ 地面舆图真的铺在殿前：位姿取**世界坐标**（mesh 自己在组里被摆平，
//      读 mesh.position 永远是 0），围栏数与形状数来自接口，亮度标准差证明"纸上画了东西"；
//   ⑧ 无凭据那一路：403 时地面写"无此殿凭据"，且不重试（权限不会自己长出来）；
//   ⑨ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑩ 列表行即详情：11 个字段用行数据平铺，不打详情接口（顶点串屏风上排不下）；
//   ⑪ 接口全挂时不白屏：概览标 stale + 收起表格给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="yutu"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s7-shots';
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
/* 与 MapOverviewProvider 的实际下发一致：四张 KPI（今日调用吃紧 → 黄）、
   一张七日调用量柱图、三条告警（配额 warn + 围栏 info + 缓存 info）、两个入口。 */
const OVERVIEW = {
  service: 'map',
  nodeKey: 'yutu-stage',
  title: '舆图司',
  subtitle: '舆图与疆界 · 点 · 界 · 注记',
  generatedAt: '2026-11-23T02:10:00+08:00',
  state: 'live',
  kpis: [
    { key: 'locations', label: '业务打点', value: 120, format: 'count', tone: 'flat', hint: '按业务对象唯一' },
    { key: 'fences', label: '围栏', value: 8, format: 'count', tone: 'flat', hint: '另停用 2 个' },
    { key: 'cache', label: '解析缓存', value: 80, format: 'count', tone: 'flat', hint: '过期 2 条' },
    { key: 'calls', label: '今日调用', value: 850, format: 'count', tone: 'warn', hint: '上限 1000' },
  ],
  charts: [
    {
      key: 'call-trend',
      title: '近七日调用量',
      type: 'bar',
      format: 'count',
      categories: ['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23'],
      series: [{ name: '调用次数', values: [620, 700, 640, 830, 900, 760, 850] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '今日配额已用八成以上（850 / 1000），再涨就要限流或扩容', count: null },
    { level: 'info', text: '解析缓存已全部过期，下一轮查询会重新问供应商（会吃配额）', count: null },
  ],
  links: [
    { key: 'fences', label: '围栏台账', path: '/map/v1/fence/page', permission: 'sys:map:fence:query' },
    { key: 'providers', label: '供应商状态', path: '/map/v1/provider/health', permission: 'sys:map:provider:view' },
  ],
  notes: ['口径：调用量取供应商日台账（各供应商合计）；配额按已用 ≥80% 算吃紧，上限为 0 表示不限；缓存过期不等于要清，命中时会被覆盖'],
};

/**
 * 8 条围栏 = 3 页（3 + 3 + 2）。刻意安排：
 *  · 4 圆 4 多边形 → 地面舆图上两种形状都要画得出来；
 *  · 2 条停用（enabled=0）→ 虚线 + 灰标；
 *  · 1 条没有 bizType → 列表显示"通用"；
 *  · 多边形没有 radiusMeters → 列表显示"多边形"。
 */
const FENCES = [
  {
    id: '7101', name: '城北配送区', fenceType: 'POLYGON', bizType: 'ORDER', bizId: null,
    centerLng: null, centerLat: null, radiusMeters: null, coordType: 'GCJ02', enabled: 1,
    remark: '冷链单独划一片', updateTime: '2026-11-22T18:20:00+08:00',
    points: [
      { lng: 116.32, lat: 39.96 }, { lng: 116.40, lat: 39.97 },
      { lng: 116.41, lat: 39.90 }, { lng: 116.33, lat: 39.89 },
    ],
  },
  {
    id: '7102', name: '总部打卡圈', fenceType: 'CIRCLE', bizType: 'OA', bizId: null,
    centerLng: 116.36, centerLat: 39.92, radiusMeters: 300, coordType: 'GCJ02', enabled: 1,
    remark: null, updateTime: '2026-11-21T09:05:00+08:00', points: null,
  },
  {
    id: '7103', name: '旧码头禁区', fenceType: 'POLYGON', bizType: null, bizId: null,
    centerLng: null, centerLat: null, radiusMeters: null, coordType: 'GCJ02', enabled: 0,
    remark: '码头已停用，围栏先留着指路', updateTime: '2026-11-18T15:40:00+08:00',
    points: [
      { lng: 116.44, lat: 39.95 }, { lng: 116.49, lat: 39.94 },
      { lng: 116.48, lat: 39.91 }, { lng: 116.43, lat: 39.92 },
    ],
  },
  {
    id: '7104', name: '东市电子围栏', fenceType: 'CIRCLE', bizType: 'ORDER', bizId: null,
    centerLng: 116.46, centerLat: 39.93, radiusMeters: 450, coordType: 'GCJ02', enabled: 1,
    remark: null, updateTime: '2026-11-20T11:30:00+08:00', points: null,
  },
  {
    id: '7105', name: '南仓配送区', fenceType: 'POLYGON', bizType: 'ORDER', bizId: null,
    centerLng: null, centerLat: null, radiusMeters: null, coordType: 'GCJ02', enabled: 1,
    remark: null, updateTime: '2026-11-19T08:10:00+08:00',
    points: [
      { lng: 116.34, lat: 39.86 }, { lng: 116.41, lat: 39.87 },
      { lng: 116.40, lat: 39.82 }, { lng: 116.35, lat: 39.81 },
    ],
  },
  {
    id: '7106', name: '巡防区', fenceType: 'CIRCLE', bizType: 'OA', bizId: null,
    centerLng: 116.31, centerLat: 39.88, radiusMeters: 260, coordType: 'GCJ02', enabled: 0,
    remark: null, updateTime: '2026-11-17T20:00:00+08:00', points: null,
  },
  {
    id: '7107', name: '西坊临时圈', fenceType: 'CIRCLE', bizType: null, bizId: null,
    centerLng: 116.35, centerLat: 39.90, radiusMeters: 180, coordType: 'GCJ02', enabled: 1,
    remark: null, updateTime: '2026-11-16T10:20:00+08:00', points: null,
  },
  {
    id: '7108', name: '北门缓冲带', fenceType: 'POLYGON', bizType: 'ORDER', bizId: null,
    centerLng: null, centerLat: null, radiusMeters: null, coordType: 'GCJ02', enabled: 1,
    remark: null, updateTime: '2026-11-15T14:00:00+08:00',
    points: [
      { lng: 116.30, lat: 39.99 }, { lng: 116.38, lat: 40.00 },
      { lng: 116.39, lat: 39.95 }, { lng: 116.31, lat: 39.94 },
    ],
  },
];

const json = (data) => ({ code: 0, message: 'ok', data });

const browser = await chromium.launch({
  channel: 'msedge',
  args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});

/**
 * 假围栏接口：**真的按 page/size 切页**。
 *
 * <p>这是本文件最关键的一处替身：若前端把页码发成 current（FenceQuery 不认），
 * 服务端会按默认 page=1 返回同一批行，而屏风上的页码照样从 1 变到 3——
 * "看着在翻页、其实没翻"就是这么发生的。按 page 真切页，才能让"第 2 页换了行"
 * 变成参数名正确的证据。</p>
 *
 * @param route Playwright 路由
 */
async function fulfillFences(route) {
  const url = new URL(route.request().url());
  const page = Number(url.searchParams.get('page') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '20');
  const start = Math.max(0, (page - 1) * size);
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({
      records: FENCES.slice(start, start + size),
      total: FENCES.length,
      current: page,
      size,
    })),
  });
}

const queries = [];
const overviewHits = [];

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

await page.route('**/map/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/map/v1/fence/page**', async (route) => {
  const url = new URL(route.request().url());
  queries.push({
    names: [...url.searchParams.keys()],
    page: url.searchParams.get('page'),
    size: url.searchParams.get('size'),
    current: url.searchParams.get('current'),
  });
  await fulfillFences(route);
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是围栏台账 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yutu"]');
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  return {
    size: el.dataset.size,
    title: el.querySelector('.bws-title')?.textContent ?? '',
    w: Math.round(rect.width),
    h: Math.round(rect.height),
    hasPage: Boolean(el.querySelector('.bwsp')),
    tabs: [...el.querySelectorAll('.bwsp-tab')].map((tab) => tab.textContent),
    tabDisabled: [...el.querySelectorAll('.bwsp-tab')].map((tab) => tab.disabled),
    id: el.dataset.screenId,
  };
});
check('舆图司屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 yutu', mounted?.id === 'yutu', mounted?.id);
check('大屏规格', mounted?.size === 'large', mounted?.size);
check('题头是舆图司 · 围栏台账', String(mounted?.title).includes('围栏台账'), mounted?.title);
check('入口标签跟概览 links 走：围栏台账 + 供应商状态两个',
  mounted?.tabs?.length === 2 && mounted.tabs[0] === '围栏台账' && mounted.tabs[1] === '供应商状态',
  mounted?.tabs);
/* 2D 里没有"供应商状态"这一页（map 前端还没落地），所以它只能是禁用标签。
   这条断言的意义：别假设"概览给了几个入口就配几个可点的标签"。 */
check('没有页配置的入口渲染成禁用标签（供应商状态不可点）',
  mounted?.tabDisabled?.[0] === false && mounted?.tabDisabled?.[1] === true, mounted?.tabDisabled);

/* ---- 2. 概览走 /map/overview ---- */
check('概览请求打到 /map/overview',
  overviewHits.length > 0 && overviewHits.every((p) => p === '/map/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（打点/围栏/缓存/今日调用）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（近七日调用量）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（配额黄 + 缓存提示）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yutu"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('打点 120、灰灯（存量只报数）', kpiCards[0]?.value === '120' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('围栏 8、灰灯，提示带停用数', kpiCards[1]?.value === '8' && kpiCards[1]?.tone === 'flat'
  && String(kpiCards[1]?.label).includes('另停用 2 个'), kpiCards[1]);
check('解析缓存 80、灰灯，提示带过期数',
  kpiCards[2]?.value === '80' && String(kpiCards[2]?.label).includes('过期 2 条'), kpiCards[2]);
check('今日调用 850、黄灯（配额八成以上）',
  kpiCards[3]?.value === '850' && kpiCards[3]?.tone === 'warn', kpiCards[3]);
check('今日调用提示给的是上限（单看已用读不出轻重）',
  String(kpiCards[3]?.label).includes('上限 1000'), kpiCards[3]?.label);

/* ---- 3. 七日柱图：七根、标签是月-日 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yutu"]');
  return {
    bars: scope?.querySelectorAll('.bwsp-barrow').length ?? 0,
    labels: [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent),
  };
});
check('柱图七根柱', barChart.bars === 7, barChart);
check('七根柱子按日期升序（11-17 → 11-23）',
  JSON.stringify(barChart.labels) === JSON.stringify(['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23']),
  barChart.labels);

/* ---- 4. 围栏台账：列、字典、空值口径、分页参数名 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="yutu"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        name: cells[0]?.textContent?.trim() ?? '',
        type: cells[1]?.textContent?.trim() ?? '',
        typeTone: cells[1]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        biz: cells[2]?.textContent?.trim() ?? '',
        radius: cells[3]?.textContent?.trim() ?? '',
        enabled: cells[4]?.textContent?.trim() ?? '',
        enabledTone: cells[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        updated: cells[5]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 3 行（与横柱图同一笔账：七根柱子吃掉两行）', rows.length === 3, rows.length);
check('首行是城北配送区', rows[0]?.name === '城北配送区', rows[0]?.name);
check('多边形灰标（类型走列级字典）', rows[0]?.type === '多边形' && rows[0]?.typeTone === 'flat', rows[0]);
check('多边形的半径列显示"多边形"（列级 emptyText，不是裸破折号）',
  rows[0]?.radius === '多边形', rows[0]?.radius);
check('业务类型照原样（ORDER）', rows[0]?.biz === 'ORDER', rows[0]?.biz);
check('启用绿标', rows[0]?.enabled === '启用' && rows[0]?.enabledTone === 'good', rows[0]);
check('更新时间走 datetime 口径（MM-DD HH:mm）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.updated ?? ''), rows[0]?.updated);
check('圆形绿标（第二行是打卡圈）', rows[1]?.type === '圆形' && rows[1]?.typeTone === 'good', rows[1]);
check('圆形的半径照数值显示（300）', rows[1]?.radius === '300', rows[1]?.radius);
check('停用围栏灰标（第三行旧码头）', rows[2]?.enabled === '停用' && rows[2]?.enabledTone === 'flat', rows[2]);
check('没有业务类型的围栏显示"通用"（列级 emptyText）', rows[2]?.biz === '通用', rows[2]?.biz);

/* 分页参数名：FenceQuery 读 page/size，发 current 会被静默忽略。
   同一条接口有两路调用：地面舆图要整份（size=100），屏风台账按页（size=3）——
   断言要按 size 分开看，混在一起会把"舆图那次"当成"台账首屏"。 */
const listCalls = queries.filter((call) => call.size === '3');
const groundCalls = queries.filter((call) => call.size === '100');
check('围栏接口一律带 page/size（不是 current/size）',
  queries.length > 0 && queries.every((call) => call.names.includes('page') && call.names.includes('size')
    && !call.names.includes('current')), queries.slice(0, 3));
check('地面舆图整份取回（size=100，一次画全）',
  groundCalls.length === 1 && groundCalls[0].page === '1', groundCalls);
check('台账首屏请求 page=1、size=3', listCalls[0]?.page === '1' && listCalls[0]?.size === '3', listCalls[0]);
check('首屏记录到 8 条（服务端分页，total 来自接口）', probe?.list.total === 8, probe?.list);

/* ---- 5. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yutu"]');
  /* 关键：`bws-screen` 元素**自身**的 matrix3d 才是世界变换（v[12]/v[14] 是世界
     x/z）；parentElement 是 CSS3DRenderer 的相机透视层，那层解出来是相机坐标。 */
  const own = el?.style.transform || '';
  const parent = el?.parentElement?.style.transform || '';
  const t = own.includes('matrix3d') ? own : (parent.includes('matrix3d') ? parent : own);
  const m = t.match(/matrix3d\(([^)]+)\)/);
  if (!m) return null;
  const v = m[1].split(',').map(Number);
  const zx = v[8];
  const zz = v[10];
  const len = Math.hypot(zx, zz) || 1;
  return { x: v[12], z: v[14], nx: zx / len, nz: zz / len };
});
check('能从 CSS3D 变换里解出屏风位姿', Boolean(pose) && Number.isFinite(pose?.x), pose);
/* 舆图司在正西偏南外城（屏风落点 x≈-72, z≈-44）。若解出的 x≈0，说明误取了
   相机透视层的 transform——那条路会让玩家走错方向，必须当场拦住。 */
check('位姿来自世界层而非相机层（舆图司在西部外城 x<-55 且 z<-30）',
  pose?.x < -55 && pose?.z < -30, pose);

/* 地面舆图：正前方 10.5 米、8×5.6 米，围着殿前那段净空铺开 */
let ground = await page.evaluate(() => window.__bwwMapGround?.() ?? null);
check('地面舆图铺上了', Boolean(ground), ground);
check('地面舆图的位姿是世界坐标（x<-55 且 z<-30，不是组内 0,0）',
  ground?.x < -55 && ground?.z < -30, ground);
check('舆图画布尺寸 1000×700（与地面 8×5.6 同比，圆形不会被拉成椭圆）',
  ground?.width === 1000 && ground?.height === 700, ground);
check('围栏按接口数据画出来（8 条：4 圆 4 多边形）',
  ground?.mode === 'live' && ground?.fences === 8 && ground?.circles === 4 && ground?.polygons === 4, ground);
check('多边形顶点数被记下来了（4×4=16，供排障看"画了几笔"）', ground?.vertices >= 16, ground?.vertices);
/* 亮度标准差：纯纸底约 2~3，画了围栏与题记后会明显更高。阈值取 4，
   既容得下纸底噪点，又拦得住"纹理没绑上、画布还是空白"这种假通过。 */
check('画布上真的有内容（亮度标准差 > 4，不是一张纯色纸）', (ground?.contrast ?? 0) > 4, ground?.contrast);

await page.evaluate(({ pose }) => {
  const px = pose.x + 4.5 * pose.nx;
  const pz = pose.z + 4.5 * pose.nz;
  window.__bwwWalkTo(px, pz);
  window.__bwwLook(Math.atan2(pose.nx, pose.nz), 0.18, 9);
}, { pose });
await page.waitForTimeout(18000);
/* 不用 locator.click：走到跟前时屏风可能落在小地图面板底下（hit-target 被拦），
   而且屏风带 3D 变换，包围盒中心未必落在元素上。直接派发 click 走同一套监听。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yutu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="yutu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="yutu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'yutu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 走位必须真的到了屏风前：dispatchEvent 会把走位失败盖过去（影戏院就是这么被盖的）。 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const gap = pose && playerAt ? Math.round(Math.hypot(pose.x - playerAt.x, pose.z - playerAt.z)) : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 屏风 (${pose?.x}, ${pose?.z}) 距离 ${gap}m`);
check('走位真的走到了屏风前（距离 < 10 米）', gap !== null && gap < 10, { playerAt, pose, gap });
await page.screenshot({ path: `${OUT}/120-yutu-engaged.png` });

/* ---- 6. 翻页：服务端分页，page=2 必须真的换一批行 ---- */
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('第 2 页 3 行、总数 8', probe?.list.page === 2 && probe?.list.rows === 3 && probe?.list.total === 8, probe?.list);
check('第 2 页请求带 page=2（参数名对上才换得了页）',
  queries.some((call) => call.page === '2' && call.size === '3'), queries.filter((call) => call.size === '3'));
rows = await readRows();
check('第 2 页换了一批行（行首是东市电子围栏）', rows[0]?.name === '东市电子围栏', rows[0]?.name);
check('第 2 页的圆形围栏半径照数值（450）', rows[0]?.radius === '450', rows[0]?.radius);

await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('第 3 页只剩 2 行（8 条 / 3 行）', probe?.list.page === 3 && probe?.list.rows === 2, probe?.list);
check('末页下一页禁用', (await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(700);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('退两页回到第 1 页（一次只退一页）', probe?.list.page === 1 && probe?.list.rows === 3, probe?.list);

/* ---- 7. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：只打围栏列表接口（顶点串屏风上排不下）',
  queries.length > 0 && [...new Set(queries.map((call) => call.names.join(',')))].length === 1,
  queries.length);
check('详情 11 个字段用列表行数据', probe?.detail.fields === 11 && probe?.detail.lines === 0, probe?.detail);
check('详情标题取 titleKey（围栏名）', probe?.detail.title === '城北配送区', probe?.detail.title);
const detailValues = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yutu"] .bwsp-kv dd')].map((dd) => dd.textContent?.trim() ?? ''),
);
check('详情里多边形的圆心写"多边形"（口径跟列一致）',
  detailValues.filter((value) => value === '多边形').length >= 3, detailValues);
check('详情里有备注原文', detailValues.includes('冷链单独划一片'), detailValues);
await page.screenshot({ path: `${OUT}/121-yutu-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yutu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 3, probe);

/* ---- 8. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yutu"]');
  return {
    engaged: document.body.dataset.screenEngaged ?? null,
    focus: el?.dataset.focus ?? null,
    layer: el?.parentElement?.id ?? null,
    htmlClass: el?.className ?? '',
  };
});
check('Esc 退出屏风', exited.engaged === null, exited);
check('退出后恢复非贴脸态', exited.focus === 'false', exited.focus);
check('退出后回到 3D 层（parentElement 不再是平面化层 bww-planar）',
  exited.layer !== 'bww-planar', exited);
check('退出后屏风不再是贴脸样式', !String(exited.htmlClass).includes('bww-focus'), exited.htmlClass);

/* ---- 9. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 ---- */
await page.route('**/map/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/map/v1/fence/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yutu"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="yutu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('yutu');
  const empty = document.querySelector('[data-screen-id="yutu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="yutu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/122-yutu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

/* ---- 10. 无凭据那一路：403 时地面写"无此殿凭据"，且不重试 ----
   单独开一页：地面舆图在进城时就取数，只在启动那一趟验得到"没凭据"这一档。 */
const denied = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await denied.addInitScript(() => {
  try {
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});
/* 同一条接口有两路调用：地面舆图（size=100）与屏风台账（size=3）。
   要数的是**地面那一路**问了几次，所以按 size 分开记。 */
const deniedHits = [];
await denied.route('**/map/overview**', (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) }),
);
await denied.route('**/map/v1/fence/page**', async (route) => {
  deniedHits.push(new URL(route.request().url()).searchParams.get('size'));
  await route.fulfill({ status: 403, contentType: 'application/json', body: '{"code":403,"message":"无权限"}' });
});
await denied.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await denied.waitForTimeout(9000);
const deniedGround = await denied.evaluate(() => window.__bwwMapGround?.() ?? null);
check('无凭据时地面写"无此殿凭据"（不是空白、也不白屏）',
  deniedGround?.mode === 'denied' && String(deniedGround?.note).includes('凭据'), deniedGround);
/* 403 不重试：权限不会自己长出来，反复问只是浪费请求。
   401 会重试（先逛城再登录是常态），两者刻意不同。 */
check('403 不重试（地面舆图只问一次；401 才重试，权限不会自己长出来）',
  deniedHits.filter((size) => size === '100').length === 1, deniedHits);
await denied.close();

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
