// 影戏院内容台账（S7 / T7.2）冒烟：外城九处的第二处，本坊特殊项是**视频纹理**。
//
// 与乐坊的分工：乐坊验曲库流水线，这一条验**片子从上传到可播、再到授权失效的全程**
// ——待审、上线、评论、版权到期这条链，2D 后台要切内容页、评论审核页、
// 电影授权页三处才看得全。
//
// 十一件事：
//   ① 幕布是**真视频纹理**：canvas 实时皮影戏经 captureStream 灌进 <video>，
//      VideoTexture 逐帧上传；探针读帧计数在涨，读 mode 确认走的是真视频那条路；
//   ② 走远即停（负向验证）：出生点离影戏院约 93 米，幕布必须 near=false 且已暂停，
//      否则"在放"就是无条件常开，等于没接线；
//   ③ 概览四张牌走 /video/overview，三张运营牌有账就黄；
//   ④ 内容发布四态画成**柱图不是折线**：码值固定（DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE）；
//   ⑤ 内容台账有搜索框（keyword 模糊片名）+ 状态筛选（publishState 独立参数）；
//   ⑥ 发布态用**页面级字典**（待审黄、已发布绿、草稿/下线灰），
//      类型与版权用**列级字典**（电影、短视频、已下架各归各位）；
//   ⑦ 列表行即详情（不配 detailPath）：ContentView 平铺，行内字段够详情用；
//   ⑧ 收录/上架时间走 datetime 口径（MM-DD HH:mm）；
//   ⑨ 概览入口两个（内容台账 + 电影台账），屏风页挂内容主账；
//   ⑩ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑪ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="yingxi"] ';

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
/* 与 VideoOverviewProvider 的实际下发一致：四张 KPI（三张运营牌黄）、
   一张四态柱图、四条告警（待审/评论/授权 warn + 下架存量 info）、两个入口。 */
const OVERVIEW = {
  service: 'video',
  nodeKey: 'yingxi-stage',
  title: '影戏院',
  subtitle: '内容运营 · 内容流水线',
  generatedAt: '2026-11-22T21:10:00+08:00',
  state: 'live',
  kpis: [
    { key: 'published', label: '已发布内容', value: 30, format: 'count', tone: 'flat', hint: '草稿 4 · 已下线 3 · 累计播放 128000' },
    { key: 'pending-review', label: '待审内容', value: 2, format: 'count', tone: 'warn', hint: '送审中，等着运营过目' },
    { key: 'comment-pending', label: '待审评论', value: 6, format: 'count', tone: 'warn', hint: '压着没放行，观众等不到自己的留言' },
    { key: 'license-expiring', label: '授权将到期', value: 2, format: 'count', tone: 'warn', hint: '7 天内到期，不续签就得下线' },
  ],
  charts: [
    {
      key: 'content-status',
      title: '内容发布状态分布',
      type: 'bar',
      format: 'count',
      categories: ['草稿', '待审', '已发布', '已下线'],
      series: [{ name: '内容数', values: [4, 2, 30, 3] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 2 部内容待审，投稿人等着放行', count: null },
    { level: 'warning', text: '有 6 条评论待审，压着没放行，观众等不到自己的留言', count: null },
    { level: 'warning', text: '有 2 部电影授权 7 天内到期，不续签就得下线', count: null },
    { level: 'info', text: '存量 1 部版权下架内容，处置已出结论，如需恢复走版权复核流程', count: null },
  ],
  links: [
    { key: 'contents', label: '内容台账', path: '/video/v1/admin/content/page', permission: 'sys:video:content:list' },
    { key: 'movies', label: '电影台账', path: '/video/v1/admin/movies/page', permission: 'sys:video:movie:manage' },
  ],
  notes: ['口径：待办只数未出结论的一段（待审内容 / 待审评论）；授权到期窗口 7 天；版权下架是处置结果不算欠账'],
};

/**
 * 10 部片子：四态 × 类型 × 版权全覆盖。6 行/页 → 2 页。
 * 第 7 部（第 2 页首行）叫"夜航"，验证分页真的翻过去了。
 * keyword=鲸 命中 1/2 两部；publishState=PUBLISHED 命中 2/3/5/7/9 五部。
 */
const CONTENTS = [
  { id: 50001, title: '鲸背上的城', subtitle: '纪录片 · 上', contentType: 'VIDEO', publishState: 'DRAFT', copyrightState: 'AUTHORIZED', visibility: 'PRIVATE', durationMs: 1840000, playCount: 0, likeCount: 0, favoriteCount: 0, commentCount: 0, createTime: '2026-11-21 09:30:00', publishedAt: null },
  { id: 50002, title: '鲸落', subtitle: '短片 · 实验', contentType: 'SHORT_VIDEO', publishState: 'PUBLISHED', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 62000, playCount: 12800, likeCount: 640, favoriteCount: 210, commentCount: 88, createTime: '2026-11-20 10:05:00', publishedAt: '2026-11-20 15:00:00' },
  { id: 50003, title: '海上来客', subtitle: '电影 · 修复版', contentType: 'MOVIE', publishState: 'PUBLISHED', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 6480000, playCount: 5600, likeCount: 210, favoriteCount: 96, commentCount: 34, createTime: '2026-11-19 11:20:00', publishedAt: '2026-11-19 14:00:00' },
  { id: 50004, title: '夜市灯影', subtitle: '短片 · 街拍', contentType: 'SHORT_VIDEO', publishState: 'OFFLINE', copyrightState: 'TAKEDOWN', visibility: 'PUBLIC', durationMs: 48000, playCount: 3200, likeCount: 96, favoriteCount: 40, commentCount: 12, createTime: '2026-11-18 13:40:00', publishedAt: '2026-11-18 18:00:00' },
  { id: 50005, title: '归潮', subtitle: '电影 · 首映', contentType: 'MOVIE', publishState: 'PUBLISHED', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 7200000, playCount: 9900, likeCount: 480, favoriteCount: 300, commentCount: 61, createTime: '2026-11-17 15:10:00', publishedAt: '2026-11-17 20:00:00' },
  { id: 50006, title: '灯下客', subtitle: '短片 · 默片', contentType: 'SHORT_VIDEO', publishState: 'PENDING_REVIEW', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 55000, playCount: 0, likeCount: 0, favoriteCount: 0, commentCount: 0, createTime: '2026-11-16 08:45:00', publishedAt: null },
  { id: 50007, title: '夜航', subtitle: '纪录片 · 下', contentType: 'VIDEO', publishState: 'PUBLISHED', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 2400000, playCount: 4300, likeCount: 190, favoriteCount: 88, commentCount: 27, createTime: '2026-11-15 09:00:00', publishedAt: '2026-11-15 12:00:00' },
  { id: 50008, title: '旧码头', subtitle: '短片 · 黑白', contentType: 'SHORT_VIDEO', publishState: 'OFFLINE', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 72000, playCount: 7800, likeCount: 350, favoriteCount: 120, commentCount: 55, createTime: '2026-11-14 16:30:00', publishedAt: '2026-11-14 19:00:00' },
  { id: 50009, title: '潮汐表', subtitle: '电影 · 重映', contentType: 'MOVIE', publishState: 'PUBLISHED', copyrightState: 'AUTHORIZED', visibility: 'PUBLIC', durationMs: 6900000, playCount: 21000, likeCount: 1200, favoriteCount: 700, commentCount: 156, createTime: '2026-11-13 10:20:00', publishedAt: '2026-11-13 13:00:00' },
  { id: 50010, title: '雾港', subtitle: '短片 · 试验', contentType: 'SHORT_VIDEO', publishState: 'DRAFT', copyrightState: 'AUTHORIZED', visibility: 'PRIVATE', durationMs: 39000, playCount: 0, likeCount: 0, favoriteCount: 0, commentCount: 0, createTime: '2026-11-12 14:00:00', publishedAt: null },
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

/** 记录列表真实发出的查询参数：关键字叫 keyword，发布态叫 publishState */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/video/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   影戏院不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/video/v1/admin/content/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/video/v1/admin/content/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? url.searchParams.get('pageNum') ?? '1');
  const size = Number(url.searchParams.get('size') ?? url.searchParams.get('pageSize') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const publishState = url.searchParams.get('publishState');
  queried.push({
    current,
    size,
    keyword,
    publishState,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：keyword 按片名模糊匹配，publishState 精确匹配（ContentQueryCommand） */
  const matched = CONTENTS.filter((row) => {
    const hitState = !publishState || row.publishState === publishState;
    const hitKeyword = !keyword || row.title.includes(keyword);
    return hitState && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 0. 幕布负向验证：出生点在城北，离正东外城影戏院约 93 米，
   幕布必须已停（near=false、playing=false），且帧计数不再增长——
   否则"走近才放"就是无条件常开，等于没接线。 ---- */
const farStage = await page.evaluate(() => window.__bwwStageScreen?.() ?? null);
check('幕布句柄存在（视频纹理已挂进场景）', Boolean(farStage), farStage);
check('出生点远离影戏院时不在放映范围内（负向验证）', farStage?.near === false, farStage);
check('远离时视频已暂停', farStage?.playing === false, farStage);
const farFrames = farStage?.frames ?? -1;
await page.waitForTimeout(1600);
const farFrames2 = (await page.evaluate(() => window.__bwwStageScreen?.() ?? null))?.frames ?? -1;
check('远离时帧计数停止增长（走远即停，不做无用重绘）', farFrames === farFrames2, { farFrames, farFrames2 });

/* ---- 1. 屏风立起来了，且挂的是内容台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingxi"]');
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
check('影戏院屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 yingxi', mounted?.id === 'yingxi', mounted?.id);
check('大屏规格（七列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是影戏院 · 内容台账', String(mounted?.title).includes('内容台账'), mounted?.title);
check('入口标签跟概览 links 走：内容台账 + 电影台账两个',
  mounted?.tabs?.length === 2 && mounted.tabs[0] === '内容台账' && mounted.tabs[1] === '电影台账', mounted?.tabs);
const movieDisabled = await page.evaluate(() => {
  const tabs = [...document.querySelectorAll('[data-screen-id="yingxi"] .bwsp-tab')];
  return tabs.find((tab) => tab.textContent === '电影台账')?.disabled ?? null;
});
check('电影台账标签是禁用的（屏风只挂内容主账）', movieDisabled === true, movieDisabled);

/* ---- 2. 概览走 /video/overview ---- */
check('概览请求打到 /video/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/video/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（已发布/待审内容/待审评论/授权到期）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（内容发布状态分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警（三黄催办 + 一条下架存量说明）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yingxi"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('已发布 30 部、灰灯（只报数不喊事）', kpiCards[0]?.value === '30' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('已发布牌写出架上成色（草稿 4 · 已下线 3 · 累计播放）',
  String(kpiCards[0]?.label).includes('草稿 4') && String(kpiCards[0]?.label).includes('累计播放'), kpiCards[0]?.label);
check('待审内容 2 部、黄灯（投稿人等着放行）',
  kpiCards[1]?.value === '2' && kpiCards[1]?.tone === 'warn'
  && String(kpiCards[1]?.label).includes('等着运营过目'), kpiCards[1]);
check('待审评论 6 条、黄灯（压着没放行）',
  kpiCards[2]?.value === '6' && kpiCards[2]?.tone === 'warn'
  && String(kpiCards[2]?.label).includes('观众等不到'), kpiCards[2]);
check('授权将到期 2 部、黄灯（7 天内不续签就下线）',
  kpiCards[3]?.value === '2' && kpiCards[3]?.tone === 'warn'
  && String(kpiCards[3]?.label).includes('7 天内'), kpiCards[3]);

/* ---- 3. 状态分布柱图：四态分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yingxi"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  return { bars: rows.length, labels };
});
check('柱图四根柱（四态全覆盖）', barChart.bars === 4, barChart);
check('四态标签是草稿/待审/已发布/已下线',
  JSON.stringify(barChart.labels) === JSON.stringify(['草稿', '待审', '已发布', '已下线']), barChart.labels);

/* ---- 4. 内容台账：列、字典、行数 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="yingxi"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        title: cells[0]?.textContent?.trim() ?? '',
        type: cells[1]?.textContent?.trim() ?? '',
        publish: cells[2]?.textContent?.trim() ?? '',
        publishTone: cells[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        copyright: cells[3]?.textContent?.trim() ?? '',
        play: cells[4]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 6 行', rows.length === 6, rows.length);
check('首行片名是鲸背上的城', rows[0]?.title === '鲸背上的城', rows[0]?.title);
check('草稿行灰灯、状态是草稿', rows[0]?.publish === '草稿' && rows[0]?.publishTone === 'flat', rows[0]);
check('类型列走列级字典（电影绿、短视频灰）',
  rows[1]?.type === '短视频' && rows[2]?.type === '电影', rows.map((row) => row.type));
check('版权列走列级字典（下架给红不给绿）',
  rows[3]?.copyright === '已下架', rows[3]?.copyright);
check('播放数原样展示（不做二次换算）', rows[1]?.play === '12800', rows[1]?.play);

/* ---- 5. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ----
   注意不要先来一次 __bwwWalkTo(0,0)"热个身"：走位是"设一个目标、逐帧走过去"，
   连续两次调用会让玩家停在第一个目标（实测停在城心 (0,0)，离影戏院 87 米），
   而屏风用 dispatchEvent 强制点击照样能进——走位失败被盖住，幕布断言才暴露。 */
/* 先看屏风在世界里的落点：必须来自 bws-screen 自身的 matrix3d（世界 x/z），
   不能解 parentElement——那是相机透视层，解出来的是相机坐标。 */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingxi"]');
  /* 关键：`bws-screen` 元素**自身**的 matrix3d 才是世界变换（v[12]/v[14] 是世界
     x/z）；parentElement 是 CSS3DRenderer 的相机透视层，那层的 v[12]/v[14] 是
     相机坐标——从它解位姿会得到一个看着合理但方向全错的位置（乐坊就解成
     (0,-76) 而真位在 (39.73,85.20)）。优先取自身 transform，parent 仅作回退。 */
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
/* 影戏院在正东外城（x>60, z>0）。若解出的 x≈0/z<0，说明误取了相机透视层的
   transform——那条路会让玩家走错方向、幕布恒在放映范围外，必须当场拦住。 */
check('位姿来自世界层而非相机层（影戏院在正东 x>60 且 z>10）',
  pose?.x > 60 && pose?.z > 10, pose);
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
  const el = document.querySelector('[data-screen-id="yingxi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="yingxi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="yingxi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'yingxi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6. 幕布正向验证：走近后真的在放（视频纹理这条线） ---- */
const nearStage = await page.evaluate(() => window.__bwwStageScreen?.() ?? null);
/* 诊断：把玩家坐标与幕布坐标一起打出来——near 判错时第一件事是确认人到底在哪 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const stageGap =
  nearStage && playerAt
    ? Math.round(Math.hypot(nearStage.x - playerAt.x, nearStage.z - playerAt.z))
    : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 幕布 (${nearStage?.x}, ${nearStage?.z}) 距离 ${stageGap}m videoError=${nearStage?.videoError}`);
check('走近后进入放映范围', nearStage?.near === true, { nearStage, playerAt, stageGap });
check('走近后视频在播', nearStage?.playing === true, nearStage);
check('幕布走真视频纹理（captureStream → VideoTexture）', nearStage?.mode === 'video', nearStage?.mode);
check('幕布画面尺寸 16:9（480×270）',
  nearStage?.width === 480 && nearStage?.height === 270, nearStage);
const nearFrames = nearStage?.frames ?? -1;
await page.waitForTimeout(1500);
const nearFrames2 = (await page.evaluate(() => window.__bwwStageScreen?.() ?? null))?.frames ?? -1;
check('走近后帧计数在涨（画面真的在动）', nearFrames2 > nearFrames, { nearFrames, nearFrames2 });
const stagePos = { x: nearStage?.x ?? 0, z: nearStage?.z ?? 0 };
check('幕布立在影戏院侧前方（世界坐标 x>70、z>20）',
  stagePos.x > 70 && stagePos.z > 20, stagePos);
await page.screenshot({ path: `${OUT}/94-yingxi-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=鲸 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('鲸');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
const lastSearch = queried.filter((item) => item.keyword === '鲸').pop();
check('关键字按 keyword=鲸 发给服务端（模糊片名）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 2 部（鲸背上的城 + 鲸落）',
  probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('命中的是片名真带鲸的两部', rows.every((row) => row.title.includes('鲸')), rows.map((row) => row.title));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 发布态筛选：发 publishState=PUBLISHED ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('PUBLISHED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
const lastStatus = queried.filter((item) => item.publishState === 'PUBLISHED').pop();
check('发布态按 publishState=PUBLISHED 发给服务端（独立参数不并进 keyword）', Boolean(lastStatus), queried.slice(-3));
check('已发布剩 5 部', probe?.list.total === 5 && probe?.list.rows === 5, probe?.list);
rows = await readRows();
check('命中的都是已发布（绿灯）',
  rows.every((row) => row.publish === '已发布' && row.publishTone === 'good'), rows.map((row) => row.publish));
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 4 部 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
check('第 2 页剩 4 部', probe?.list.page === 2 && probe?.list.rows === 4, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是夜航（page 参数真的生效了）',
  rows[0]?.title === '夜航', rows[0]?.title);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（ContentView 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 13 个字段用列表行数据',
  probe?.detail.fields === 13 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/95-yingxi-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yingxi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingxi"]');
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
/* 阈值给到 1000×700：贴脸态是 1440×900 级别的糊满，退出后是 600~900 的 3D 尺寸，
   两者差得足够远；写死 800 会把"相机离得稍近一点"误判成没退出（外城去掉坊墙后
   退出时的相机距离就变过一次）。状态本身由 engaged/focus 两条断言把住。 */
check('退出后回到 3D 尺寸（不是糊满整个镜头）', exited.w > 0 && exited.w < 1000 && exited.h < 700, exited);

/* ---- 12. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/video/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/video/v1/admin/content/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yingxi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="yingxi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('yingxi');
  const empty = document.querySelector('[data-screen-id="yingxi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="yingxi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/96-yingxi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
