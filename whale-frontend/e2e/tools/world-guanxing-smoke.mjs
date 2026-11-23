// 观星台（S7 / T7.7）冒烟：外城九处的第七处，本坊特殊项是**3D 场景嵌入**。
//
// 与舆图司的分工：那一处把空间数据画到地上（围栏形状），这一处把孪生场景
// **立成一座浑天仪**——已发布场景里的星标按 positionJson 等比缩进球体，
// 告警红、热点青、标签金，天心随告警变色。
//
// 十二件事：
//   ① 屏风挂的是场景台账页；概览只给一个入口（孪生没有跨场景的实体列表），
//      所以只有一个可点标签——别假设"几处九坊就几个标签"；
//   ② 四张牌走 /twin/overview：场景/星标/快照三张灰灯只报数，未恢复告警按
//      严重度三档（有严重红、有未恢复黄、全清绿）；
//   ③ 柱子是**近七日告警**：场景与星标是存量，永远不会掉；告警按天落，
//      掉到 0 才是"设备一直安分"；
//   ④ 分页参数是 **current/size**（平台统一口径）。这里与舆图司刻意相反：
//      那一处是 page/size，发错不报错、永远第 1 页。假接口按 current 真切页，
//      所以"第 2 页换了行"就是参数名正确的证据；
//   ⑤ 场景状态走**页面级**字典（draft/published/disabled 是字符串枚举），
//      列级字典只用于 0/1 这类码；
//   ⑥ 有详情接口（单行读），详情 11 个字段来自 /twin/scenes/{id}；
//   ⑦ 浑天仪真的立起来了：位姿取世界坐标，场景取**已发布**的第一座；
//   ⑧ 星标按启用过滤（停用的不画）、坏坐标丢弃，点数与类型数都要对得上；
//   ⑨ 有告警时天心变红——这是"红点聚在哪一侧"的入口，也是唯一用颜色说话的地方；
//   ⑩ 三环与星点在转：spin 随时间推进，证明它不是一张静图；
//   ⑪ 无已发布场景时只立空盘（不拿草稿充数），探针写明原因；
//   ⑫ 接口全挂时不白屏：概览标 stale + 收起表格给一句人话；403 不重试。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="guanxing"] ';

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
/* 与 TwinOverviewProvider 实际下发一致：四张 KPI（未恢复告警 → 黄）、
   一张七日告警柱图、两条告警（未确认 warn + 已确认 info）、一个入口。 */
const OVERVIEW = {
  service: 'twin',
  nodeKey: 'guanxing-stage',
  title: '观星台',
  subtitle: '星象与孪生 · 场景 · 告警 · 快照',
  generatedAt: '2026-11-23T09:20:00+08:00',
  state: 'live',
  kpis: [
    { key: 'scenes', label: '观星场景', value: 7, format: 'count', tone: 'flat', hint: '已发布 3' },
    { key: 'markers', label: '星标', value: 9, format: 'count', tone: 'flat', hint: '另停用 2' },
    { key: 'alerts', label: '未恢复告警', value: 4, format: 'count', tone: 'warn', hint: '待确认 2' },
    { key: 'snapshots', label: '发布快照', value: 12, format: 'count', tone: 'flat', hint: '近七日 +2' },
  ],
  charts: [
    {
      key: 'alert-trend',
      title: '近七日告警',
      type: 'bar',
      format: 'count',
      categories: ['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23'],
      series: [{ name: '告警数', values: [2, 0, 1, 0, 3, 1, 0] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '还有 2 条告警没有人工确认，值班的人可能还不知道', count: null },
    { level: 'info', text: '4 条告警未恢复，但都已确认', count: null },
  ],
  links: [
    { key: 'twin-scenes', label: '场景台账', path: '/twin/v1/scenes/page', permission: 'sys:twin:scene' },
  ],
  notes: ['口径：未恢复取 resolved=0（与告警抽屉同源）；草稿与停用都不参与运行时评估，只有已发布场景会被浑天仪读出来'],
};

/**
 * 7 座场景 = 2 页（5 + 2）。刻意安排：
 *  · 3 座已发布（s1/s4/s6）→ 浑天仪取第一座 s1；
 *  · 2 座草稿、2 座已停用 → 三种灯色都要出现；
 *  · s1 有 3 条未恢复（其中 1 条严重）→ 列表角标要能读出轻重。
 */
const SCENES = [
  {
    id: 's1', code: 'plant-a', name: '一号高炉产线', description: '炼钢主线，含 18 台设备与 6 个温压测点',
    status: 'published', entityCount: 18, unresolvedAlerts: 3, criticalAlerts: 1, unackedAlerts: 2,
    version: 7, createdBy: '1', createTime: '2026-10-08T09:12:00+08:00', updateTime: '2026-11-22T18:40:00+08:00',
  },
  {
    id: 's2', code: 'air-station', name: '空压站', description: '还在搭，只有一台样机',
    status: 'draft', entityCount: 0, unresolvedAlerts: 0, criticalAlerts: 0, unackedAlerts: 0,
    version: 1, createdBy: '1', createTime: '2026-11-20T15:00:00+08:00', updateTime: '2026-11-21T10:05:00+08:00',
  },
  {
    id: 's3', code: 'pack-old', name: '旧包装线', description: null,
    status: 'disabled', entityCount: 6, unresolvedAlerts: 0, criticalAlerts: 0, unackedAlerts: 0,
    version: 4, createdBy: '1', createTime: '2026-06-11T08:00:00+08:00', updateTime: '2026-10-02T16:30:00+08:00',
  },
  {
    id: 's4', code: 'substation', name: '变电站', description: '110kV 主变与馈线',
    status: 'published', entityCount: 22, unresolvedAlerts: 1, criticalAlerts: 0, unackedAlerts: 0,
    version: 9, createdBy: '1', createTime: '2026-05-20T11:20:00+08:00', updateTime: '2026-11-19T09:15:00+08:00',
  },
  {
    id: 's5', code: 'water-room', name: '循环水房', description: '泵组与冷却塔',
    status: 'draft', entityCount: 3, unresolvedAlerts: 0, criticalAlerts: 0, unackedAlerts: 0,
    version: 2, createdBy: '1', createTime: '2026-11-12T14:40:00+08:00', updateTime: '2026-11-18T17:02:00+08:00',
  },
  {
    id: 's6', code: 'raw-yard', name: '原料仓', description: '堆取料机与料位',
    status: 'published', entityCount: 11, unresolvedAlerts: 0, criticalAlerts: 0, unackedAlerts: 0,
    version: 5, createdBy: '1', createTime: '2026-07-03T10:00:00+08:00', updateTime: '2026-11-15T13:25:00+08:00',
  },
  {
    id: 's7', code: 'qc-line', name: '质检线', description: '已并入一号产线',
    status: 'disabled', entityCount: 5, unresolvedAlerts: 2, criticalAlerts: 0, unackedAlerts: 0,
    version: 3, createdBy: '1', createTime: '2026-04-18T09:30:00+08:00', updateTime: '2026-09-27T20:10:00+08:00',
  },
];

/**
 * 一号高炉的星标：8 条，画得出来 6 个点。
 *
 *  · 3 条 label、2 条 alarm、1 条 hotspot → 三种颜色都要出现，天心要变红；
 *  · 1 条 enabled=0（停用）→ 不进盘，`markers` 与 `points` 的差就是它；
 *  · 1 条 positionJson 是坏 JSON → 丢点但不报错（探针里 points 再少一个）；
 *  · 坐标格式两种混用：数组与 {x,y,z}，两种都要认。
 */
const MARKERS = [
  { id: 'm1', markerType: 'alarm', content: '1# 炉温超上限', positionJson: '[6, 2.4, -3]', enabled: 1 },
  { id: 'm2', markerType: 'alarm', content: '除尘风机振动', positionJson: '{"x":-4.5,"y":1.2,"z":4}', enabled: 1 },
  { id: 'm3', markerType: 'hotspot', content: '出铁口', positionJson: '[1.5, 0.8, 2.2]', enabled: 1 },
  { id: 'm4', markerType: 'label', content: '热风炉', positionJson: '[-6, 3.6, -1.5]', enabled: 1 },
  { id: 'm5', markerType: 'label', content: '上料皮带', positionJson: '{"x":3,"y":1.8,"z":5.4}', enabled: 1 },
  { id: 'm6', markerType: 'label', content: '渣处理', positionJson: '[7.2, 0.6, 1]', enabled: 1 },
  { id: 'm7', markerType: 'label', content: '已废弃的取样点', positionJson: '[0, 2, 0]', enabled: 0 },
  { id: 'm8', markerType: 'label', content: '坐标没填全', positionJson: '{"x":1}', enabled: 1 },
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

const sceneQueries = [];
const markerQueries = [];
const detailQueries = [];
const overviewHits = [];

/**
 * 场景系列接口的统一替身：按路径分流（/page、/markers、/{id}）。
 *
 * <p>一次注册而不是三条 route：通配符里的星号不跨路径分隔符，但 {@code page}
 * 也是单段，用通配符会连 /page 一起吃掉——按 pathname 分流最省心。</p>
 *
 * @param route Playwright 路由
 */
async function fulfillScenes(route) {
  const url = new URL(route.request().url());
  const path = url.pathname;
  if (path.endsWith('/page')) {
    const current = Number(url.searchParams.get('current') ?? '1');
    const size = Number(url.searchParams.get('size') ?? '20');
    const status = url.searchParams.get('status') ?? '';
    sceneQueries.push({
      names: [...url.searchParams.keys()],
      current: url.searchParams.get('current'),
      size: url.searchParams.get('size'),
      status,
    });
    /* 真的按 status 过滤、按 current/size 切页：参数名发错（例如发成 page）
       就会落回默认 current=1，屏风上页码变而内容不变。 */
    const matched = SCENES.filter((scene) => !status || scene.status === status);
    const start = Math.max(0, (current - 1) * size);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(json({
        records: matched.slice(start, start + size),
        total: matched.length,
        current,
        size,
      })),
    });
    return;
  }
  if (path.endsWith('/markers')) {
    markerQueries.push(path);
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(MARKERS)) });
    return;
  }
  detailQueries.push(path);
  const id = path.split('/').pop();
  const scene = SCENES.find((item) => item.id === id) ?? null;
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(scene)) });
}

await page.route('**/twin/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});
await page.route('**/twin/v1/scenes/**', fulfillScenes);

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是场景台账 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="guanxing"]');
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
check('观星台屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 guanxing', mounted?.id === 'guanxing', mounted?.id);
check('大屏规格', mounted?.size === 'large', mounted?.size);
check('题头是观星台 · 场景台账', String(mounted?.title).includes('场景台账'), mounted?.title);
check('入口标签跟概览 links 走：只有一个场景台账（孪生没有跨场景的实体列表）',
  mounted?.tabs?.length === 1 && mounted.tabs[0] === '场景台账', mounted?.tabs);
check('唯一的入口可点（本服务全类共用一个权限码）', mounted?.tabDisabled?.[0] === false, mounted?.tabDisabled);

/* ---- 2. 概览走 /twin/overview ---- */
check('概览请求打到 /twin/overview',
  overviewHits.length > 0 && overviewHits.every((p) => p === '/twin/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('guanxing'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（场景/星标/未恢复/快照）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（近七日告警）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（未确认黄 + 已确认青）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="guanxing"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('场景 7、灰灯（存量只报数）', kpiCards[0]?.value === '7' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('场景提示带已发布数', String(kpiCards[0]?.label).includes('已发布 3'), kpiCards[0]?.label);
check('星标 9、灰灯，提示带停用数', kpiCards[1]?.value === '9' && kpiCards[1]?.tone === 'flat'
  && String(kpiCards[1]?.label).includes('另停用 2'), kpiCards[1]);
check('未恢复告警 4、黄灯', kpiCards[2]?.value === '4' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('告警提示先说"待确认"（有人还不知道比"没恢复"更要紧）',
  String(kpiCards[2]?.label).includes('待确认 2'), kpiCards[2]?.label);
check('发布快照 12、灰灯，提示带近七日新增', kpiCards[3]?.value === '12'
  && String(kpiCards[3]?.label).includes('近七日 +2'), kpiCards[3]);

/* ---- 3. 七日柱图：七根、标签是月-日 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="guanxing"]');
  return {
    bars: scope?.querySelectorAll('.bwsp-barrow').length ?? 0,
    labels: [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent),
  };
});
check('柱图七根柱', barChart.bars === 7, barChart);
check('七根柱子按日期升序（11-17 → 11-23）',
  JSON.stringify(barChart.labels) === JSON.stringify(['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23']),
  barChart.labels);

/* ---- 4. 场景台账：列、页面级字典、分页参数名 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="guanxing"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        name: cells[0]?.textContent?.trim() ?? '',
        code: cells[1]?.textContent?.trim() ?? '',
        status: cells[2]?.textContent?.trim() ?? '',
        statusTone: cells[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        entities: cells[3]?.textContent?.trim() ?? '',
        unresolved: cells[4]?.textContent?.trim() ?? '',
        version: cells[5]?.textContent?.trim() ?? '',
        updated: cells[6]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 5 行（七根纵柱之后只排得下 5 行）', rows.length === 5, rows.length);
check('首行是一号高炉产线', rows[0]?.name === '一号高炉产线', rows[0]?.name);
check('编码照原样（plant-a）', rows[0]?.code === 'plant-a', rows[0]?.code);
check('已发布绿标（页面级字典）', rows[0]?.status === '已发布' && rows[0]?.statusTone === 'good', rows[0]);
check('实体数 18', rows[0]?.entities === '18', rows[0]?.entities);
check('未恢复告警 3', rows[0]?.unresolved === '3', rows[0]?.unresolved);
check('版本 7', rows[0]?.version === '7', rows[0]?.version);
check('更新时间走 datetime 口径（MM-DD HH:mm）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.updated ?? ''), rows[0]?.updated);
check('草稿灰标（第二行空压站）', rows[1]?.status === '草稿' && rows[1]?.statusTone === 'flat', rows[1]);
check('已停用黄标（第三行旧包装线）', rows[2]?.status === '已停用' && rows[2]?.statusTone === 'warn', rows[2]);

/* 分页参数名：平台统一口径是 current/size（场景分页接口读 current）。
   舆图司是 page/size——两处刻意不同，发错都不报错、只是永远第 1 页。
   按 current 真切页之后，"第 2 页换了行"才说明参数名对上了。 */
const listCalls = sceneQueries.filter((call) => call.size === '5');
const orreryCalls = sceneQueries.filter((call) => call.size === '1');
check('场景接口一律带 current/size（不是 page/size）',
  sceneQueries.length > 0 && sceneQueries.every((call) => call.names.includes('current') && call.names.includes('size')
    && !call.names.includes('page')), sceneQueries.slice(0, 3));
check('浑天仪只要一条、且只要已发布（status=published&size=1）',
  orreryCalls.length === 1 && orreryCalls[0].status === 'published', orreryCalls);
check('台账首屏请求 current=1、size=5', listCalls[0]?.current === '1' && listCalls[0]?.size === '5', listCalls[0]);
check('首屏记录到 7 条（服务端分页，total 来自接口）', probe?.list.total === 7, probe?.list);

/* ---- 5. 浑天仪：3D 场景嵌入 ----
   位姿在殿前偏北（x≈-84、z≈1.6），读的是世界坐标（组位置），不是组内 0,0。 */
let orrery = await page.evaluate(() => window.__bwwOrrery?.() ?? null);
check('浑天仪立起来了', Boolean(orrery), orrery);
check('浑天仪的位姿是世界坐标（x<-78 且 z 在 ±3 之间，不是组内 0,0）',
  orrery?.x < -78 && Math.abs(orrery?.z ?? 99) < 3, orrery);
check('读的是已发布场景的第一座（plant-a）',
  orrery?.mode === 'live' && orrery?.sceneId === 's1' && orrery?.sceneCode === 'plant-a', orrery);
check('星标按启用过滤：8 条里画 6 个点（停用 1 条 + 坏坐标 1 条被丢掉）',
  orrery?.markers === 8 && orrery?.points === 6, orrery);
check('三种标记各归其位（标签 3 / 热点 1 / 告警 2）',
  orrery?.labels === 3 && orrery?.hotspots === 1 && orrery?.alarms === 2,
  { labels: orrery?.labels, hotspots: orrery?.hotspots, alarms: orrery?.alarms });
check('坐标 JSON 数组与 {x,y,z} 两种格式都认（两种各画出了点）',
  (orrery?.points ?? 0) >= 6, orrery?.points);
check('有告警时天心变红（#ff6b4a）', orrery?.coreColor === '#ff6b4a', orrery?.coreColor);
check('底座半径 1.7 米（与选址净空一致）', orrery?.radius === 1.7, orrery?.radius);

/* 转动：等 1.2 秒再读一次，角度必须推进（否则就是一张静图） */
const spinBefore = orrery?.spin ?? 0;
await page.waitForTimeout(1200);
const spinAfter = (await page.evaluate(() => window.__bwwOrrery?.() ?? null))?.spin ?? 0;
check('三环与星点在转（1.2 秒后角度推进）', spinAfter !== spinBefore, { spinBefore, spinAfter });

/* ---- 6. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="guanxing"]');
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
/* 观星台在正西外城（屏风落点 x≈-84.3, z≈-2.04）。若解出的 x≈0，说明误取了
   相机透视层的 transform——那条路会让玩家走错方向，必须当场拦住。 */
check('位姿来自世界层而非相机层（观星台在正西外城 x<-70）', pose?.x < -70, pose);

await page.evaluate(({ pose: p }) => {
  window.__bwwWalkTo(p.x + 4.5 * p.nx, p.z + 4.5 * p.nz);
  window.__bwwLook(Math.atan2(p.nx, p.nz), 0.16, 10);
}, { pose });
await page.waitForTimeout(18000);
await page.screenshot({ path: `${OUT}/140-guanxing-street.png` });
/* 不用 locator.click：走到跟前时屏风可能落在小地图面板底下（hit-target 被拦），
   而且屏风带 3D 变换，包围盒中心未必落在元素上。直接派发 click 走同一套监听。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="guanxing"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="guanxing"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="guanxing"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'guanxing', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 走位必须真的到了屏风前：dispatchEvent 会把走位失败盖过去（影戏院就是这么被盖的）。 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const gap = pose && playerAt ? Math.round(Math.hypot(pose.x - playerAt.x, pose.z - playerAt.z)) : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 屏风 (${pose?.x}, ${pose?.z}) 距离 ${gap}m`);
check('走位真的走到了屏风前（距离 < 10 米）', gap !== null && gap < 10, { playerAt, pose, gap });
await page.screenshot({ path: `${OUT}/141-guanxing-engaged.png` });

/* ---- 7. 翻页：服务端分页，current=2 必须真的换一批行 ---- */
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('guanxing'));
check('第 2 页 2 行、总数 7', probe?.list.page === 2 && probe?.list.rows === 2 && probe?.list.total === 7, probe?.list);
check('第 2 页请求带 current=2（参数名对上才换得了页）',
  sceneQueries.some((call) => call.current === '2' && call.size === '5'),
  sceneQueries.filter((call) => call.size === '5'));
rows = await readRows();
check('第 2 页换了一批行（行首是原料仓）', rows[0]?.name === '原料仓', rows[0]?.name);
check('原料仓也是已发布绿标', rows[0]?.status === '已发布', rows[0]);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('guanxing'));
check('退回第 1 页', probe?.list.page === 1 && probe?.list.rows === 5, probe?.list);

/* ---- 8. 详情：单行读接口，11 个字段 ---- */
const detailCallsBefore = detailQueries.length;
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('guanxing'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('详情走 /twin/scenes/{id}（本页配了 detailPath）',
  detailQueries.length === detailCallsBefore + 1 && detailQueries[detailQueries.length - 1] === '/twin/v1/scenes/s1',
  detailQueries);
check('详情 11 个字段', probe?.detail.fields === 11 && probe?.detail.lines === 0, probe?.detail);
check('详情标题取 titleKey（场景名）', probe?.detail.title === '一号高炉产线', probe?.detail.title);
const detailValues = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="guanxing"] .bwsp-kv dd')].map((dd) => dd.textContent?.trim() ?? ''),
);
check('详情里有描述原文', detailValues.includes('炼钢主线，含 18 台设备与 6 个温压测点'), detailValues);
check('详情里状态走字典（中文，不是裸的 published）', detailValues.includes('已发布'), detailValues);
check('详情里"其中严重"单列一格（轻重分得开）', detailValues.includes('1'), detailValues);
await page.screenshot({ path: `${OUT}/142-guanxing-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('guanxing'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 5, probe);

/* ---- 9. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="guanxing"]');
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

/* ---- 10. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 ---- */
await page.route('**/twin/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/twin/v1/scenes/**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="guanxing"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="guanxing"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('guanxing');
  const empty = document.querySelector('[data-screen-id="guanxing"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="guanxing"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/143-guanxing-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

/* ---- 11. 无已发布场景：只立空盘，不拿草稿充数 ----
   单独开一页：浑天仪在进城时就取数，只在启动那一趟验得到"没有已发布"这一档。 */
const empty = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await empty.addInitScript(() => {
  try {
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});
let emptyMarkerCalls = 0;
await empty.route('**/twin/overview**', (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) }),
);
await empty.route('**/twin/v1/scenes/**', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/markers')) {
    emptyMarkerCalls += 1;
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json([])) });
    return;
  }
  await route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify(json({ records: [], total: 0, current: 1, size: 10 })),
  });
});
await empty.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await empty.waitForTimeout(9000);
const emptyOrrery = await empty.evaluate(() => window.__bwwOrrery?.() ?? null);
check('没有已发布场景时浑天仪是空盘（mode=empty）', emptyOrrery?.mode === 'empty', emptyOrrery);
check('空盘的说明是"还没有已发布的场景"，不是"未接通"',
  String(emptyOrrery?.note).includes('已发布'), emptyOrrery?.note);
check('空盘不画点', emptyOrrery?.points === 0, emptyOrrery?.points);
check('没有场景就不去问星标（省一次必然为空的请求）', emptyMarkerCalls === 0, emptyMarkerCalls);
await empty.close();

/* ---- 12. 无凭据：403 时空盘写"无此殿凭据"，且不重试 ---- */
const denied = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await denied.addInitScript(() => {
  try {
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});
const deniedHits = [];
await denied.route('**/twin/overview**', (route) =>
  route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) }),
);
await denied.route('**/twin/v1/scenes/**', async (route) => {
  /* 同一条接口两路调用：浑天仪（size=1、status=published）与屏风台账（size=5）。
     要数的是**浑天仪那一路**问了几次，所以按 size 记。 */
  deniedHits.push(new URL(route.request().url()).searchParams.get('size'));
  await route.fulfill({ status: 403, contentType: 'application/json', body: '{"code":403,"message":"无权限"}' });
});
await denied.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await denied.waitForTimeout(9000);
const deniedOrrery = await denied.evaluate(() => window.__bwwOrrery?.() ?? null);
check('无凭据时浑天仪写"无此殿凭据"（不是空白、也不白屏）',
  deniedOrrery?.mode === 'denied' && String(deniedOrrery?.note).includes('凭据'), deniedOrrery);
/* 403 不重试：权限不会自己长出来，反复问只是浪费请求。
   401 会重试（先逛城再登录是常态），两者刻意不同。 */
check('403 不重试（浑天仪只问一次；401 才重试，权限不会自己长出来）',
  deniedHits.filter((size) => size === '1').length === 1, deniedHits);
await denied.close();

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
