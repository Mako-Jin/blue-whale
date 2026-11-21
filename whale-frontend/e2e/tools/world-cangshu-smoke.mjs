// 藏书殿文件台账（S5 / T5.4）冒烟：屏风里是"东西放在哪儿"的真业务页。
//
// 与前面几块屏风的分工：
//   户部（T4.5）验列表该有的交互（筛选/分页/详情/分录）；
//   数枢殿（T5.1）验各殿连通状态；枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   度支殿（T5.3）验"接口支持 keyword 就必须给搜索框"；
//   这条验藏书殿自己的四件事：
//     ① 概览四张 KPI 是"库存 + 流动"（文件数 / 占用空间 / 今日新增 / 回收站）；
//     ② 大小列走 `size` 口径（字节 → "2.5 MB"），既不是 ¥ 也不是裸数字；
//     ③ **接口没有状态参数，屏风就不给状态下拉**（与度支殿正相反，两条一起才是判据）；
//     ④ 关键字参数名是 `originalName`（不是通用的 keyword）——发错参数名时后端不报错、只是不过滤。
//
// 数据来源：page.route 拦 /file/overview 与 /file/v1/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着五块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="cangshu"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s5-shots';
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
const OVERVIEW = {
  service: 'file',
  nodeKey: 'cangshu-hall',
  title: '藏书殿',
  subtitle: '文件与存储 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'files', label: '文件总数', value: 128, format: 'count', tone: 'flat', unit: '份', hint: '不含历史版本', display: '128份' },
    { key: 'bytes', label: '占用空间', value: 3.4, format: 'count', tone: 'flat', unit: 'GB', hint: '含各版本行', display: '3.4GB' },
    { key: 'today', label: '今日新增', value: 6, format: 'count', tone: 'good', unit: '份', hint: '按根文件计', display: '6份' },
    { key: 'recycle', label: '回收站', value: 4, format: 'count', tone: 'warn', unit: '份', hint: '其中超期 2 份', display: '4份' },
  ],
  charts: [
    {
      key: 'upload-trend',
      title: '近 7 日新增文件',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '新增', values: [3, 0, 5, 2, 8, 1, 6] }],
    },
    {
      key: 'bucket-mix',
      title: '按桶分布',
      type: 'bar',
      format: 'count',
      categories: ['doc', 'avatar', 'attachment'],
      series: [{ name: '文件数', values: [90, 30, 8] }],
    },
  ],
  alerts: [{ level: 'warning', text: '回收站有 2 份文件已过 30 天保留期未清理，清理作业可能没跑', count: 2 }],
  links: [{ key: 'files', label: '文件台账', path: '/file/v1/page', permission: 'sys:file:list' }],
  notes: ['口径：文件数按根文件计（与文件台账同口径，不含历史版本），占用空间含全部版本行；回收站只统计已删除的根文件'],
};

/** 9 份文件：大小刻意跨 B / KB / MB / GB 四档，专测 size 口径 */
const FILES = [
  { id: 7101, originalName: '2026 年度预算说明.docx', fileName: 'f71a-c001.docx', bucket: 'doc', objectKey: 'doc/2026/f71a-c001.docx', fileSize: 2621440, contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', md5: 'a1b2c3d4e5f60718293a4b5c6d7e8f90', uploaderId: 1, createTime: '2026-11-21T09:12:00' },
  { id: 7102, originalName: '首页海报.png', fileName: 'f71a-c002.png', bucket: 'avatar', objectKey: 'avatar/2026/f71a-c002.png', fileSize: 512, contentType: 'image/png', md5: 'b1b2c3d4e5f60718293a4b5c6d7e8f91', uploaderId: 1, createTime: '2026-11-21T09:05:30' },
  { id: 7103, originalName: '培训录像.mp4', fileName: 'f71a-c003.mp4', bucket: 'attachment', objectKey: 'attachment/2026/f71a-c003.mp4', fileSize: 1610612736, contentType: 'video/mp4', md5: 'c1b2c3d4e5f60718293a4b5c6d7e8f92', uploaderId: 2, createTime: '2026-11-21T08:40:12' },
  { id: 7104, originalName: '采购合同-2026-011.pdf', fileName: 'f71a-c004.pdf', bucket: 'doc', objectKey: 'doc/2026/f71a-c004.pdf', fileSize: 358400, contentType: 'application/pdf', md5: 'd1b2c3d4e5f60718293a4b5c6d7e8f93', uploaderId: 1, createTime: '2026-11-21T08:12:00' },
  { id: 7105, originalName: '设备清单.xlsx', fileName: 'f71a-c005.xlsx', bucket: 'doc', objectKey: 'doc/2026/f71a-c005.xlsx', fileSize: 20480, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', md5: 'e1b2c3d4e5f60718293a4b5c6d7e8f94', uploaderId: 3, createTime: '2026-11-21T07:50:00' },
  { id: 7106, originalName: '旧版方案.pdf', fileName: 'f71a-c006.pdf', bucket: 'doc', objectKey: 'doc/2026/f71a-c006.pdf', fileSize: 1048576, contentType: 'application/pdf', md5: 'f1b2c3d4e5f60718293a4b5c6d7e8f95', uploaderId: 1, createTime: '2026-11-20T18:20:00' },
  { id: 7107, originalName: 'logo-备用.svg', fileName: 'f71a-c007.svg', bucket: 'avatar', objectKey: 'avatar/2026/f71a-c007.svg', fileSize: 3072, contentType: 'image/svg+xml', md5: '11b2c3d4e5f60718293a4b5c6d7e8f96', uploaderId: 2, createTime: '2026-11-20T15:02:00' },
  { id: 7108, originalName: '备份-1120.zip', fileName: 'f71a-c008.zip', bucket: 'attachment', objectKey: 'attachment/2026/f71a-c008.zip', fileSize: 52428800, contentType: 'application/zip', md5: '21b2c3d4e5f60718293a4b5c6d7e8f97', uploaderId: 1, createTime: '2026-11-19T20:30:00' },
  { id: 7109, originalName: '会议纪要-1120.md', fileName: 'f71a-c009.md', bucket: 'doc', objectKey: 'doc/2026/f71a-c009.md', fileSize: 4096, contentType: 'text/markdown', md5: '31b2c3d4e5f60718293a4b5c6d7e8f98', uploaderId: 1, createTime: '2026-11-19T11:00:00' },
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
    // 没令牌时屏风页连请求都不发（直接显示"未登录"），这里给一个 debug 令牌
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});

/** 记录屏风页真实发出的查询参数：只发接口支持的参数，不发明参数 */
const queried = [];

await page.route('**/file/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/file/v1/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 文件名模糊参数在生产里叫 originalName（FilePageRequest.originalName），不是 keyword
  const keyword = url.searchParams.get('originalName');
  queried.push({
    current,
    size,
    keyword,
    /* 把全部参数名记下来，用来断言"没有发明接口不认的参数" */
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = FILES.filter((row) => (keyword ? row.originalName.includes(keyword) : true));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是文件台账 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="cangshu"]');
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  return {
    size: el.dataset.size,
    title: el.querySelector('.bws-title')?.textContent ?? '',
    w: Math.round(rect.width),
    h: Math.round(rect.height),
    hasPage: Boolean(el.querySelector('.bwsp')),
    tabs: [...el.querySelectorAll('.bwsp-tab')].map((tab) => tab.textContent),
  };
});
check('藏书殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是藏书殿 · 文件台账', String(mounted?.title).includes('文件台账'), mounted?.title);
check('入口标签来自概览 links（文件台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '文件台账', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI + 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('cangshu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（文件数/占用空间/今日新增/回收站）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日新增 + 按桶分布）', probe?.overview.charts === 2, probe?.overview.charts);

/* ---- 3. 搜索框：接口支持 originalName，屏风就必须给；状态筛选接口没有，就不能给 ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="cangshu"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const status = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    /* 渲染器永远创建这个 select，没有 statusParam 时置 hidden——
       所以要判"可见"而不是"存在" */
    statusVisible: status ? !status.hidden : false,
  };
});
check('有搜索框（文件接口支持 originalName 模糊）', filters?.searchHidden === false, filters);
check('搜索框提示写的是真字段（文件名）', String(filters?.placeholder).includes('文件名'), filters?.placeholder);
check('没有状态下拉（接口没有 status 参数，给了就是不生效的框）', filters?.statusVisible === false, filters);

/* ---- 4. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword/status）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null
    && queried[0].names.every((name) => ['current', 'size'].includes(name)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="cangshu"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    bucket: tr.children[1]?.textContent ?? '',
    size: tr.children[2]?.textContent ?? '',
    type: tr.children[3]?.textContent ?? '',
    created: tr.children[4]?.textContent ?? '',
  })),
);
check('大小列走 size 口径（字节 → 2.5 MB，不是 ¥ 也不是裸数字）',
  rows[0]?.size === '2.5 MB', rows.map((row) => row.size));
check('小文件走 B 档（512 B 不被折成 0.0 KB）', rows[1]?.size === '512 B', rows[1]?.size);
check('大文件走 GB 档（1.5 GB）', rows[2]?.size === '1.5 GB', rows[2]?.size);
check('上传时间到分钟', rows.every((row) => /^\d{2}-\d{2} \d{2}:\d{2}$/.test(row.created)), rows.map((row) => row.created));
check('桶列是原名（doc / avatar / attachment）',
  rows.slice(0, 3).map((row) => row.bucket).join(',') === 'doc,avatar,attachment', rows.map((row) => row.bucket));

await page.screenshot({ path: `${OUT}/30-cangshu-list.png` });

/* ---- 5. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="cangshu"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="cangshu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="cangshu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'cangshu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6. 关键字搜索：参数名必须是 originalName，且结果真被筛过 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('合同');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('cangshu'));
const lastKeyword = queried.filter((item) => item.keyword === '合同').pop();
check('关键字按 originalName 发给服务端（不是通用 keyword）', Boolean(lastKeyword), queried.slice(-3));
check('关键字确实筛掉其它行（1 条）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(800);

/* ---- 7. 分页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('cangshu'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(500);

/* ---- 8. 点行看详情（同一份快照，不再二次请求） ---- */
const beforeDetail = queried.length;
await page.locator(SCOPE + '.bwsp-table tbody tr').nth(2).click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('cangshu'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是原始文件名', probe?.detail.title === '培训录像.mp4', probe?.detail.title);
check('详情 9 个字段（含对象键/上传人）', probe?.detail.fields === 9, probe?.detail);
check('详情不再打二次接口（列表行就是详情）', queried.length === beforeDetail, queried.length - beforeDetail);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="cangshu"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的对象键没被吞掉',
  detailText.some((text) => text.includes('attachment/2026/f71a-c003.mp4')), detailText.slice(0, 9));
check('详情里的大小也是 size 口径', detailText.some((text) => text === '1.5 GB'), detailText.slice(0, 9));
await page.screenshot({ path: `${OUT}/31-cangshu-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('cangshu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 9. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="cangshu"]');
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

/* ---- 10. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/file/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/file/v1/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="cangshu"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="cangshu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('cangshu');
  const empty = document.querySelector('[data-screen-id="cangshu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="cangshu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/32-cangshu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
