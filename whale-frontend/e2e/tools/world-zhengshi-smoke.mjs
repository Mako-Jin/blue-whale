// 政事殿流程实例（S5 / T5.6）冒烟：屏风里是"哪条审批链在动、卡在谁那里"的真业务页。
//
// 与前面几块屏风的分工：
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   度支殿（T5.3）验"接口支持 keyword 就给搜索框"；
//   藏书殿（T5.4）验"关键字参数名要跟接口真字段一致（originalName 不是 keyword）"；
//   时辰殿（T5.5）验"keyword + status 两个筛选都给、状态值用后端原值"；
//   这条验政事殿自己的四件事：
//     ① 搜索参数名是 `title`（实例接口只认 title/status/definitionCode/initiator，
//        **不认 keyword**——发错名字后端静默忽略，搜索框就成了摆设）；
//     ② 两条序列的折线真的画了两条（发起 / 办结）：早前渲染器只取 series[0]，
//        第二条会被静默丢弃，图上看着正常其实少一半数；
//     ③ 详情里的审批链用**任务**状态字典翻译（"待办"），不能沿用实例字典（会成"运行中"）；
//     ④ 概览"卡住实例"亮黄灯、告警点名"审批链可能断了"。
//
// 数据来源：page.route 拦 /workflow/overview、/workflow/v1/instance/page 与
// /workflow/v1/instance/{id}，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着七块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="zhengshi"] ';

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
  service: 'workflow',
  nodeKey: 'zhengshi-hall',
  title: '政事殿',
  subtitle: '工作流 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'running', label: '运行中', value: 12, format: 'count', tone: 'flat', hint: '今日发起 30', display: '12' },
    { key: 'finished', label: '今日办结', value: 8, format: 'count', tone: 'flat', hint: '审批通过', display: '8' },
    { key: 'pending', label: '待办任务', value: 5, format: 'count', tone: 'warn', hint: '已超时 2', display: '5' },
    { key: 'stuck', label: '卡住实例', value: 1, format: 'count', tone: 'warn', hint: '运行中但无待办', display: '1' },
  ],
  charts: [
    {
      key: 'flow-trend',
      title: '近 7 日发起 / 办结',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [
        { name: '发起', values: [18, 22, 0, 25, 30, 27, 30] },
        { name: '办结', values: [15, 20, 0, 24, 26, 22, 8] },
      ],
    },
    {
      key: 'node-backlog',
      title: '待办积压 Top 5',
      type: 'bar',
      format: 'count',
      categories: ['财务复核', '部门负责人…', '总经理审批'],
      series: [{ name: '待办', values: [3, 1, 1] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '运行中流程 1 个没有任何待办，审批链可能断了', count: 1 },
    { level: 'warning', text: '待办超时 2 个，最久已超时 5 小时', count: 2 },
  ],
  links: [
    { key: 'instances', label: '流程实例', path: '/workflow/v1/instance/page', permission: 'sys:workflow:instance:list' },
  ],
  notes: ['口径：办结只算审批通过（驳回 / 撤销停在 update_time，不计入），卡住实例指运行中但没有任何待办任务的实例，均限本租户'],
};

/** 9 条实例：故意混运行中/通过/驳回/撤销，用来验徽标灯色与状态筛选 */
const INSTANCES = [
  { id: 7101, definitionId: 501, definitionCode: 'SCM_CONTRACT', definitionVersion: 3, title: '采购合同审批 · 蓝鲸云服务年框', businessKey: 'CT-2026-011', status: 0, currentIndex: 1, currentNodeKey: 'finance_review', initiator: 'zhangwei', createTime: '2026-11-21T09:12:00', updateTime: '2026-11-21T09:31:00' },
  { id: 7102, definitionId: 502, definitionCode: 'OA_LEAVE', definitionVersion: 5, title: '请假申请 · 张伟 11-24 至 11-25', businessKey: null, status: 1, currentIndex: 2, currentNodeKey: 'finish', initiator: 'zhangwei', createTime: '2026-11-20T16:40:00', updateTime: '2026-11-20T18:02:00' },
  { id: 7103, definitionId: 503, definitionCode: 'OA_EXPENSE', definitionVersion: 2, title: '报销单 · 差旅 上海', businessKey: 'EX-9912', status: 1, currentIndex: 2, currentNodeKey: 'finish', initiator: 'liuyang', createTime: '2026-11-20T11:05:00', updateTime: '2026-11-20T15:20:00' },
  { id: 7104, definitionId: 501, definitionCode: 'SCM_CONTRACT', definitionVersion: 3, title: '采购合同审批 · 服务器扩容', businessKey: 'CT-2026-012', status: 0, currentIndex: 0, currentNodeKey: 'dept_lead', initiator: 'chenjing', createTime: '2026-11-21T08:30:00', updateTime: '2026-11-21T08:30:00' },
  { id: 7105, definitionId: 504, definitionCode: 'OA_SEAL', definitionVersion: 1, title: '用印申请 · 投标文件', businessKey: null, status: 2, currentIndex: 1, currentNodeKey: 'legal_review', initiator: 'wangfang', createTime: '2026-11-19T14:00:00', updateTime: '2026-11-19T16:10:00' },
  { id: 7106, definitionId: 505, definitionCode: 'OA_OVERTIME', definitionVersion: 4, title: '加班申请 · 财务月结', businessKey: null, status: 3, currentIndex: 0, currentNodeKey: 'dept_lead', initiator: 'liuyang', createTime: '2026-11-18T20:00:00', updateTime: '2026-11-18T20:05:00' },
  { id: 7107, definitionId: 506, definitionCode: 'OA_TRIP', definitionVersion: 3, title: '出差申请 · 深圳客户拜访', businessKey: null, status: 1, currentIndex: 2, currentNodeKey: 'finish', initiator: 'chenjing', createTime: '2026-11-17T09:00:00', updateTime: '2026-11-17T17:30:00' },
  { id: 7108, definitionId: 501, definitionCode: 'SCM_CONTRACT', definitionVersion: 3, title: '采购合同审批 · 办公家具', businessKey: 'CT-2026-013', status: 0, currentIndex: 1, currentNodeKey: 'finance_review', initiator: 'zhaolei', createTime: '2026-11-21T07:55:00', updateTime: '2026-11-21T08:40:00' },
  { id: 7109, definitionId: 507, definitionCode: 'HR_REGULAR', definitionVersion: 2, title: '转正申请 · 试用期评估', businessKey: null, status: 2, currentIndex: 1, currentNodeKey: 'hr_review', initiator: 'sunqi', createTime: '2026-11-16T10:30:00', updateTime: '2026-11-17T09:10:00' },
];

/** 实例 7101 的审批链：第 1 步已同意、后两步待办（用来验任务字典与实例字典不串味） */
const TASKS_7101 = [
  { id: 91001, instanceId: 7101, nodeIndex: 0, nodeName: '部门负责人审批', nodeKey: 'dept_lead', assignee: 'zhangwei', signType: null, signPolicy: null, ownerId: 'zhangwei', delegateFrom: null, operatorType: 'USER', operatorSource: 'MANUAL', status: 1, comment: '同意，按年框执行', dueAt: null, createTime: '2026-11-21T09:12:00', updateTime: '2026-11-21T09:20:00' },
  { id: 91002, instanceId: 7101, nodeIndex: 1, nodeName: '财务复核', nodeKey: 'finance_review', assignee: 'liuyang', signType: null, signPolicy: null, ownerId: 'liuyang', delegateFrom: null, operatorType: null, operatorSource: null, status: 0, comment: null, dueAt: '2026-11-21T21:20:00', createTime: '2026-11-21T09:20:00', updateTime: '2026-11-21T09:20:00' },
  { id: 91003, instanceId: 7101, nodeIndex: 2, nodeName: '总经理审批', nodeKey: 'gm_approve', assignee: 'chenjing', signType: null, signPolicy: null, ownerId: 'chenjing', delegateFrom: null, operatorType: null, operatorSource: null, status: 0, comment: null, dueAt: null, createTime: '2026-11-21T09:20:00', updateTime: '2026-11-21T09:20:00' },
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
const detailHits = [];

await page.route('**/workflow/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/workflow/v1/instance/*', async (route) => {
  const url = new URL(route.request().url());
  const id = Number(url.pathname.split('/').pop());
  detailHits.push(id);
  const found = INSTANCES.find((row) => row.id === id);
  const data = found ? { ...found, tasks: id === 7101 ? TASKS_7101 : [] } : null;
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.route('**/workflow/v1/instance/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 ProcessInstanceQueryRequest 的条件叫 title / status / definitionCode / initiator
  const title = url.searchParams.get('title');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    title,
    status,
    /* 把全部参数名记下来，用来断言"没有发明接口不认的参数（例如 keyword）" */
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = INSTANCES.filter((row) => (title ? row.title.includes(title) : true))
    .filter((row) => (status === null || status === '' ? true : String(row.status) === status));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是流程实例页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="zhengshi"]');
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
check('政事殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是政事殿 · 流程实例', String(mounted?.title).includes('流程实例'), mounted?.title);
check('入口标签来自概览 links（流程实例）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '流程实例', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI（在途/办结/待办/卡住）+ 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（运行中/今日办结/待办任务/卡住实例）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日发起办结 + 积压 Top）', probe?.overview.charts === 2, probe?.overview.charts);

const kpiTones = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('待办 KPI 亮黄灯（2 个已超时）', kpiTones[2]?.tone === 'warn', kpiTones[2]);
check('待办 KPI 副标写超时数', String(kpiTones[2]?.label).includes('已超时 2'), kpiTones[2]?.label);
check('卡住 KPI 亮黄灯（运行中却没人有待办）', kpiTones[3]?.tone === 'warn', kpiTones[3]);
check('运行中副标写今日发起（不用看的人自己相减）',
  String(kpiTones[0]?.label).includes('今日发起 30'), kpiTones[0]?.label);

/* ---- 3. 两条序列的折线：真的画了两条 + 有图例 ---- */
const line = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="zhengshi"]');
  const svg = scope?.querySelector('.bwsp-linewrap svg.bwsp-line');
  return {
    polylines: svg ? [...svg.querySelectorAll('polyline')].length : 0,
    strokes: svg ? [...svg.querySelectorAll('polyline')].map((item) => item.getAttribute('stroke')) : [],
    legend: [...(scope?.querySelectorAll('.bwsp-linewrap .bwsp-legend-item') ?? [])]
      .map((item) => item.textContent),
    labels: [...(scope?.querySelectorAll('.bwsp-barlabels span') ?? [])].map((item) => item.textContent),
  };
});
check('折线画了两条序列（发起 / 办结）', line.polylines === 2, line.polylines);
check('两条线用不同颜色（同色等于分不清）', new Set(line.strokes).size === 2, line.strokes);
check('多序列有图例且写的是序列名', line.legend.join(',') === '发起,办结', line.legend);
check('横轴是 7 天（缺日补 0 后仍 7 个刻度）', line.labels.length === 7, line.labels);

/* ---- 4. 搜索参数名是 title，不是 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="zhengshi"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const status = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    placeholder: input?.placeholder ?? '',
    statusVisible: status ? !status.hidden : false,
    statusOptions: status ? [...status.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('有搜索框（实例接口支持 title 模糊）', filters?.searchHidden === false, filters);
check('搜索框提示写的是真字段（流程标题）', String(filters?.placeholder).includes('流程标题'), filters?.placeholder);
check('有状态下拉（接口支持 status）', filters?.statusVisible === true, filters);
check('状态值用后端的 0/1/2/3，不用自造枚举',
  filters?.statusOptions?.join(',') === ':全部状态,0:运行中,1:已通过,2:已驳回,3:已撤销',
  filters?.statusOptions);

/* ---- 5. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword/title）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.title === null
    && queried[0]?.status === null
    && queried[0].names.every((name) => ['current', 'size'].includes(name)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    code: tr.children[1]?.textContent ?? '',
    initiator: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    created: tr.children[4]?.textContent ?? '',
  })),
);
check('标题列是真标题', rows[0]?.title === '采购合同审批 · 蓝鲸云服务年框', rows.map((row) => row.title));
check('流程编码原样显示（不在前端翻译成人话）', rows[0]?.code === 'SCM_CONTRACT', rows.map((row) => row.code));
check('运行中给黄灯（未批完不算健康态）',
  rows[0]?.status === '运行中' && rows[0]?.statusTone === 'warn', rows[0]);
check('发起时间到分钟（ISO 串按位置切，不做时区换算）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.created ?? ''), rows.map((row) => row.created));

await page.screenshot({ path: `${OUT}/50-zhengshi-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="zhengshi"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="zhengshi"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="zhengshi"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'zhengshi', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 关键字搜索：参数名是 title，且结果真被筛过 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('合同');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
const lastTitle = queried.filter((item) => item.title === '合同').pop();
check('关键字按 title 发给服务端（不是 keyword）', Boolean(lastTitle), queried.slice(-3));
check('只发 title，不夹带 keyword', lastTitle ? !lastTitle.names.includes('keyword') : false, lastTitle?.names);
check('关键字确实筛掉其它行（3 条）', probe?.list.total === 3 && probe?.list.rows === 3, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(800);

/* ---- 8. 状态筛选：发的是 '0'（运行中），不是 RUNNING ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('0');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
const lastStatus = queried.filter((item) => item.status === '0').pop();
check('状态按 status=0 发给服务端（后端只认 0/1/2/3）', Boolean(lastStatus), queried.slice(-3));
check('筛选后只剩运行中的 3 条', probe?.list.total === 3, probe?.list);
const filteredCodes = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-table tbody tr')]
    .map((tr) => tr.children[3]?.textContent ?? ''),
);
check('列表里没有已通过/已驳回的实例',
  filteredCodes.every((text) => text === '运行中'), filteredCodes);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(800);

/* ---- 9. 分页：第 2 页 3 条，含驳回（红灯）与撤销（灰灯） ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
const tailRows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    tone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('已驳回给红灯', tailRows.some((row) => row.status === '已驳回' && row.tone === 'bad'), tailRows);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);
/* 已撤销那条在第 1 页最后一行（9 条按 6 条分页）：撤销是主动决定，给灰灯不是红灯 */
const headRows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    tone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('已撤销给灰灯（撤销是主动决定，不是故障）',
  headRows.some((row) => row.status === '已撤销' && row.tone === 'flat'), headRows);

/* ---- 10. 点行看详情：详情接口带上审批链（分录表） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是流程标题', probe?.detail.title === '采购合同审批 · 蓝鲸云服务年框', probe?.detail.title);
check('详情 9 个字段（含流程编码/业务键/当前节点）', probe?.detail.fields === 9, probe?.detail);
check('详情按 id 打了真实详情接口（审批链只有详情里有）', detailHits.includes(7101), detailHits);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的业务键没被吞掉', detailText.includes('CT-2026-011'), detailText);
check('详情里的当前节点是原文键（finance_review 不翻译）',
  detailText.includes('finance_review'), detailText);

/* ---- 11. 审批链状态用任务字典：'待办' 不能显示成 '运行中' ---- */
const chain = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="zhengshi"] .bwsp-lines tbody tr')].map((tr) => ({
    node: tr.children[0]?.textContent ?? '',
    assignee: tr.children[1]?.textContent ?? '',
    status: tr.children[2]?.textContent ?? '',
    comment: tr.children[3]?.textContent ?? '',
    time: tr.children[4]?.textContent ?? '',
  })),
);
check('审批链 3 行（部门/财务/总经理）', chain.length === 3, chain.length);
check('审批链节点名是真节点名', chain[0]?.node === '部门负责人审批', chain.map((row) => row.node));
check('已同意用任务字典', chain[0]?.status === '已同意', chain[0]);
check('待办用任务字典（不是实例字典的"运行中"）',
  chain[1]?.status === '待办' && chain[2]?.status === '待办', chain.map((row) => row.status));
check('审批意见原样显示（驳回理由要让人看见）', chain[0]?.comment === '同意，按年框执行', chain[0]?.comment);
await page.screenshot({ path: `${OUT}/51-zhengshi-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('zhengshi'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 12. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="zhengshi"]');
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

/* ---- 13. 接口全挂时不白屏：有旧值标"陈旧"，列表给人话 + 重试 ---- */
await page.route('**/workflow/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/workflow/v1/instance/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="zhengshi"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="zhengshi"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('zhengshi');
  const empty = document.querySelector('[data-screen-id="zhengshi"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="zhengshi"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/52-zhengshi-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
