// 起居殿审计留痕（S5 / T5.9）冒烟：屏风里是"审计本身还活着吗"的真业务页。
//
// 与前面几块屏风的分工：
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   藏书殿（T5.4）验"关键字参数名要跟接口真字段一致（originalName 不是 keyword）"；
//   时辰殿（T5.5）验"keyword + status 两个筛选都给、状态值用后端原值"；
//   通政殿（T5.8）验"没有关键字条件就连搜索框都不给 + 列级字典不串味"；
//   这条验起居殿自己的四件事：
//     ① 概览四张 KPI 里有两张"静默失效"牌——留痕断流（DURATION 排版）与归档陈旧（天）；
//     ② 关键字参数是 `username`（不是 keyword），且确实发给服务端做模糊匹配；
//     ③ 结果状态 bizStatus 是数字 1/0，靠页面级字典译成"成功/失败"并打灯（String(value) 查表）；
//     ④ 列表行即详情（不配 detailPath）：点行不发二次请求，详情用列表行数据。
//
// 数据来源：page.route 拦 /audit/overview 与 /audit/v1/log/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="qiju"] ';

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
  service: 'audit',
  nodeKey: 'qiju-hall',
  title: '起居殿',
  subtitle: '审计留痕 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'today', label: '今日审计', value: 1284, format: 'count', tone: 'flat', hint: '本租户 · 今日写入的审计记录', display: '1,284' },
    { key: 'failed', label: '失败操作', value: 7, format: 'count', tone: 'warn', hint: '近 24h · biz_status=0', display: '7' },
    { key: 'silence', label: '留痕断流', value: 108000, format: 'duration', tone: 'bad', hint: '超 2h 无新审计 · 请查摄入链路', display: '30.0小时' },
    { key: 'stale', label: '归档陈旧', value: 5, format: 'count', tone: 'warn', hint: '归档作业最后成功距今 · 天', display: '5' },
  ],
  charts: [
    {
      key: 'audit-trend',
      title: '近 7 日审计',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '审计', values: [980, 1102, 0, 1340, 1255, 1180, 1284] }],
    },
    {
      key: 'operate-type',
      title: '近 7 日操作类型',
      type: 'bar',
      format: 'count',
      categories: ['QUERY', 'LOGIN', 'UPDATE', 'CREATE', 'GRANT'],
      series: [{ name: '条数', values: [820, 310, 96, 42, 16] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '审计留痕已断流 30.0 小时，写入链路可能已断', count: 30 },
    { level: 'warning', text: '归档作业 5 天未成功，审计证据未归档', count: 5 },
  ],
  links: [
    { key: 'logs', label: '审计日志', path: '/audit/v1/log/page', permission: 'sys:audit:log:list' },
  ],
  notes: ['口径：断流 = 当前时刻减本租户最近一条审计写入时刻；归档陈旧与 audit.job.stale.days 同口径'],
};

/** 8 条审计记录：bizStatus 覆盖 1/0、module/operateType 多样 */
const LOGS = [
  { id: 8001, createTime: '2026-11-21T08:52:00', username: 'admin', module: 'whale-auth', operateType: 'GRANT', bizStatus: 1, operation: '授予角色', ip: '10.0.0.8' },
  { id: 8002, createTime: '2026-11-21T08:40:00', username: 'qa_admin', module: 'whale-erp', operateType: 'UPDATE', bizStatus: 0, operation: '修改采购单', ip: '10.0.0.9' },
  { id: 8003, createTime: '2026-11-21T08:15:00', username: 'zhangsan', module: 'whale-file', operateType: 'CREATE', bizStatus: 1, operation: '上传文件', ip: '10.0.1.2' },
  { id: 8004, createTime: '2026-11-21T07:58:00', username: 'lisi', module: 'whale-auth', operateType: 'LOGIN', bizStatus: 0, operation: '用户登录', ip: '10.0.1.3' },
  { id: 8005, createTime: '2026-11-21T07:30:00', username: 'admin', module: 'whale-pay', operateType: 'QUERY', bizStatus: 1, operation: '查询支付单', ip: '10.0.0.8' },
  { id: 8006, createTime: '2026-11-21T07:05:00', username: 'wangwu', module: 'whale-form', operateType: 'DELETE', bizStatus: 1, operation: '删除表单', ip: '10.0.2.1' },
  { id: 8007, createTime: '2026-11-20T19:20:00', username: 'qa_admin', module: 'whale-job', operateType: 'UPDATE', bizStatus: 1, operation: '启停任务', ip: '10.0.0.9' },
  { id: 8008, createTime: '2026-11-20T18:00:00', username: 'admin', module: 'whale-audit', operateType: 'EXPORT', bizStatus: 1, operation: '导出审计日志', ip: '10.0.0.8' },
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

/** 记录屏风页真实发出的查询参数：关键字必须是 username，不是 keyword */
const queried = [];
const detailHits = [];

await page.route('**/audit/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   起居殿不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/audit/v1/log/*', async (route) => {
  const url = new URL(route.request().url());
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/audit/v1/log/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 AuditLogQueryRequest 的文本条件叫 username（模糊），没有 keyword
  const username = url.searchParams.get('username');
  const bizStatus = url.searchParams.get('bizStatus');
  queried.push({
    current,
    size,
    username,
    bizStatus,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = LOGS.filter((row) => {
    const byName = username === null || username === '' || row.username.includes(username);
    const byStatus = bizStatus === null || bizStatus === '' || String(row.bizStatus) === bizStatus;
    return byName && byStatus;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是审计日志页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qiju"]');
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
check('起居殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是起居殿 · 审计日志', String(mounted?.title).includes('审计日志'), mounted?.title);
check('入口标签来自概览 links（审计日志）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '审计日志', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI + 两张图 + 两条告警 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（今日审计/失败操作/留痕断流/归档陈旧）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日审计 + 操作类型）', probe?.overview.charts === 2, probe?.overview.charts);
check('两条告警都下发（断流 + 归档，根因不同）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiTones = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="qiju"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('今日审计不亮灯（有量本身不是问题）', kpiTones[0]?.tone === 'flat', kpiTones[0]);
check('失败操作亮黄灯', kpiTones[1]?.tone === 'warn', kpiTones[1]);
check('留痕断流亮红灯（写入链路可能已断）',
  kpiTones[2]?.tone === 'bad' && String(kpiTones[2]?.label).includes('摄入链路'), kpiTones[2]);
/* 108000 秒 = 30h，前端 formatDuration 对 ≥1 天折算成"1.3天"。
   这里只断言走的是 DURATION 口径（带天/小时单位），不写死是"小时"——
   前端 formatValue 对 86400s 以上折算成天，把它当小时断言会误判。 */
check('断流时长排版带单位（DURATION 口径，不是裸秒数）',
  /天$|小时$/.test(kpiTones[2]?.value ?? ''), kpiTones[2]?.value);
check('归档陈旧亮黄灯并标注单位"天"',
  kpiTones[3]?.tone === 'warn' && String(kpiTones[3]?.label).includes('天'), kpiTones[3]);

/* ---- 3. 折线单序列 + 柱图五类目 ---- */
const charts = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="qiju"]');
  const svg = scope?.querySelector('.bwsp-linewrap svg.bwsp-line');
  return {
    polylines: svg ? [...svg.querySelectorAll('polyline')].length : 0,
    labels: [...(scope?.querySelectorAll('.bwsp-barlabels span') ?? [])].map((item) => item.textContent),
  };
});
check('折线只画一条序列（审计）', charts.polylines === 1, charts.polylines);
check('横轴是 7 天（缺日补 0 后仍 7 个刻度）', charts.labels.length === 7, charts.labels);

/* ---- 4. 有搜索框：关键字参数是 username，不是 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="qiju"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  return {
    searchHidden: input ? input.hidden : null,
    searchPlaceholder: input?.placeholder ?? '',
    status: scope.querySelector('.bwsp-status') ? [...scope.querySelector('.bwsp-status').options]
      .map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('有搜索框（审计日志接口支持 username 模糊匹配）', filters?.searchHidden === false, filters);
check('搜索框提示语是"搜索操作人…"（不是泛泛的"搜索…"）',
  String(filters?.searchPlaceholder).includes('操作人'), filters?.searchPlaceholder);
check('状态筛选值用后端 bizStatus 原值（0/1），失败在前',
  filters?.status.join(',') === ':全部结果,0:失败,1:成功', filters?.status);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 username/bizStatus 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.username === null && queried[0]?.bizStatus === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="qiju"] .bwsp-table tbody tr')].map((tr) => ({
    time: tr.children[0]?.textContent ?? '',
    username: tr.children[1]?.textContent ?? '',
    module: tr.children[2]?.textContent ?? '',
    operateType: tr.children[3]?.textContent ?? '',
    result: tr.children[4]?.textContent ?? '',
    resultTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('操作时间到分钟（ISO 串按位置切）', /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.time ?? ''), rows[0]?.time);
check('操作人原样显示', rows[0]?.username === 'admin', rows.map((row) => row.username));
check('模块原样显示后端值', rows[0]?.module === 'whale-auth', rows[0]?.module);
check('操作类型原样显示枚举（GRANT）', rows[0]?.operateType === 'GRANT', rows[0]?.operateType);
check('bizStatus=1 译成"成功"并打绿灯（数字经 String() 查字典）',
  rows[0]?.result === '成功' && rows[0]?.resultTone === 'good', rows[0]);
check('bizStatus=0 译成"失败"并打红灯（不是"进行中"中性色）',
  rows[1]?.result === '失败' && rows[1]?.resultTone === 'bad', rows[1]);
await page.screenshot({ path: `${OUT}/73-qiju-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="qiju"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="qiju"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="qiju"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'qiju', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 关键字搜索：发 username，不发 keyword ---- */
await page.locator(SCOPE + '.bwsp-search').fill('qa_admin');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
const lastSearch = queried.filter((item) => item.username === 'qa_admin').pop();
check('关键字按 username=qa_admin 发给服务端（不是自造 keyword 参数）',
  Boolean(lastSearch), queried.slice(-3));
check('搜索时也不夹带 keyword', lastSearch ? !lastSearch.names.includes('keyword') : false, lastSearch?.names);
check('筛选后只剩 qa_admin 的 2 条', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(700);

/* ---- 8. 状态筛选：发 bizStatus=0 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('0');
await page.waitForTimeout(800);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
check('状态按 bizStatus=0 发给服务端', queried.some((item) => item.bizStatus === '0'), queried.slice(-3));
check('筛选后只剩失败的 2 条', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(700);

/* ---- 9. 分页：第 2 页 2 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口，详情用列表行数据 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（没有 /audit/v1/log/{id} 请求）',
  detailHits.length === 0, detailHits);
check('详情字段用列表行数据（审计页无 detailFields，不额外定义分录）',
  probe?.detail.fields === 0, probe?.detail);
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('qiju'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="qiju"]');
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
await page.route('**/audit/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/audit/v1/log/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="qiju"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="qiju"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('qiju');
  const empty = document.querySelector('[data-screen-id="qiju"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="qiju"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/74-qiju-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
