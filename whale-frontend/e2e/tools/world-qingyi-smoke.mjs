// 清议堂帖子列表（S7 / T7.3）冒烟：外城九处的第三处，本坊特殊项是**帖子列表**。
//
// 与影戏院的分工：影戏院验"片子从上传到可播"，这一条验**公开议事的秩序**——
// 待审、举报、申诉三笔治理账，加上帖子五态与列表本身的筛选/分页/详情。
//
// 十件事：
//   ① 屏风挂的是帖子列表页；概览下发三个入口（帖子列表 / 审核队列 / 举报工单），
//      本地只配了帖子列表，另外两个必须渲染成**禁用标签**（不是消失，也不是能点）；
//   ② 四张牌走 /forum/overview：在议帖灰灯只报数，待审 / 举报 / 申诉三张有账就黄；
//   ③ 柱子是帖子**五态**分布（待审/在议/已解决/已关闭/已驳回），不是折线；
//   ④ 列表下拉框选的是**排序不是状态**：公开列表按浏览，没有状态维度，
//      statusParam 指向 sort（最新 / 热帖 / 精华），行上的状态灯另走 statusMap；
//   ⑤ 关键字发 keyword（模糊标题）；
//   ⑥ 类型走**列级字典**（问答/投票/公告各有语义），状态走**页面级字典**（待审黄、驳回红）；
//   ⑦ 列表行即详情（不配 detailPath）：15 个字段用行数据平铺，不打详情接口；
//   ⑧ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑨ 分页 10 条 / 6 行一页 → 第 2 页 4 条，末页禁用下一页；
//   ⑩ 接口全挂时不白屏：标 stale + 收起表格给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="qingyi"] ';

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
/* 与 ForumOverviewProvider 的实际下发一致：四张 KPI（三张治理牌黄）、
   一张五态柱图、四条告警（待审/举报/申诉 warn + 封禁存量 info）、三个入口。 */
const OVERVIEW = {
  service: 'forum',
  nodeKey: 'qingyi-stage',
  title: '清议堂',
  subtitle: '论坛议事 · 议事与秩序',
  generatedAt: '2026-11-23T00:10:00+08:00',
  state: 'live',
  kpis: [
    { key: 'published', label: '在议帖子', value: 12, format: 'count', tone: 'flat', hint: '悬榜 1 · 题金榜 2 · 已解决 3' },
    { key: 'pending', label: '待审帖子', value: 2, format: 'count', tone: 'warn', hint: '作者交了稿，等着放行' },
    { key: 'reports', label: '待受理举报', value: 4, format: 'count', tone: 'warn', hint: '含处理中，SLA 24h，超时升级' },
    { key: 'appeals', label: '待理申诉', value: 1, format: 'count', tone: 'warn', hint: '作者对驳回或处置不服，等复核' },
  ],
  charts: [
    {
      key: 'thread-status',
      title: '帖子状态分布',
      type: 'bar',
      format: 'count',
      categories: ['待审', '在议', '已解决', '已关闭', '已驳回'],
      series: [{ name: '帖子数', values: [2, 12, 3, 2, 1] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 2 条帖子待审，作者等着放行', count: null },
    { level: 'warning', text: '有 4 张举报待受理，SLA 24 小时，超时会被升级', count: null },
    { level: 'warning', text: '有 1 条申诉待理，作者对驳回或处置不服，等复核', count: null },
    { level: 'info', text: '当前有 3 名用户处于禁言或封号，解除走处置流程', count: null },
  ],
  links: [
    { key: 'threads', label: '帖子列表', path: '/forum/v1/thread/page', permission: 'sys:forum:browse' },
    { key: 'audit', label: '审核队列', path: '/forum/v1/admin/audit/page', permission: 'sys:forum:manage:audit' },
    { key: 'reports', label: '举报工单', path: '/forum/v1/admin/report/page', permission: 'sys:forum:manage:dispose' },
  ],
  notes: ['口径：待办只数未出结论的一段（待审帖 / 待受理与处理中举报 / 待理申诉）；封禁是处置结果，只报存量'],
};

/**
 * 10 条帖子：五态 × 六类型全覆盖。4 行/页 → 3 页。
 * 第 4 条是"待审 + 一条回复都没有"的巡夜帖：待审黄灯与"最后回复给破折号"
 * 两条断言都落在第一页末行（也是表格下缘那一行——正好顺带证明不被切掉）。
 * 第 5 条（第 2 页首行）是开坊公告，验证分页真的翻过去了。
 * keyword=夜 命中三条（外城夜市 / 夜场排期 / 巡夜人手）——"巡夜"也带夜字，
 * 正好证明搜索是模糊匹配而不是前缀匹配。
 */
const THREADS = [
  { id: 70001, threadNo: 'FM-20261120-0001', boardId: 12, threadType: 'NORMAL', title: '外城夜市该不该开到子时', status: 'PUBLISHED', pinned: 1, essence: 0, replyCount: 36, viewCount: 1280, likeCount: 88, collectCount: 24, lastReplyTime: '2026-11-21 10:20:00', publishTime: '2026-11-20 21:00:00' },
  { id: 70002, threadNo: 'FM-20261119-0002', boardId: 12, threadType: 'QA', title: '外城驿路怎么走最省时', status: 'SOLVED', pinned: 0, essence: 0, replyCount: 18, viewCount: 960, likeCount: 42, collectCount: 12, lastReplyTime: '2026-11-20 09:00:00', publishTime: '2026-11-19 08:30:00' },
  { id: 70003, threadNo: 'FM-20261118-0003', boardId: 13, threadType: 'VOTE', title: '清议堂该不该设常驻议事', status: 'PUBLISHED', pinned: 0, essence: 1, replyCount: 54, viewCount: 2100, likeCount: 130, collectCount: 60, lastReplyTime: '2026-11-19 22:10:00', publishTime: '2026-11-18 20:00:00' },
  { id: 70006, threadNo: 'FM-20261115-0006', boardId: 12, threadType: 'NORMAL', title: '外城巡夜人手够不够', status: 'PENDING', pinned: 0, essence: 0, replyCount: 0, viewCount: 0, likeCount: 0, collectCount: 0, lastReplyTime: null, publishTime: '2026-11-15 10:00:00' },
  { id: 70004, threadNo: 'FM-20261117-0004', boardId: 10, threadType: 'NOTICE', title: '外城九处开坊公告', status: 'PUBLISHED', pinned: 1, essence: 1, replyCount: 7, viewCount: 3400, likeCount: 210, collectCount: 88, lastReplyTime: '2026-11-18 12:00:00', publishTime: '2026-11-17 09:00:00' },
  { id: 70005, threadNo: 'FM-20261116-0005', boardId: 13, threadType: 'NORMAL', title: '影戏院的夜场排期', status: 'PUBLISHED', pinned: 0, essence: 0, replyCount: 12, viewCount: 720, likeCount: 30, collectCount: 9, lastReplyTime: '2026-11-17 18:40:00', publishTime: '2026-11-16 16:00:00' },
  { id: 70007, threadNo: 'FM-20261114-0007', boardId: 11, threadType: 'ARTICLE', title: '外城风物志（上）', status: 'PUBLISHED', pinned: 0, essence: 1, replyCount: 41, viewCount: 1860, likeCount: 96, collectCount: 52, lastReplyTime: '2026-11-16 08:20:00', publishTime: '2026-11-14 07:00:00' },
  { id: 70008, threadNo: 'FM-20261113-0008', boardId: 13, threadType: 'ACTIVITY', title: '秋祭集会报名', status: 'PUBLISHED', pinned: 0, essence: 0, replyCount: 63, viewCount: 1540, likeCount: 74, collectCount: 33, lastReplyTime: '2026-11-15 20:00:00', publishTime: '2026-11-13 19:00:00' },
  { id: 70009, threadNo: 'FM-20261112-0009', boardId: 12, threadType: 'QA', title: '坊墙为什么不设西门', status: 'REJECTED', pinned: 0, essence: 0, replyCount: 3, viewCount: 210, likeCount: 4, collectCount: 1, lastReplyTime: '2026-11-13 11:00:00', publishTime: '2026-11-12 09:00:00' },
  { id: 70010, threadNo: 'FM-20261111-0010', boardId: 12, threadType: 'NORMAL', title: '外城排污怎么改', status: 'CLOSED', pinned: 0, essence: 0, replyCount: 22, viewCount: 880, likeCount: 51, collectCount: 14, lastReplyTime: '2026-11-14 15:30:00', publishTime: '2026-11-11 08:00:00' },
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

/** 记录列表真实发出的查询参数：关键字叫 keyword，排序叫 sort */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/forum/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   清议堂不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/forum/v1/thread/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/forum/v1/thread/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const sort = url.searchParams.get('sort');
  queried.push({
    current,
    size,
    keyword,
    sort,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：keyword 按标题模糊匹配；sort 只改顺序不改集合（latest/hot/essence） */
  const matched = THREADS.filter((row) => !keyword || row.title.includes(keyword));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是帖子列表 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qingyi"]');
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
check('清议堂屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 qingyi', mounted?.id === 'qingyi', mounted?.id);
check('大屏规格（八列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是清议堂 · 帖子列表', String(mounted?.title).includes('帖子列表'), mounted?.title);
check('入口标签跟概览 links 走：帖子列表 / 审核队列 / 举报工单三个',
  mounted?.tabs?.length === 3
  && mounted.tabs[0] === '帖子列表' && mounted.tabs[1] === '审核队列' && mounted.tabs[2] === '举报工单',
  mounted?.tabs);
const otherTabs = await page.evaluate(() => {
  const tabs = [...document.querySelectorAll('[data-screen-id="qingyi"] .bwsp-tab')];
  return tabs.filter((tab) => tab.textContent !== '帖子列表').map((tab) => tab.disabled);
});
check('审核队列与举报工单是禁用标签（屏风只挂帖子主账）',
  otherTabs.length === 2 && otherTabs.every((flag) => flag === true), otherTabs);

/* ---- 2. 概览走 /forum/overview ---- */
check('概览请求打到 /forum/overview',
  overviewHits.length > 0 && overviewHits.every((p) => p === '/forum/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在议/待审/待受理举报/待理申诉）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（帖子状态分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警（三黄治理待办 + 一条封禁存量）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="qingyi"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在议 12 条、灰灯（只报数不喊事）', kpiCards[0]?.value === '12' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('在议牌写出议事成色（悬榜 1 · 题金榜 2 · 已解决 3）',
  String(kpiCards[0]?.label).includes('悬榜 1') && String(kpiCards[0]?.label).includes('题金榜'), kpiCards[0]?.label);
check('待审 2 条、黄灯（作者等着放行）',
  kpiCards[1]?.value === '2' && kpiCards[1]?.tone === 'warn'
  && String(kpiCards[1]?.label).includes('等着放行'), kpiCards[1]);
check('待受理举报 4 张、黄灯（SLA 24h）',
  kpiCards[2]?.value === '4' && kpiCards[2]?.tone === 'warn'
  && String(kpiCards[2]?.label).includes('SLA'), kpiCards[2]);
check('待理申诉 1 条、黄灯（作者等复核）',
  kpiCards[3]?.value === '1' && kpiCards[3]?.tone === 'warn'
  && String(kpiCards[3]?.label).includes('复核'), kpiCards[3]);

/* ---- 3. 状态分布柱图：五态分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="qingyi"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  return { bars: rows.length, labels };
});
check('柱图五根柱（五态全覆盖）', barChart.bars === 5, barChart);
check('五态标签是待审/在议/已解决/已关闭/已驳回',
  JSON.stringify(barChart.labels) === JSON.stringify(['待审', '在议', '已解决', '已关闭', '已驳回']), barChart.labels);

/* ---- 4. 帖子列表：列、字典、行数 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="qingyi"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        title: cells[0]?.textContent?.trim() ?? '',
        type: cells[1]?.textContent?.trim() ?? '',
        status: cells[2]?.textContent?.trim() ?? '',
        statusTone: cells[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        reply: cells[3]?.textContent?.trim() ?? '',
        view: cells[4]?.textContent?.trim() ?? '',
        like: cells[5]?.textContent?.trim() ?? '',
        publish: cells[6]?.textContent?.trim() ?? '',
        lastReply: cells[7]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 4 行（五态柱图吃掉约 0.7 行，排 5 行会被切半行）', rows.length === 4, rows.length);
check('首行标题是外城夜市该不该开到子时', rows[0]?.title === '外城夜市该不该开到子时', rows[0]?.title);
check('在议行绿灯、状态是在议', rows[0]?.status === '在议' && rows[0]?.statusTone === 'good', rows[0]);
check('类型列走列级字典（问答绿、投票绿）',
  rows[1]?.type === '问答' && rows[2]?.type === '投票', rows.map((row) => row.type));
check('待审行黄灯（不是红：欠账不是事故）', rows[3]?.status === '待审' && rows[3]?.statusTone === 'warn', rows[3]);
check('四个计数原样展示（回复/浏览/投花不做二次换算）',
  rows[1]?.reply === '18' && rows[1]?.view === '960' && rows[1]?.like === '42', rows[1]);
check('时间走 datetime 口径（MM-DD HH:mm）', /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.publish ?? ''), rows[0]?.publish);
/* 没回复过的帖 lastReplyTime 为 null：列表不能渲染成 1970 也不能空白无物，
   屏风统一给一个破折号 —— 这是"没有这一项"，不是"取值失败"。 */
check('没有回复的帖在"最后回复"列给破折号（不是 1970、不是空白）',
  rows[3]?.lastReply === '—', rows[3]?.lastReply);

/* ---- 5. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ----
   注意不要先来一次 __bwwWalkTo(0,0)"热个身"：走位是"设一个目标、逐帧走过去"，
   连续两次调用会让玩家停在第一个目标，而屏风用 dispatchEvent 强制点击照样能进——
   走位失败会被完全盖住。 */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qingyi"]');
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
/* 清议堂在正东偏北的外城（x>80, z<0）。若解出的 x≈0，说明误取了相机透视层的
   transform——那条路会让玩家走错方向，必须当场拦住。 */
check('位姿来自世界层而非相机层（清议堂在正东偏北 x>75 且 z<-12）',
  pose?.x > 75 && pose?.z < -12, pose);
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
  const el = document.querySelector('[data-screen-id="qingyi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="qingyi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="qingyi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'qingyi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 走位必须真的到了屏风前：玩家与屏风落点的距离要落在"能进门"的范围内，
   否则 dispatchEvent 会把走位失败盖过去（影戏院就是被这个坑盖了半天）。 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const gap = pose && playerAt ? Math.round(Math.hypot(pose.x - playerAt.x, pose.z - playerAt.z)) : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 屏风 (${pose?.x}, ${pose?.z}) 距离 ${gap}m`);
check('走位真的走到了屏风前（距离 < 10 米）', gap !== null && gap < 10, { playerAt, pose, gap });
await page.screenshot({ path: `${OUT}/97-qingyi-engaged.png` });

/* ---- 6. 关键字搜索：发 keyword=夜 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('夜');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
const lastSearch = queried.filter((item) => item.keyword === '夜').pop();
check('关键字按 keyword=夜 发给服务端（模糊标题）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 3 条（夜市 / 夜场排期 / 巡夜人手）',
  probe?.list.total === 3 && probe?.list.rows === 3, probe?.list);
rows = await readRows();
check('命中的是标题真带夜的三条', rows.every((row) => row.title.includes('夜')), rows.map((row) => row.title));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 7. 排序下拉：发 sort=hot（这个控件选的是顺序，不是状态） ---- */
const sortOptions = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="qingyi"] .bwsp-status option')].map((opt) => ({
    value: opt.value,
    label: opt.textContent,
  })),
);
check('下拉框是排序三档（最新回帖 / 热帖 / 精华），没有"全部状态"这种伪状态',
  sortOptions.length === 3 && sortOptions[0].label.includes('最新')
  && sortOptions[1].value === 'hot' && sortOptions[2].value === 'essence',
  sortOptions);
await page.locator(SCOPE + '.bwsp-status').selectOption('hot');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
const lastSort = queried.filter((item) => item.sort === 'hot').pop();
check('排序按 sort=hot 发给服务端（独立参数不并进 keyword）', Boolean(lastSort), queried.slice(-3));
check('排序只换顺序不换集合（仍是 10 条）', probe?.list.total === 10, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 8. 分页：第 2 页 4 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
check('第 2 页剩 4 条', probe?.list.page === 2 && probe?.list.rows === 4, probe?.list);
check('第 2 页还有下一页（不是末页）', (await nextBtn.isDisabled()) === false);
rows = await readRows();
check('第 2 页第一行是外城九处开坊公告（page 参数真的生效了）',
  rows[0]?.title === '外城九处开坊公告', rows[0]?.title);
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
check('第 3 页剩 2 条', probe?.list.page === 3 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 3 页第一行是坊墙为什么不设西门', rows[0]?.title === '坊墙为什么不设西门', rows[0]?.title);
check('已驳回行红灯（审核不通过，作者可能来申诉）',
  rows[0]?.status === '已驳回' && rows[0]?.statusTone === 'bad', rows[0]);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（ThreadListItemVO 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 15 个字段用列表行数据',
  probe?.detail.fields === 15 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/98-qingyi-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qingyi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 4, probe);

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qingyi"]');
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
   镜头里本来就会占掉大半（实测 3667×2161），拿像素阈值判"没退出"必然误报
   （影戏院那条 1000×700 只是因为那时相机恰好退得远）。层不对才是真没退出。 */
check('退出后回到 3D 层（parentElement 不再是平面化层 bww-planar）',
  exited.layer !== 'bww-planar', exited);
check('退出后屏风不再是贴脸样式', !String(exited.htmlClass).includes('bww-focus'), exited.htmlClass);

/* ---- 11. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/forum/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/forum/v1/thread/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qingyi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="qingyi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('qingyi');
  const empty = document.querySelector('[data-screen-id="qingyi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="qingyi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/99-qingyi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
