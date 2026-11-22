// 乐坊歌曲台账（S7 / T7.1）冒烟：外城九处的第一处。
//
// 与内城十二坊的分工：翰林院验文档账、市舶司验生意流水，
// 这一条验**内容从上传到可播的流水线卡在哪一节**——版权复核、上架、
// 评论审核这条链，2D 后台要切曲库页、评论审核页、申诉页三处才看得全。
//
// 十件事：
//   ① 概览四张牌走 /music/overview，三张运营牌有账就黄；
//   ② 歌曲发布三态画成**柱图不是折线**：乐坊的账是存量不是流量，
//      三态码值固定（DRAFT/PUBLISHED/OFFLINE），屏风不译码；
//   ③ 歌曲台账**有搜索框**（keyword 模糊曲名）+ 状态筛选（publishState 独立参数）；
//   ④ 发布态用**页面级字典**（已上架绿、草稿/已下架灰），
//      版权态用**列级字典**（待复核黄、有效绿、下架灰）——两套码同名字段不同义；
//   ⑤ 列表行即详情（不配 detailPath）：SongView 平铺，行内字段够详情用；
//   ⑥ 收录/上架时间走 datetime 口径（MM-DD HH:mm）；
//   ⑦ 概览入口两个（歌曲台账 + 艺人台账），屏风页挂歌曲主账；
//   ⑧ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑨ 空间音频：靠近乐坊时琴音增益拉起，离开归零（探针验状态，不测听感）；
//   ⑩ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十一块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="yuefang"] ';

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
/* 与 MusicOverviewProvider 的实际下发一致：四张 KPI（三张运营牌黄）、
   一张三态柱图、四条告警（待审/申诉/版权复核 warn + 下架存量 info）、两个入口。 */
const OVERVIEW = {
  service: 'music',
  nodeKey: 'yuefang-stage',
  title: '乐坊',
  subtitle: '曲库运营 · 曲库三本账',
  generatedAt: '2026-11-22T08:30:00+08:00',
  state: 'live',
  kpis: [
    { key: 'published', label: '已上架歌曲', value: 18, format: 'count', tone: 'flat', hint: '草稿 6 · 已下架 2' },
    { key: 'comment-pending', label: '待审评论', value: 5, format: 'count', tone: 'warn', hint: '先审后发，压着没放行' },
    { key: 'appeal-pending', label: '待复核申诉', value: 3, format: 'count', tone: 'warn', hint: '对下架不服，等着复核' },
    { key: 'copyright-declared', label: '版权待复核', value: 4, format: 'count', tone: 'warn', hint: '协议已签，等运营过目' },
  ],
  charts: [
    {
      key: 'song-status',
      title: '歌曲发布状态分布',
      type: 'bar',
      format: 'count',
      categories: ['草稿', '已上架', '已下架'],
      series: [{ name: '歌曲数', values: [6, 18, 2] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 5 条评论待审，先审后发模式下听众等不到留言放行', count: null },
    { level: 'warning', text: '有 3 单申诉待复核，上传者对版权下架不服，要人给说法', count: null },
    { level: 'warning', text: '有 4 支曲子版权待复核，协议已签但没过目，上架被卡在这一节', count: null },
    { level: 'info', text: '存量 2 支版权下架曲子，处置已出结论，如需翻案走申诉流程', count: null },
  ],
  links: [
    { key: 'songs', label: '歌曲台账', path: '/music/v1/song/page', permission: 'sys:music:song:list' },
    { key: 'artists', label: '艺人台账', path: '/music/v1/artist/page', permission: 'sys:music:artist:list' },
  ],
  notes: ['口径：待办只数未出结论的一段（待审评论 / 待复核申诉）；版权下架是处置结果不算欠账'],
};

/**
 * 10 首曲子：发布三态 × 版权三态全覆盖。6 行/页 → 2 页。
 * 第 7 首（第 2 页首行）叫"云深处"，验证分页真的翻过去了。
 * keyword=云 命中 6/7 两首；publishState=PUBLISHED 命中 2/3/5/7/9 五首。
 */
const SONGS = [
  { id: 40001, title: '春风序', subtitle: '国风 · 序曲', language: '普通话', publishState: 'DRAFT', copyrightState: 'DECLARED', playCount: 0, likeCount: 0, commentCount: 0, createTime: '2026-11-21 09:30:00', publishedAt: null },
  { id: 40002, title: '长安夜', subtitle: '国风 · 夜曲', language: '普通话', publishState: 'PUBLISHED', copyrightState: 'VALID', playCount: 12800, likeCount: 640, commentCount: 88, createTime: '2026-11-20 10:05:00', publishedAt: '2026-11-20 15:00:00' },
  { id: 40003, title: '关山月', subtitle: '古琴 · 独奏', language: '纯音乐', publishState: 'PUBLISHED', copyrightState: 'DECLARED', playCount: 5600, likeCount: 210, commentCount: 34, createTime: '2026-11-19 11:20:00', publishedAt: '2026-11-19 14:00:00' },
  { id: 40004, title: '渭城曲', subtitle: '送别 · 改编', language: '普通话', publishState: 'OFFLINE', copyrightState: 'TAKEDOWN', playCount: 3200, likeCount: 96, commentCount: 12, createTime: '2026-11-18 13:40:00', publishedAt: '2026-11-18 18:00:00' },
  { id: 40005, title: '长相思', subtitle: '词牌 · 清唱', language: '普通话', publishState: 'PUBLISHED', copyrightState: 'VALID', playCount: 9900, likeCount: 480, commentCount: 61, createTime: '2026-11-17 15:10:00', publishedAt: '2026-11-17 20:00:00' },
  { id: 40006, title: '云想衣裳', subtitle: '国风 · 花想容', language: '普通话', publishState: 'DRAFT', copyrightState: 'VALID', playCount: 0, likeCount: 0, commentCount: 0, createTime: '2026-11-16 08:45:00', publishedAt: null },
  { id: 40007, title: '云深处', subtitle: '埙 · 独奏', language: '纯音乐', publishState: 'PUBLISHED', copyrightState: 'VALID', playCount: 4300, likeCount: 190, commentCount: 27, createTime: '2026-11-15 09:00:00', publishedAt: '2026-11-15 12:00:00' },
  { id: 40008, title: '蜀道难', subtitle: '摇滚 · 改编', language: '普通话', publishState: 'OFFLINE', copyrightState: 'DECLARED', playCount: 7800, likeCount: 350, commentCount: 55, createTime: '2026-11-14 16:30:00', publishedAt: '2026-11-14 19:00:00' },
  { id: 40009, title: '静夜思', subtitle: '童声 · 吟诵', language: '普通话', publishState: 'PUBLISHED', copyrightState: 'VALID', playCount: 21000, likeCount: 1200, commentCount: 156, createTime: '2026-11-13 10:20:00', publishedAt: '2026-11-13 13:00:00' },
  { id: 40010, title: '将进酒', subtitle: '鼓乐 · 合奏', language: '纯音乐', publishState: 'DRAFT', copyrightState: 'VALID', playCount: 0, likeCount: 0, commentCount: 0, createTime: '2026-11-12 14:00:00', publishedAt: null },
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

await page.route('**/music/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   乐坊坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/music/v1/song/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/music/v1/song/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
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
  /* 与后端同口径：keyword 按曲名模糊匹配，publishState 精确匹配（SongQueryCommand） */
  const matched = SONGS.filter((row) => {
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

/* ---- 0. 空间音频负向验证：出生点在城北，离东南外城乐坊约 94 米，
   琴音增益必须为 0——否则"靠近拉起"就是无条件常开，等于没接线。 ---- */
const farGain = await page.evaluate(() => window.__bwwAudio?.().stageGain ?? null);
check('出生点远离乐坊，琴音增益为 0（负向验证）', farGain === 0, farGain);

/* ---- 1. 屏风立起来了，且挂的是歌曲台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yuefang"]');
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
check('乐坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 yuefang', mounted?.id === 'yuefang', mounted?.id);
check('大屏规格（七列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是乐坊 · 歌曲台账', String(mounted?.title).includes('歌曲台账'), mounted?.title);
check('入口标签跟概览 links 走：歌曲台账 + 艺人台账两个',
  mounted?.tabs?.length === 2 && mounted.tabs[0] === '歌曲台账' && mounted.tabs[1] === '艺人台账', mounted?.tabs);
const artistDisabled = await page.evaluate(() => {
  const tabs = [...document.querySelectorAll('[data-screen-id="yuefang"] .bwsp-tab')];
  return tabs.find((tab) => tab.textContent === '艺人台账')?.disabled ?? null;
});
check('艺人台账标签是禁用的（屏风只挂歌曲主账）', artistDisabled === true, artistDisabled);

/* ---- 2. 概览走 /music/overview ---- */
check('概览请求打到 /music/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/music/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（已上架/待审评论/申诉/版权）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（歌曲发布状态分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警（三黄催办 + 一条下架存量说明）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yuefang"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('已上架 18 支、灰灯（只报数不喊事）', kpiCards[0]?.value === '18' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('已上架牌写出架上成色（草稿 6 · 已下架 2）', String(kpiCards[0]?.label).includes('草稿 6'), kpiCards[0]?.label);
check('待审评论 5 条、黄灯（先审后发压着）',
  kpiCards[1]?.value === '5' && kpiCards[1]?.tone === 'warn'
  && String(kpiCards[1]?.label).includes('先审后发'), kpiCards[1]);
check('待复核申诉 3 单、黄灯（对下架不服等复核）',
  kpiCards[2]?.value === '3' && kpiCards[2]?.tone === 'warn'
  && String(kpiCards[2]?.label).includes('下架不服'), kpiCards[2]);
check('版权待复核 4 支、黄灯（协议已签等过目）',
  kpiCards[3]?.value === '4' && kpiCards[3]?.tone === 'warn'
  && String(kpiCards[3]?.label).includes('协议已签'), kpiCards[3]);

/* ---- 3. 状态分布柱图：三态分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yuefang"]');
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
check('状态分布画成柱图（不是折线）', barChart.rows.length === 3 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是服务端定的三态标签（草稿/已上架/已下架）',
  barChart.labels.join(',') === '草稿,已上架,已下架', barChart.labels);
check('柱值按计数排版（6/18/2）', barChart.values.join(',') === '6,18,2', barChart.values);

/* ---- 4. 有搜索框、也有状态筛选，参数名分别是 keyword / publishState ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="yuefang"]');
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
check('有搜索框（后端 keyword 模糊曲名）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（曲名）', String(filters?.placeholder).includes('曲名'), filters?.placeholder);
check('有状态筛选（分页接口真收 publishState）', filters?.statusHidden === false, filters);
check('状态筛选项是发布三态（不摆版权态——那是列级字典）',
  filters?.statusValues.join(',') === ',DRAFT,PUBLISHED,OFFLINE', filters?.statusValues);

/* ---- 5. 首屏列表：10 首分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 10 首 / 2 页', probe?.list.rows === 6 && probe?.list.total === 10 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / publishState 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.publishState === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="yuefang"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    language: tr.children[1]?.textContent ?? '',
    publish: tr.children[2]?.textContent ?? '',
    publishTone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    copyright: tr.children[3]?.textContent ?? '',
    copyrightTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    play: tr.children[4]?.textContent ?? '',
    created: tr.children[5]?.textContent ?? '',
    published: tr.children[6]?.textContent ?? '',
  })),
);
let rows = await readRows();
check('曲名原样显示', rows[0]?.title === '春风序', rows[0]?.title);
check('发布态走页面级字典：草稿灰灯',
  rows[0]?.publish === '草稿' && rows[0]?.publishTone === 'flat', rows[0]);
check('版权态走列级字典：待复核黄灯（与发布态同名字段不同义）',
  rows[0]?.copyright === '待复核' && rows[0]?.copyrightTone === 'warn', rows[0]);
check('发布态已上架绿灯',
  rows[1]?.publish === '已上架' && rows[1]?.publishTone === 'good', rows[1]);
check('版权态有效绿灯',
  rows[1]?.copyright === '有效' && rows[1]?.copyrightTone === 'good', rows[1]);
check('版权态已下架灰灯（处置结果，不催办）',
  rows[3]?.copyright === '已下架' && rows[3]?.copyrightTone === 'flat', rows[3]);
check('收录时间按 datetime 口径（MM-DD HH:mm）', rows[0]?.created === '11-21 09:30', rows[0]?.created);
check('上架时间按 datetime 口径（11-20 15:00）', rows[1]?.published === '11-20 15:00', rows[1]?.published);
await page.screenshot({ path: `${OUT}/90-yuefang-list.png` });

/* ---- 6. 外城远距离走位：乐坊在东南外城（x/z = 39.73/85.20，旋转 -155°），
   出生点离得远且镜头朝北，必须先走。屏风位姿直接从 CSS3D 变换矩阵解：
   matrix3d 列主序，第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴
   （即屏风正面法线，与 districtScreenPose 的 front 同口径）。
   站到法线外侧 4.5 米；外城约 80 米路程，时间窗放宽到 18 秒（软件渲染约 17fps）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yuefang"]');
  /* 关键：`bws-screen` 元素**自身**的 matrix3d 才是世界变换（v[12]/v[14] 是世界
     x/z）；parentElement 是 CSS3DRenderer 的相机透视层，那层的 v[12]/v[14] 是
     相机坐标——从它解位姿会得到一个看着合理但方向全错的位置（外城尤其明显：
     解出 (0,-76) 而真乐坊在 (39.73,85.20)）。前几坊用 dispatchEvent 强制点击、
     断言不校验真实距离，把这个坑盖住了；乐坊的空间音频按真实距离衰减，
     走错方向就恒 0，才把它暴露出来。优先取自身 transform，parent 仅作回退。 */
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
/* 乐坊在东南外城（x>0, z>0）。若解出的 x≈0/z<0，说明误取了相机透视层的
   transform——那条路会让玩家走错方向、音频恒 0，必须当场拦住。 */
check('位姿来自世界层而非相机层（乐坊在东南 x>0 且 z>0）',
  pose?.x > 10 && pose?.z > 50, pose);
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
  const el = document.querySelector('[data-screen-id="yuefang"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="yuefang"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="yuefang"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'yuefang', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6b. 空间音频探针：靠近乐坊时增益拉起（只验状态，不测听感） ---- */
const nearGain = await page.evaluate(() => window.__bwwAudio?.().stageGain ?? null);
check('靠近乐坊时琴音增益拉起（空间音频已接线）',
  typeof nearGain === 'number' && nearGain > 0, nearGain);
await page.screenshot({ path: `${OUT}/91b-yuefang-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=云 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('云');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
const lastSearch = queried.filter((item) => item.keyword === '云').pop();
check('关键字按 keyword=云 发给服务端（模糊曲名）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 2 首（云想衣裳 + 云深处）',
  probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('命中的是曲名真带云的两首', rows.every((row) => row.title.includes('云')), rows.map((row) => row.title));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 发布态筛选：发 publishState=PUBLISHED ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('PUBLISHED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
const lastStatus = queried.filter((item) => item.publishState === 'PUBLISHED').pop();
check('发布态按 publishState=PUBLISHED 发给服务端（独立参数不并进 keyword）', Boolean(lastStatus), queried.slice(-3));
check('已上架剩 5 首', probe?.list.total === 5 && probe?.list.rows === 5, probe?.list);
rows = await readRows();
check('命中的都是已上架（绿灯）',
  rows.every((row) => row.publish === '已上架' && row.publishTone === 'good'), rows.map((row) => row.publish));
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 4 首 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
check('第 2 页剩 4 首', probe?.list.page === 2 && probe?.list.rows === 4, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是云深处（page 参数真的生效了）',
  rows[0]?.title === '云深处', rows[0]?.title);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（SongView 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 10 个字段用列表行数据（含副题/版权/播放/收藏/评论）',
  probe?.detail.fields === 10 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/92-yuefang-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('yuefang'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yuefang"]');
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
await page.route('**/music/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/music/v1/song/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="yuefang"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="yuefang"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('yuefang');
  const empty = document.querySelector('[data-screen-id="yuefang"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="yuefang"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/93-yuefang-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
