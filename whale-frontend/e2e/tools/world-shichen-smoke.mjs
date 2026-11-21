// 时辰殿定时任务（S5 / T5.5）冒烟：屏风里是"任务还在不在按点跑"的真业务页。
//
// 与前面几块屏风的分工：
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   度支殿（T5.3）验"接口支持 keyword 就给搜索框"；
//   藏书殿（T5.4）验"接口有 keyword、没有 status，于是只给搜索框"；
//   这条验时辰殿自己的四件事：
//     ① 概览四张 KPI 是"存量 + 流动 + 静默"（任务总数 / 今日执行 / 今日失败 / 静默任务）；
//     ② **keyword 与 status 两个筛选都给**——`JobInfoQueryRequest` 真支持这两个参数；
//     ③ 状态值是 `'1'/'0'` 而不是自造枚举（发错值后端不报错、只是不过滤）；
//     ④ 「已停止」是灰灯不是红灯：停用是主动决定，不是故障。
//
// 数据来源：page.route 拦 /job/overview 与 /job/v1/info/page，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着六块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="shichen"] ';

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
  service: 'job',
  nodeKey: 'shichen-hall',
  title: '时辰殿',
  subtitle: '定时调度 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'jobs', label: '任务总数', value: 9, format: 'count', tone: 'flat', hint: '运行中 7 · 已停 2', display: '9' },
    { key: 'runs', label: '今日执行', value: 96, format: 'count', tone: 'flat', hint: '成功 93 · 97%', display: '96' },
    { key: 'failures', label: '今日失败', value: 3, format: 'count', tone: 'warn', hint: '涉及 2 个任务', display: '3' },
    { key: 'quiet', label: '静默任务', value: 1, format: 'count', tone: 'warn', hint: '运行中 · 24h 无执行', display: '1' },
  ],
  charts: [
    {
      key: 'run-trend',
      title: '近 7 日执行',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '执行', values: [88, 92, 0, 90, 95, 97, 96] }],
    },
    {
      key: 'fail-top',
      title: '近 7 日失败最多',
      type: 'bar',
      format: 'count',
      categories: ['执行日志归档', '商城订单超时'],
      series: [{ name: '失败', values: [2, 1] }],
    },
  ],
  alerts: [{ level: 'warning', text: '今日失败占比 3%，涉及 2 个任务', count: 3 }],
  links: [{ key: 'jobs', label: '定时任务', path: '/job/v1/info/page', permission: 'sys:job:list' }],
  notes: ['口径：只数已结束的执行（成功 + 失败，不含执行中），静默任务按 24 小时窗口、只算运行中的任务，均限本租户'],
};

/** 9 个任务：故意混 2 个已停止，用来验徽标灯色与状态筛选 */
const JOBS = [
  { id: 8001, jobName: '执行日志清理', jobGroup: 'SYSTEM', executorApp: 'whale-job', jobCode: 'logCleanup', cronExpression: '0 0 3 * * ?', handlerBean: 'jobLogCleanupHandler', handlerParam: null, misfirePolicy: 'SMART', maxRetry: 0, retryIntervalSeconds: 60, shardTotal: 1, timeoutSeconds: 0, failThreshold: 3, failCount: 0, status: 1, remark: '内置任务：逻辑删除超过保留期的 job_log', createTime: '2026-09-01T10:00:00', updateTime: '2026-11-20T03:00:02', nextFireTime: '2026-11-22T03:00:00' },
  { id: 8002, jobName: '执行日志归档', jobGroup: 'SYSTEM', executorApp: 'whale-job', jobCode: 'logArchive', cronExpression: '0 0 4 * * ?', handlerBean: 'jobLogArchiveHandler', handlerParam: null, misfirePolicy: 'SMART', maxRetry: 1, retryIntervalSeconds: 300, shardTotal: 1, timeoutSeconds: 600, failThreshold: 3, failCount: 2, status: 1, remark: '导出过期日志到 whale-file 后再清理', createTime: '2026-09-01T10:00:00', updateTime: '2026-11-21T04:02:10', nextFireTime: '2026-11-22T04:00:00' },
  { id: 8003, jobName: 'CRM 客户沉睡回访', jobGroup: 'CRM', executorApp: 'bw-crm', jobCode: 'customerWakeup', cronExpression: '0 0 9 * * ?', handlerBean: 'customerWakeupHandler', handlerParam: '{"days":30}', misfirePolicy: 'FIRE_ONCE', maxRetry: 2, retryIntervalSeconds: 120, shardTotal: 4, timeoutSeconds: 1800, failThreshold: 5, failCount: 1, status: 1, remark: '30 天未成交客户生成回访任务', createTime: '2026-10-11T14:20:00', updateTime: '2026-11-21T09:01:30', nextFireTime: '2026-11-22T09:00:00' },
  { id: 8004, jobName: 'PDM 图纸归档', jobGroup: 'PDM', executorApp: 'bw-pdm', jobCode: 'drawingArchive', cronExpression: '0 30 2 * * ?', handlerBean: 'drawingArchiveHandler', handlerParam: null, misfirePolicy: 'DO_NOTHING', maxRetry: 0, retryIntervalSeconds: 60, shardTotal: 1, timeoutSeconds: 3600, failThreshold: 3, failCount: 0, status: 0, remark: '旧版已停用，改由文件服务统一归档', createTime: '2026-07-02T09:00:00', updateTime: '2026-11-01T00:00:00', nextFireTime: null },
  { id: 8005, jobName: 'OA 待办超时提醒', jobGroup: 'OA', executorApp: 'bw-oa', jobCode: 'todoRemind', cronExpression: '0 0/30 * * * ?', handlerBean: 'todoRemindHandler', handlerParam: null, misfirePolicy: 'SMART', maxRetry: 1, retryIntervalSeconds: 60, shardTotal: 1, timeoutSeconds: 300, failThreshold: 3, failCount: 0, status: 1, remark: '每 30 分钟扫一次超时待办', createTime: '2026-08-18T11:00:00', updateTime: '2026-11-21T08:30:00', nextFireTime: '2026-11-21T09:00:00' },
  { id: 8006, jobName: 'LMS 学时结算', jobGroup: 'LMS', executorApp: 'bw-lms', jobCode: 'creditSettle', cronExpression: '0 0 1 1 * ?', handlerBean: 'creditSettleHandler', handlerParam: null, misfirePolicy: 'SMART', maxRetry: 3, retryIntervalSeconds: 600, shardTotal: 1, timeoutSeconds: 7200, failThreshold: 3, failCount: 0, status: 1, remark: '每月 1 日结算上月学时', createTime: '2026-06-01T09:30:00', updateTime: '2026-11-01T01:20:00', nextFireTime: '2026-12-01T01:00:00' },
  { id: 8007, jobName: '商城订单超时关闭', jobGroup: 'MALL', executorApp: 'bw-mall', jobCode: 'orderClose', cronExpression: '0 0/5 * * * ?', handlerBean: 'orderCloseHandler', handlerParam: '{"minutes":30}', misfirePolicy: 'SMART', maxRetry: 0, retryIntervalSeconds: 60, shardTotal: 2, timeoutSeconds: 120, failThreshold: 5, failCount: 1, status: 1, remark: '30 分钟未支付自动关单', createTime: '2026-05-20T16:00:00', updateTime: '2026-11-21T08:55:00', nextFireTime: '2026-11-21T09:00:00' },
  { id: 8008, jobName: '文件分享过期清理', jobGroup: 'FILE', executorApp: 'bw-file', jobCode: 'shareCleanup', cronExpression: '0 0 5 * * ?', handlerBean: 'shareCleanupHandler', handlerParam: null, misfirePolicy: 'SMART', maxRetry: 1, retryIntervalSeconds: 300, shardTotal: 1, timeoutSeconds: 900, failThreshold: 3, failCount: 0, status: 1, remark: '清理到期外链', createTime: '2026-09-14T10:10:00', updateTime: '2026-11-21T05:00:40', nextFireTime: '2026-11-22T05:00:00' },
  { id: 8009, jobName: '消息推送重试', jobGroup: 'MSG', executorApp: 'bw-message', jobCode: 'pushRetry', cronExpression: '0 0/10 * * * ?', handlerBean: 'pushRetryHandler', handlerParam: null, misfirePolicy: 'DO_NOTHING', maxRetry: 2, retryIntervalSeconds: 60, shardTotal: 1, timeoutSeconds: 240, failThreshold: 3, failCount: 4, status: 0, remark: '改用 MQ 重试后停用', createTime: '2026-04-08T15:00:00', updateTime: '2026-10-20T00:00:00', nextFireTime: null },
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

await page.route('**/job/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/job/v1/info/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 JobInfoQueryRequest 的两个筛选参数就叫 keyword 与 status（1 运行中 / 0 已停止）
  const keyword = url.searchParams.get('keyword');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    keyword,
    status,
    /* 把全部参数名记下来，用来断言"没有发明接口不认的参数" */
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = JOBS.filter((row) => (keyword ? row.jobName.includes(keyword) : true))
    .filter((row) => (status === null || status === '' ? true : String(row.status) === status));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是定时任务页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shichen"]');
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
check('时辰殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是时辰殿 · 定时任务', String(mounted?.title).includes('定时任务'), mounted?.title);
check('入口标签来自概览 links（定时任务）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '定时任务', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI（存量/流动/失败/静默）+ 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（任务总数/今日执行/今日失败/静默任务）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日执行 + 失败最多）', probe?.overview.charts === 2, probe?.overview.charts);

const kpiTones = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shichen"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('失败 KPI 亮黄灯（3 次失败不是正常态）', kpiTones[2]?.tone === 'warn', kpiTones[2]);
check('静默 KPI 亮黄灯（1 个运行中任务 24h 没动）', kpiTones[3]?.tone === 'warn', kpiTones[3]);
check('今日执行副标带成功率（分母是已结束执行）',
  String(kpiTones[1]?.label).includes('97%'), kpiTones[1]?.label);

/* ---- 3. 两个筛选都要给：keyword 与 status 后端都支持 ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="shichen"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const status = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    /* 渲染器永远创建这个 select，没有 statusParam 时置 hidden——
       所以要判"可见"而不是"存在" */
    statusVisible: status ? !status.hidden : false,
    statusOptions: status ? [...status.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('有搜索框（任务接口支持 keyword 模糊）', filters?.searchHidden === false, filters);
check('搜索框提示写的是真字段（任务名）', String(filters?.placeholder).includes('任务名'), filters?.placeholder);
check('有状态下拉（接口支持 status）', filters?.statusVisible === true, filters);
check('状态值用后端的 1/0，不用自造枚举',
  filters?.statusOptions?.join(',') === ':全部状态,1:运行中,0:已停止', filters?.statusOptions);

/* ---- 4. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword/status）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null
    && queried[0]?.status === null
    && queried[0].names.every((name) => ['current', 'size'].includes(name)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shichen"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    group: tr.children[1]?.textContent ?? '',
    cron: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    next: tr.children[4]?.textContent ?? '',
  })),
);
check('任务名列是真任务名', rows[0]?.name === '执行日志清理', rows.map((row) => row.name));
check('Cron 原样显示（不在前端翻译成人话）', rows[0]?.cron === '0 0 3 * * ?', rows.map((row) => row.cron));
check('运行中给绿灯', rows[0]?.status === '运行中' && rows[0]?.statusTone === 'good', rows[0]);
check('下次执行时间到分钟（ISO 串按位置切，不做时区换算）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.next ?? ''), rows.map((row) => row.next));

await page.screenshot({ path: `${OUT}/40-shichen-list.png` });

/* ---- 5. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="shichen"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="shichen"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="shichen"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'shichen', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 6. 关键字搜索：参数名是 keyword，且结果真被筛过 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('归档');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
const lastKeyword = queried.filter((item) => item.keyword === '归档').pop();
check('关键字按 keyword 发给服务端', Boolean(lastKeyword), queried.slice(-3));
check('关键字确实筛掉其它行（2 条）', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(800);

/* ---- 7. 状态筛选：发的是 '1'，不是 RUNNING 这类自造值 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('1');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
const lastStatus = queried.filter((item) => item.status === '1').pop();
check('状态按 status=1 发给服务端（后端只认 1/0）', Boolean(lastStatus), queried.slice(-3));
check('筛选后只剩运行中的 7 个', probe?.list.total === 7, probe?.list);
/* 筛完再看一眼第 2 页：已停止的那两个不该混进来 */
const filteredNames = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shichen"] .bwsp-table tbody tr')]
    .map((tr) => tr.children[0]?.textContent ?? ''),
);
check('列表里没有已停止的任务', !filteredNames.includes('PDM 图纸归档') && !filteredNames.includes('消息推送重试'), filteredNames);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(800);

/* ---- 8. 分页：第 2 页 3 条，含两个已停止（灰灯不是红灯） ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
const stoppedRow = await page.evaluate(() => {
  const tr = [...document.querySelectorAll('[data-screen-id="shichen"] .bwsp-table tbody tr')]
    .find((row) => row.children[0]?.textContent === '消息推送重试');
  if (!tr) return null;
  const badge = tr.children[3]?.querySelector('.bwsp-badge');
  return { text: badge?.textContent ?? '', tone: badge?.dataset.tone ?? '' };
});
check('已停止给灰灯（停用是主动决定，不是故障）',
  stoppedRow?.text === '已停止' && stoppedRow?.tone === 'flat', stoppedRow);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(500);

/* ---- 9. 点行看详情（同一份快照，不再二次请求） ---- */
const beforeDetail = queried.length;
await page.locator(SCOPE + '.bwsp-table tbody tr').nth(2).click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是任务名', probe?.detail.title === 'CRM 客户沉睡回访', probe?.detail.title);
check('详情 12 个字段（含执行器/错过策略/累计失败）', probe?.detail.fields === 12, probe?.detail);
check('详情不再打二次接口（列表行就是详情）', queried.length === beforeDetail, queried.length - beforeDetail);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shichen"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的执行器 Bean 没被吞掉',
  detailText.some((text) => text.includes('customerWakeupHandler')), detailText.slice(0, 12));
check('详情里的错过策略是原文（FIRE_ONCE 不翻译）',
  detailText.some((text) => text === 'FIRE_ONCE'), detailText.slice(0, 12));
await page.screenshot({ path: `${OUT}/41-shichen-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shichen'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 10. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shichen"]');
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
await page.route('**/job/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/job/v1/info/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="shichen"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="shichen"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('shichen');
  const empty = document.querySelector('[data-screen-id="shichen"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="shichen"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/42-shichen-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
