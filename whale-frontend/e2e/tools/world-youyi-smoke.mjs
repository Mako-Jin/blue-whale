// 邮驿邮件列表（S7 / T7.4）冒烟：外城九处的第四处，本坊特殊项是**邮件列表**。
//
// 与清议堂的分工：清议堂验公开议事的秩序，这一条验**异步信件的收发链路**——
// 账号同步（信进不进得来）、配额（存不存得住）、退信（送不送得出去）。
//
// 十件事：
//   ① 屏风挂的是邮件列表页；概览下发三个入口（邮件列表 / 邮箱账号 / 退信回执），
//      本地只配了邮件列表，另外两个必须渲染成**禁用标签**（不是消失，也不是能点）；
//   ② 四张牌走 /mail/overview：在册邮箱灰灯只报数，同步超时 / 配额吃紧 / 退信三张有账就黄；
//   ③ 柱子是账号**同步四态**（近一日已同步 / 超时未同步 / 从未同步 / 未开同步），
//      四格互斥且加起来等于在册数；
//   ④ 列表下拉框选的是**文件夹不是状态**：邮件没有状态维度，statusParam 指向
//      folderCode；空串表示"不给参数"，服务端默认取 INBOX；
//   ⑤ 关键字发 keyword（模糊主题）；
//   ⑥ 已读 / 附件走 0-1 字典（键必须是字符串，写数字会静默查不到）；
//   ⑦ 列表行即详情（不配 detailPath）：11 个字段用行数据平铺，不打详情接口——
//      /mail/v1/message/{id} 会触发正文回源，屏风上点一行不该去打邮箱服务器；
//   ⑧ 外城远距离走位：从 CSS3D 变换矩阵解屏风位姿，走到正面法线外 4.5 米；
//   ⑨ 分页 10 条 / 6 行一页 → 第 2 页 4 条，末页禁用下一页；
//   ⑩ 接口全挂时不白屏：标 stale + 收起表格给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十余块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="youyi"] ';

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
/* 与 MailOverviewProvider 的实际下发一致：四张 KPI（三张欠账牌黄）、
   一张四态柱图、五条告警（超时/配额/永久退信 warn + 从未同步/临时退信 info）、
   三个入口。 */
const OVERVIEW = {
  service: 'mail',
  nodeKey: 'youyi-stage',
  title: '邮驿',
  subtitle: '邮件收发 · 驿路与信件',
  generatedAt: '2026-11-23T00:30:00+08:00',
  state: 'live',
  kpis: [
    { key: 'mailboxes', label: '在册邮箱', value: 22, format: 'count', tone: 'flat', hint: '另停用 3 个' },
    { key: 'sync-overdue', label: '同步超时', value: 2, format: 'count', tone: 'warn', hint: '超过一天没拉到新信' },
    { key: 'quota-tight', label: '配额吃紧', value: 1, format: 'count', tone: 'warn', hint: '已用八成以上，该清理或扩容' },
    { key: 'bounce', label: '近七日退信', value: 4, format: 'count', tone: 'warn', hint: '永久 3 · 临时 1' },
  ],
  charts: [
    {
      key: 'sync-health',
      title: '账号同步分布',
      type: 'bar',
      format: 'count',
      categories: ['近一日已同步', '超时未同步', '从未同步', '未开同步'],
      series: [{ name: '邮箱数', values: [18, 2, 2, 3] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 2 个邮箱超过一天没同步，屏风上的信会越来越旧', count: null },
    { level: 'info', text: '有 2 个邮箱开了同步但一次没跑成，先验协议端点与口令', count: null },
    { level: 'warning', text: '有 1 个邮箱配额已用八成以上，再满就收不进新信', count: null },
    { level: 'warning', text: '近七日有 3 封永久退信（5.x.x），地址多半已失效，重发也是白发', count: null },
    { level: 'info', text: '近七日有 1 封临时退信（4.x.x），对端繁忙或限流，会自动重投', count: null },
  ],
  links: [
    { key: 'inbox', label: '邮件列表', path: '/mail/v1/message/page', permission: 'sys:mail:message:list' },
    { key: 'accounts', label: '邮箱账号', path: '/mail/v1/account/list', permission: 'sys:mail:account:list' },
    { key: 'bounces', label: '退信回执', path: '/mail/v1/delivery/report/page', permission: 'sys:mail:message:list' },
  ],
  notes: ['口径：同步按 24 小时判新鲜；配额按已用 ≥80% 算吃紧（配额未知的不算）；退信只数近 7 日，永久与临时分开'],
};

/**
 * 10 封收件箱 + 2 封已删除。6 行/页 → 2 页。
 * 第 2 封大小 2516582 字节 → 屏风按 1024 进制显示 "2.4 MB"（后端配额也按 1024 算，
 * 两边不一致时"刚满 50MB 却被拒"最难解释）。
 * keyword=驿 命中 3 封（驿路改道 / 驿站马匹调度 / 驿路冬季封路）——"驿"在两字词
 * 中间与词首都命中，证明是模糊匹配。
 * 已删除里两封都是已读：切到"已删除"若还是全"未读"，说明 folderCode 没发出去。
 */
const INBOX = [
  { id: 81001, folderCode: 'INBOX', subject: '秋祭集会流程（终稿）', fromName: '典仪司', fromAddr: 'dianyi@bw.city', seen: 0, hasAttachment: 1, sizeBytes: 12698, sentTime: '2026-11-22 09:12:00', preview: '三献礼改到午时…' },
  { id: 81002, folderCode: 'INBOX', subject: '关于外城驿路改道的复函', fromName: '工部', fromAddr: 'gongbu@bw.city', seen: 1, hasAttachment: 1, sizeBytes: 2516582, sentTime: '2026-11-22 08:30:00', preview: '改道走北线…' },
  { id: 81003, folderCode: 'INBOX', subject: '坊门开闭时刻表（冬令）', fromName: '门下省', fromAddr: 'menxia@bw.city', seen: 1, hasAttachment: 0, sizeBytes: 4096, sentTime: '2026-11-21 19:45:00', preview: '卯时开…' },
  { id: 81004, folderCode: 'INBOX', subject: '你订阅的《鲸闻》第 42 期', fromName: '鲸闻编辑部', fromAddr: 'news@bw.city', seen: 0, hasAttachment: 0, sizeBytes: 20480, sentTime: '2026-11-21 07:00:00', preview: '本期头条…' },
  { id: 81005, folderCode: 'INBOX', subject: '对账差异说明（11 月）', fromName: '户部', fromAddr: 'hubu@bw.city', seen: 1, hasAttachment: 1, sizeBytes: 65536, sentTime: '2026-11-20 16:20:00', preview: '差 37 文…' },
  { id: 81006, folderCode: 'INBOX', subject: '驿站马匹调度表（冬季）', fromName: '兵部', fromAddr: 'bingbu@bw.city', seen: 1, hasAttachment: 0, sizeBytes: 5120, sentTime: '2026-11-20 10:05:00', preview: '驿马 24 匹…' },
  { id: 81007, folderCode: 'INBOX', subject: '影戏院放映设备检修通知', fromName: '工部', fromAddr: 'gongbu@bw.city', seen: 1, hasAttachment: 0, sizeBytes: 3072, sentTime: '2026-11-19 15:00:00', preview: '停映半日…' },
  { id: 81008, folderCode: 'INBOX', subject: '巡夜人手补充申请', fromName: '兵部', fromAddr: 'bingbu@bw.city', seen: 0, hasAttachment: 0, sizeBytes: 2048, sentTime: '2026-11-19 08:40:00', preview: '请增四人…' },
  { id: 81009, folderCode: 'INBOX', subject: '《外城风物志》样章', fromName: '翰林院', fromAddr: 'hanlin@bw.city', seen: 1, hasAttachment: 1, sizeBytes: 4194304, sentTime: '2026-11-18 20:10:00', preview: '附样章三页…' },
  { id: 81010, folderCode: 'INBOX', subject: '关于驿路冬季封路的通告', fromName: '门下省', fromAddr: 'menxia@bw.city', seen: 1, hasAttachment: 0, sizeBytes: 1536, sentTime: '2026-11-18 09:00:00', preview: '大雪起封…' },
];

const TRASH = [
  { id: 83001, folderCode: 'TRASH', subject: '祖传药方（勿信）', fromName: '不明', fromAddr: 'spam@bad.example', seen: 1, hasAttachment: 0, sizeBytes: 1024, sentTime: '2026-11-17 12:00:00', preview: '包治百病…' },
  { id: 83002, folderCode: 'TRASH', subject: '中奖通知', fromName: '不明', fromAddr: 'spam@bad.example', seen: 1, hasAttachment: 0, sizeBytes: 2048, sentTime: '2026-11-16 12:00:00', preview: '您已中奖…' },
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

/** 记录列表真实发出的查询参数：关键字叫 keyword，文件夹叫 folderCode */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/mail/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   邮驿不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/mail/v1/message/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/mail/v1/message/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const folderCode = url.searchParams.get('folderCode');
  queried.push({
    current,
    size,
    keyword,
    folderCode,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：不给 folderCode 就是 INBOX（resolveFolder 的默认）；
     keyword 按主题模糊匹配 */
  const pool = folderCode === 'TRASH' ? TRASH : INBOX;
  const matched = pool.filter((row) => !keyword || row.subject.includes(keyword));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是邮件列表 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="youyi"]');
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
check('邮驿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 youyi', mounted?.id === 'youyi', mounted?.id);
check('大屏规格（六列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是邮驿 · 邮件列表', String(mounted?.title).includes('邮件列表'), mounted?.title);
check('入口标签跟概览 links 走：邮件列表 / 邮箱账号 / 退信回执三个',
  mounted?.tabs?.length === 3
  && mounted.tabs[0] === '邮件列表' && mounted.tabs[1] === '邮箱账号' && mounted.tabs[2] === '退信回执',
  mounted?.tabs);
const otherTabs = await page.evaluate(() => {
  const tabs = [...document.querySelectorAll('[data-screen-id="youyi"] .bwsp-tab')];
  return tabs.filter((tab) => tab.textContent !== '邮件列表').map((tab) => tab.disabled);
});
check('邮箱账号与退信回执是禁用标签（屏风只挂邮件主账）',
  otherTabs.length === 2 && otherTabs.every((flag) => flag === true), otherTabs);

/* ---- 2. 概览走 /mail/overview ---- */
check('概览请求打到 /mail/overview',
  overviewHits.length > 0 && overviewHits.every((p) => p === '/mail/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在册邮箱/同步超时/配额吃紧/近七日退信）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（账号同步分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('五条告警（三黄欠账 + 从未同步 + 临时退信两条提示）', probe?.overview.alerts === 5, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="youyi"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在册 22 个、灰灯（只报数不喊事）', kpiCards[0]?.value === '22' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('在册牌写出停用存量（另停用 3 个）', String(kpiCards[0]?.label).includes('另停用 3 个'), kpiCards[0]?.label);
check('同步超时 2 个、黄灯（信进不来）',
  kpiCards[1]?.value === '2' && kpiCards[1]?.tone === 'warn'
  && String(kpiCards[1]?.label).includes('超过一天'), kpiCards[1]);
check('配额吃紧 1 个、黄灯（信快存不住）',
  kpiCards[2]?.value === '1' && kpiCards[2]?.tone === 'warn'
  && String(kpiCards[2]?.label).includes('八成'), kpiCards[2]);
check('退信 4 封、黄灯（信出不去），提示永久与临时分开',
  kpiCards[3]?.value === '4' && kpiCards[3]?.tone === 'warn'
  && String(kpiCards[3]?.label).includes('永久 3 · 临时 1'), kpiCards[3]);

/* ---- 3. 同步四态柱图：四格分桶、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="youyi"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  return { bars: rows.length, labels };
});
check('柱图四根柱（同步四态全覆盖）', barChart.bars === 4, barChart);
check('四态标签是已同步/超时/从未/未开',
  JSON.stringify(barChart.labels) === JSON.stringify(['近一日已同步', '超时未同步', '从未同步', '未开同步']),
  barChart.labels);

/* ---- 4. 邮件列表：列、字典、行数 ---- */
const readRows = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('[data-screen-id="youyi"] .bwsp-table tbody tr')].map((tr) => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        subject: cells[0]?.textContent?.trim() ?? '',
        from: cells[1]?.textContent?.trim() ?? '',
        seen: cells[2]?.textContent?.trim() ?? '',
        seenTone: cells[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
        attachment: cells[3]?.textContent?.trim() ?? '',
        size: cells[4]?.textContent?.trim() ?? '',
        sentTime: cells[5]?.textContent?.trim() ?? '',
      };
    }),
  );

let rows = await readRows();
check('第一页 6 行', rows.length === 6, rows.length);
check('首行主题是秋祭集会流程（终稿）', rows[0]?.subject === '秋祭集会流程（终稿）', rows[0]?.subject);
check('发件人列显示显示名（不是裸地址）', rows[0]?.from === '典仪司', rows[0]?.from);
check('未读行黄灯（0 开头的字典键用字符串，写数字会静默查不到）',
  rows[0]?.seen === '未读' && rows[0]?.seenTone === 'warn', rows[0]);
check('已读行灰灯', rows[1]?.seen === '已读' && rows[1]?.seenTone === 'flat', rows[1]);
check('附件列走 0/1 字典（有附件写"附件"，没有给破折号）',
  rows[0]?.attachment === '附件' && rows[2]?.attachment === '—',
  rows.map((row) => row.attachment));
check('大小按 1024 进制格式化（12698 字节 → 12.4 KB）', rows[0]?.size === '12.4 KB', rows[0]?.size);
check('两兆多的那封显示 2.4 MB（不是 2.5，也不是 2516582）', rows[1]?.size === '2.4 MB', rows[1]?.size);
check('时间走 datetime 口径（MM-DD HH:mm）', /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.sentTime ?? ''), rows[0]?.sentTime);

/* ---- 5. 走位到屏风前：从 CSS3D 变换矩阵解位姿 ----
   注意不要先来一次 __bwwWalkTo(0,0)"热个身"：走位是"设一个目标、逐帧走过去"，
   连续两次调用会让玩家停在第一个目标，而屏风用 dispatchEvent 强制点击照样能进——
   走位失败会被完全盖住。 */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="youyi"]');
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
/* 邮驿在东北外城（x≈54, z≈-77）。若解出的 x≈0，说明误取了相机透视层的
   transform——那条路会让玩家走错方向，必须当场拦住。 */
check('位姿来自世界层而非相机层（邮驿在东北外城 x>40 且 z<-60）',
  pose?.x > 40 && pose?.z < -60, pose);
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
  const el = document.querySelector('[data-screen-id="youyi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1500);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="youyi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="youyi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'youyi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
/* 走位必须真的到了屏风前：玩家与屏风落点的距离要落在"能进门"的范围内，
   否则 dispatchEvent 会把走位失败盖过去（影戏院就是被这个坑盖了半天）。 */
const playerAt = await page.evaluate(() => window.__bwwDebug?.() ?? null);
const gap = pose && playerAt ? Math.round(Math.hypot(pose.x - playerAt.x, pose.z - playerAt.z)) : null;
console.log(`DIAG 玩家 (${playerAt?.x}, ${playerAt?.z}) 屏风 (${pose?.x}, ${pose?.z}) 距离 ${gap}m`);
check('走位真的走到了屏风前（距离 < 10 米）', gap !== null && gap < 10, { playerAt, pose, gap });
await page.screenshot({ path: `${OUT}/100-youyi-engaged.png` });

/* ---- 6. 关键字搜索：发 keyword=驿 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('驿');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
const lastSearch = queried.filter((item) => item.keyword === '驿').pop();
check('关键字按 keyword=驿 发给服务端（模糊主题）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 3 封（驿路改道 / 驿站马匹 / 驿路封路）',
  probe?.list.total === 3 && probe?.list.rows === 3, probe?.list);
rows = await readRows();
check('命中的是主题真带驿的三封', rows.every((row) => row.subject.includes('驿')), rows.map((row) => row.subject));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 7. 文件夹下拉：发 folderCode=TRASH（这个控件选的是抽屉，不是状态） ---- */
const folderOptions = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="youyi"] .bwsp-status option')].map((opt) => ({
    value: opt.value,
    label: opt.textContent,
  })),
);
check('下拉框是五个抽屉（收件箱/已发送/草稿箱/垃圾邮件/已删除），没有"全部状态"这种伪状态',
  folderOptions.length === 5 && folderOptions[0].label === '收件箱' && folderOptions[0].value === ''
  && folderOptions[4].value === 'TRASH',
  folderOptions);
await page.locator(SCOPE + '.bwsp-status').selectOption('TRASH');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
const lastFolder = queried.filter((item) => item.folderCode === 'TRASH').pop();
check('切换抽屉按 folderCode=TRASH 发给服务端（独立参数不并进 keyword）', Boolean(lastFolder), queried.slice(-3));
check('已删除里只剩 2 封', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('两封都已读（抽屉真的换了，不是同一批数据）',
  rows.every((row) => row.seen === '已读') && rows[0]?.subject === '祖传药方（勿信）',
  rows.map((row) => `${row.subject}/${row.seen}`));
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
check('切回收件箱后回到 10 封', probe?.list.total === 10, probe?.list);

/* ---- 8. 分页：第 2 页 4 封 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
check('第 2 页剩 4 封', probe?.list.page === 2 && probe?.list.rows === 4, probe?.list);
check('第 2 页是末页（10 封 / 6 行）', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是影戏院放映设备检修通知（page 参数真的生效了）',
  rows[0]?.subject === '影戏院放映设备检修通知', rows[0]?.subject);
check('末页仍按 datetime 排版（不是空白或 1970）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.sentTime ?? ''), rows[0]?.sentTime);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（正文回源要连 IMAP，屏风上不该触发）',
  detailHits.length === 0, detailHits);
check('详情 11 个字段用列表行数据',
  probe?.detail.fields === 11 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/101-youyi-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('youyi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="youyi"]');
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

/* ---- 11. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/mail/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/mail/v1/message/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="youyi"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="youyi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('youyi');
  const empty = document.querySelector('[data-screen-id="youyi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="youyi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/102-youyi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
