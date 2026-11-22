// 传信司会话列表（S7 / T7.5）冒烟：外城九处的第五处，本坊特殊项是**贴脸读会话**。
//
// 与邮驿的分工：邮驿验异步信件的链路（同步/配额/退信），这一条验**即时会话**——
// 今天还有谁在说话、哪些会话已经静了七天、机器人有没有卡在等确认。
//
// 十件事：
//   ① 屏风挂的是会话列表页；概览只下发一个入口（会话列表），没有第二个可点的标签；
//   ② 四张牌走 /im/overview：会话总数灰灯只报数，静默会话 / 待确认两张有账就黄；
//   ③ 柱子是**近七日消息量**（不是按类型分堆）：会话数是存量，消息量掉到 0 时
//      会话数一点都不会变；
//   ④ 接口返回的是**普通数组**（不是 {records,total}），前端本地切页：
//      请求里不该出现 current/size，翻页只切数组；
//   ⑤ 单聊标题为空是平台约定（2D 也显示"未命名会话"），屏风走列的 emptyText，
//      不能显示成裸的"—"；
//   ⑥ "最后一句"走嵌套字段 lastMessage.content（列键 a.b），没有消息的会话
//      显示"还没有消息"；
//   ⑦ 会话类型走字典（单聊灰 / 群聊绿 / 机器人黄）；
//   ⑧ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑨ 列表行即详情：9 个字段用行数据平铺，不打详情接口（后端没有按会话取详情的接口）；
//   ⑩ 接口全挂时不白屏：标 stale + 收起表格给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="chuanxin"] ';

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
/* 与 ImOverviewProvider 的实际下发一致：四张 KPI（静默与待确认两张黄）、
   一张七日消息柱图、三条告警（待确认/失败 warn + 静默 info）、一个入口。 */
const OVERVIEW = {
  service: 'im',
  nodeKey: 'chuanxin-stage',
  title: '传信司',
  subtitle: '即时传信 · 会话与驿路',
  generatedAt: '2026-11-23T01:30:00+08:00',
  state: 'live',
  kpis: [
    { key: 'conversations', label: '会话总数', value: 12, format: 'count', tone: 'flat', hint: '单聊 8 · 机器人 2' },
    { key: 'messages-today', label: '今日消息', value: 40, format: 'count', tone: 'good', hint: '近七日 210 条' },
    { key: 'silent', label: '静默会话', value: 3, format: 'count', tone: 'warn', hint: '七日没有新消息' },
    { key: 'pending-confirm', label: '待确认', value: 2, format: 'count', tone: 'warn', hint: '机器人在等人工点头' },
  ],
  charts: [
    {
      key: 'message-trend',
      title: '近七日消息量',
      type: 'bar',
      format: 'count',
      categories: ['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23'],
      series: [{ name: '消息数', values: [20, 30, 25, 35, 40, 20, 40] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 2 次机器人运行在等人工确认，写操作不会自己往下走', count: null },
    { level: 'warning', text: '近七日有 1 次智能体运行失败，用户那边只看到一句「助手开小差」', count: null },
    { level: 'info', text: '有 3 个会话七日没有新消息，可能是群人散了，也可能是消息没送到', count: null },
  ],
  links: [
    { key: 'conversations', label: '会话列表', path: '/im/v1/conversations', permission: 'sys:im:chat' },
  ],
  notes: ['口径：会话按最后活跃时间判今日/静默（七日）；消息只数正常状态，撤回的不算；机器人两笔账只看待确认与近七日失败'],
};

/**
 * 9 个会话 = 3 页（3 + 3 + 3）。刻意安排：
 *  · 第 2 个是单聊且 title 为 null → 走列级 emptyText 显示"未命名会话"；
 *  · 第 6 个 lastMessage 为 null → "最后一句"显示"还没有消息"；
 *  · 三类型各至少一条，验证字典（单聊灰 / 群聊绿 / 机器人黄）。
 */
const CONVERSATIONS = [
  {
    id: '9001', type: 'GROUP', title: '外城驿路改造群', ownerId: '1001', lastSeq: 42, unread: 3,
    lastActiveAt: '2026-11-23T00:12:00+08:00',
    lastMessage: { senderType: 'USER', content: '改道走北线，明早动工', createTime: '2026-11-23T00:12:00+08:00' },
  },
  {
    id: '9002', type: 'SINGLE', title: null, ownerId: '1001', lastSeq: 17, unread: 2,
    lastActiveAt: '2026-11-22T21:40:00+08:00',
    lastMessage: { senderType: 'USER', content: '那份驿路图我看看', createTime: '2026-11-22T21:40:00+08:00' },
  },
  {
    id: '9003', type: 'BOT', title: '蓝鲸助手', ownerId: '1001', lastSeq: 9, unread: 0,
    lastActiveAt: '2026-11-22T20:05:00+08:00',
    lastMessage: { senderType: 'BOT', content: '已为你整理三份材料', createTime: '2026-11-22T20:05:00+08:00' },
  },
  {
    id: '9004', type: 'GROUP', title: '夜巡值房', ownerId: '1001', lastSeq: 88, unread: 0,
    lastActiveAt: '2026-11-22T18:30:00+08:00',
    lastMessage: { senderType: 'USER', content: '今夜无事', createTime: '2026-11-22T18:30:00+08:00' },
  },
  {
    id: '9005', type: 'SINGLE', title: null, ownerId: '1001', lastSeq: 5, unread: 0,
    lastActiveAt: '2026-11-22T09:15:00+08:00',
    lastMessage: { senderType: 'USER', content: '收到', createTime: '2026-11-22T09:15:00+08:00' },
  },
  {
    id: '9006', type: 'GROUP', title: '新坊筹备', ownerId: '1001', lastSeq: 0, unread: 0,
    lastActiveAt: '2026-11-20T10:00:00+08:00',
    lastMessage: null,
  },
  {
    id: '9007', type: 'SINGLE', title: null, ownerId: '1001', lastSeq: 3, unread: 0,
    lastActiveAt: '2026-11-19T16:20:00+08:00',
    lastMessage: { senderType: 'USER', content: '明日再议', createTime: '2026-11-19T16:20:00+08:00' },
  },
  {
    id: '9008', type: 'GROUP', title: '影戏院排片', ownerId: '1001', lastSeq: 26, unread: 1,
    lastActiveAt: '2026-11-18T11:00:00+08:00',
    lastMessage: { senderType: 'USER', content: '申时加一场', createTime: '2026-11-18T11:00:00+08:00' },
  },
  {
    id: '9009', type: 'SINGLE', title: null, ownerId: '1001', lastSeq: 1, unread: 0,
    lastActiveAt: '2026-11-17T08:00:00+08:00',
    lastMessage: { senderType: 'USER', content: '好', createTime: '2026-11-17T08:00:00+08:00' },
  },
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

/** 记录会话列表真实发出的查询参数：clientPaged 页面不该带 current/size */
const queried = [];
const overviewHits = [];

await page.route('**/im/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 后端契约就是"整份数组"：不切页、不带 total。前端若按 {records,total} 解，
   会得到 0 行——这条路由替身按真实形状返回，才能验出本地切页是否生效。 */
await page.route('**/im/v1/conversations**', async (route) => {
  const url = new URL(route.request().url());
  queried.push({ names: [...url.searchParams.keys()], path: url.pathname });
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(CONVERSATIONS)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是会话列表 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="chuanxin"]');
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
check('传信司屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 chuanxin', mounted?.id === 'chuanxin', mounted?.id);
check('大屏规格（贴脸读会话要 620px 高）', mounted?.size === 'large', mounted?.size);
check('题头是传信司 · 会话列表', String(mounted?.title).includes('会话列表'), mounted?.title);
check('入口标签跟概览 links 走：只有一个"会话列表"',
  mounted?.tabs?.length === 1 && mounted.tabs[0] === '会话列表', mounted?.tabs);
const tabDisabled = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="chuanxin"] .bwsp-tab')].map((tab) => tab.disabled),
);
check('唯一入口可点（不是禁用标签）', tabDisabled.length === 1 && tabDisabled[0] === false, tabDisabled);

/* ---- 2. 概览走 /im/overview ---- */
check('概览请求打到 /im/overview',
  overviewHits.length > 0 && overviewHits.every((p) => p === '/im/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（会话总数/今日消息/静默会话/待确认）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（近七日消息量）', probe?.overview.charts === 1, probe?.overview.charts);
check('三条告警（待确认/失败两黄 + 静默一提示）', probe?.overview.alerts === 3, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="chuanxin"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('会话 12 个、灰灯（只报数不喊事）', kpiCards[0]?.value === '12' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('会话牌写出单聊与机器人拆分（单聊 8 · 机器人 2）',
  String(kpiCards[0]?.label).includes('单聊 8 · 机器人 2'), kpiCards[0]?.label);
check('今日消息 40、绿灯（有消息不是欠账）',
  kpiCards[1]?.value === '40' && kpiCards[1]?.tone === 'good', kpiCards[1]);
check('今日消息牌带近七日总量', String(kpiCards[1]?.label).includes('近七日 210 条'), kpiCards[1]?.label);
check('静默会话 3 个、黄灯（七日没人说话）',
  kpiCards[2]?.value === '3' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('待确认 2 次、黄灯（写操作停在半路）',
  kpiCards[3]?.value === '2' && kpiCards[3]?.tone === 'warn', kpiCards[3]);

/* ---- 3. 七日柱图：七根、标签是月-日 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="chuanxin"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  return { bars: rows.length, labels };
});
check('柱图七根柱（一屏放得下，不需要横向滚动）', barChart.bars === 7, barChart);
check('七根柱子按日期升序（11-17 → 11-23）',
  JSON.stringify(barChart.labels) === JSON.stringify(['11-17', '11-18', '11-19', '11-20', '11-21', '11-22', '11-23']),
  barChart.labels);

/* ---- 4. 会话列表：列、字典、嵌套字段、本地切页 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="chuanxin"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        title: cells[0]?.textContent?.trim() ?? '',
        type: cells[1]?.textContent?.trim() ?? '',
        typeTone: cells[1]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        last: cells[2]?.textContent?.trim() ?? '',
        unread: cells[3]?.textContent?.trim() ?? '',
        active: cells[4]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 3 行（本地切页按 pageSize 切）', rows.length === 3, rows.length);
check('首行是外城驿路改造群', rows[0]?.title === '外城驿路改造群', rows[0]?.title);
check('群聊绿标（类型走字典）', rows[0]?.type === '群聊' && rows[0]?.typeTone === 'good', rows[0]);
check('"最后一句"取嵌套字段 lastMessage.content', rows[0]?.last === '改道走北线，明早动工', rows[0]?.last);
check('未读数照原样（3）', rows[0]?.unread === '3', rows[0]?.unread);
check('单聊标题为空时显示"未命名会话"（列级 emptyText，不是裸破折号）',
  rows[1]?.title === '未命名会话', rows[1]?.title);
check('单聊灰标', rows[1]?.type === '单聊' && rows[1]?.typeTone === 'flat', rows[1]);
check('机器人会话黄标（自动应答要一眼分得出来）',
  rows[2]?.type === '机器人' && rows[2]?.typeTone === 'warn', rows[2]);
check('最后活跃走 datetime 口径（MM-DD HH:mm）', /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.active ?? ''), rows[0]?.active);

/* clientPaged：请求不该带 current/size，翻页只切数组 */
const listCalls = queried.filter((item) => item.path === '/im/v1/conversations');
check('会话列表请求打到了 /im/v1/conversations', listCalls.length > 0, queried.slice(0, 2));
check('本地分页的接口不带 current/size（服务端也不认这两个参数）',
  listCalls.every((call) => !call.names.includes('current') && !call.names.includes('size')),
  listCalls.map((call) => call.names));
check('首屏记录到 9 条（整份数组一次取回）', probe?.list.total === 9, probe?.list);



/* ---- 5. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ----
   注意不要先来一次 __bwwWalkTo(0,0)"热个身"：走位是"设一个目标、逐帧走过去"，
   连续两次调用会让玩家停在第一个目标，而屏风用 dispatchEvent 强制点击照样能进——
   走位失败会被完全盖住。 */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="chuanxin"]');
  /* 关键：`bws-screen` 元素**自身**的 matrix3d 才是世界变换（v[12]/v[14] 是世界
     x/z）；parentElement 是 CSS3DRenderer 的相机透视层，那层的 v[12]/v[14] 是
     相机坐标——从它解位姿会得到一个看着合理但方向全错的位置。 */
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
/* 传信司在西北外城（x≈-40, z≈-74）。若解出的 x≈0，说明误取了相机透视层的
   transform——那条路会让玩家走错方向，必须当场拦住。 */
check('位姿来自世界层而非相机层（传信司在西北外城 x<-30 且 z<-60）',
  pose?.x < -30 && pose?.z < -60, pose);
await page.evaluate(({ pose }) => {
  const px = pose.x + 4.5 * pose.nx;
  const pz = pose.z + 4.5 * pose.nz;
  window.__bwwWalkTo(px, pz);
  window.__bwwLook(Math.atan2(pose.nx, pose.nz), 0.18, 9);
}, { pose });
await page.waitForTimeout(18000);
/* 不用 locator.click：走到跟前时屏风可能落在小地图面板底下（hit-target 被拦），
   而且屏风带 3D 变换，包围盒中心未必落在元素上。点击入口是 screen.el 上的
   click 监听（与前几坊同一套），直接派发 click 事件走同一逻辑。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="chuanxin"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="chuanxin"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="chuanxin"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'chuanxin', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 走位必须真的到了屏风前：玩家与屏风落点的距离要落在"能进门"的范围内，
   否则 dispatchEvent 会把走位失败盖过去（影戏院就是被这个坑盖了半天）。 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const gap = pose && playerAt ? Math.round(Math.hypot(pose.x - playerAt.x, pose.z - playerAt.z)) : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 屏风 (${pose?.x}, ${pose?.z}) 距离 ${gap}m`);
check('走位真的走到了屏风前（距离 < 10 米）', gap !== null && gap < 10, { playerAt, pose, gap });
await page.screenshot({ path: `${OUT}/110-chuanxin-engaged.png` });

/* 分页在贴脸态下测：3D 里的屏风页被透视缩放过，页码按钮在视口外，
   Playwright 的 click 会一直"滚进视口"失败——翻页是贴脸态的操作。 */
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('第 2 页仍是 3 行、总数 9', probe?.list.page === 2 && probe?.list.rows === 3 && probe?.list.total === 9, probe?.list);
rows = await readRows();
check('第 2 页换了一批行（行首是夜巡值房）', rows[0]?.title === '夜巡值房' && rows[0]?.last === '今夜无事', rows[0]);
check('没有消息的会话显示"还没有消息"（不是空白）',
  rows[2]?.last === '还没有消息' && rows[2]?.title === '新坊筹备', rows[2]);
check('翻页没有再打一次列表接口（本地切页的负向验证）',
  queried.filter((item) => item.path === '/im/v1/conversations').length === listCalls.length,
  queried.length);

await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('第 3 页 3 行（9 条 / 3 行）', probe?.list.page === 3 && probe?.list.rows === 3, probe?.list);
check('末页下一页禁用', (await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last().isDisabled()) === true);
/* 从第 3 页回第 1 页要按两次：一次只退一页——这也是"本地切页按页切片"的
   正向证据，若两次都停在同页说明页码没生效。 */
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('上一页退到第 2 页', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('再退一页回到第 1 页', probe?.list.page === 1 && probe?.list.rows === 3, probe?.list);

/* ---- 6. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（后端也没有按会话取详情的接口）',
  queried.every((item) => item.path === '/im/v1/conversations'), queried.map((item) => item.path));
check('详情 9 个字段用列表行数据', probe?.detail.fields === 9 && probe?.detail.lines === 0, probe?.detail);
check('详情标题取 titleKey（群名）', probe?.detail.title === '外城驿路改造群', probe?.detail.title);
const detailValues = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="chuanxin"] .bwsp-kv dd')].map((dd) => dd.textContent?.trim() ?? ''),
);
check('详情里的"最后一句"也走嵌套字段', detailValues.includes('改道走北线，明早动工'), detailValues);
check('详情里"最后发言"走字典显示中文（不是裸的 USER）', detailValues.includes('用户'), detailValues);
await page.screenshot({ path: `${OUT}/111-chuanxin-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('chuanxin'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 3, probe);

/* ---- 7. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="chuanxin"]');
  return {
    engaged: document.body.dataset.screenEngaged ?? null,
    focus: el?.dataset.focus ?? null,
    layer: el?.parentElement?.id ?? null,
    htmlClass: el?.className ?? '',
  };
});
check('Esc 退出屏风', exited.engaged === null, exited);
check('退出后恢复非贴脸态', exited.focus === 'false', exited.focus);
/* 退出判"回到哪一层"，不判像素尺寸：玩家就站在屏风前 2 米，回到 3D 后屏风在
   镜头里本来就会占掉大半（清议堂实测 3667×2161），拿像素阈值判"没退出"必然误报。 */
check('退出后回到 3D 层（parentElement 不再是平面化层 bww-planar）',
  exited.layer !== 'bww-planar', exited);
check('退出后屏风不再是贴脸样式', !String(exited.htmlClass).includes('bww-focus'), exited.htmlClass);

/* ---- 8. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/im/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/im/v1/conversations**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="chuanxin"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="chuanxin"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('chuanxin');
  const empty = document.querySelector('[data-screen-id="chuanxin"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="chuanxin"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/112-chuanxin-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
