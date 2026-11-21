// 考功殿表单定义（S5 / T5.7）冒烟：屏风里是"哪些表在收数、哪张刚改过、有几个字段"的真业务页。
//
// 与前面几块屏风的分工：
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   度支殿（T5.3）验"接口支持 keyword 就给搜索框"；
//   藏书殿（T5.4）验"关键字参数名要跟接口真字段一致（originalName 不是 keyword）"；
//   时辰殿（T5.5）验"keyword + status 两个筛选都给、状态值用后端原值"；
//   政事殿（T5.6）验"多序列折线不能只画 series[0]、列级状态字典不能串味"；
//   这条验考功殿自己的四件事：
//     ① 搜索参数名是 `name`（定义接口只认 name/code/status/category，**不认 keyword**）；
//     ② 单序列折线不画图例（图例只会把 KPI 里已有的数重复一遍，白占一行高度）；
//     ③ 详情里的字段配置是真请求来的（列表行没有 fields），且"禁用"表单给灰灯不是红灯——
//        停用是主动决定，不是故障；
//     ④ 概览"待修差异"亮黄灯、告警点名"主表与物理表不一致"（列表看得到、详情可能是空的）。
//
// 数据来源：page.route 拦 /form/overview、/form/v1/definition/page 与
// /form/v1/definition/{id}，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着八块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="kaogong"] ';

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
  service: 'form',
  nodeKey: 'kaogong-hall',
  title: '考功殿',
  subtitle: '表单台账 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'forms', label: '启用表单', value: 9, format: 'count', tone: 'flat', hint: '共 12 个定义 · 停用 3', display: '9' },
    { key: 'submits', label: '今日提交', value: 12, format: 'count', tone: 'flat', hint: '近 7 日 40 条', display: '12' },
    { key: 'pending', label: '待审批', value: 6, format: 'count', tone: 'warn', hint: '其中超 24h 未动 3', display: '6' },
    { key: 'diff', label: '待修差异', value: 2, format: 'count', tone: 'warn', hint: '主表与物理表不一致', display: '2' },
  ],
  charts: [
    {
      key: 'submit-trend',
      title: '近 7 日提交',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '提交', values: [3, 0, 8, 5, 6, 6, 12] }],
    },
    {
      key: 'form-top',
      title: '近 7 日提交最多',
      type: 'bar',
      format: 'count',
      categories: ['差旅费用报销…', '请假单', '采购申请'],
      series: [{ name: '提交', values: [12, 7, 4] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '有 2 条数据主表与物理表不一致，列表里看得到、详情可能是空的', count: 2 },
    { level: 'warning', text: '有 3 条数据卡在审批中超过 24 小时（审批中只读，业务方既改不了也删不了）', count: 3 },
  ],
  links: [
    { key: 'forms', label: '表单定义', path: '/form/v1/definition/page', permission: 'sys:form:list' },
  ],
  notes: ['口径：只数有效数据（未删除且未作废），待审批为累计量、超时按 24 小时窗口，差异数为与物理表对账后仍待处理的条数，均限本租户'],
};

/** 9 条定义：混正常/禁用，且含一条"报销"与三条禁用，用来验徽标灯色与两个筛选 */
const DEFINITIONS = [
  { id: 8101, code: 'expense_trip', name: '差旅费用报销单', tableDefinitionId: 41, version: 4, status: 1, category: '财务', remark: '按季度结算', tags: '["报销","财务"]', createTime: '2026-09-01T10:00:00', updateTime: '2026-11-21T08:40:00' },
  { id: 8102, code: 'leave_request', name: '请假申请单', tableDefinitionId: 42, version: 5, status: 1, category: '人事', remark: '', tags: null, createTime: '2026-08-12T09:20:00', updateTime: '2026-11-20T18:02:00' },
  { id: 8103, code: 'purchase_apply', name: '采购申请单', tableDefinitionId: 43, version: 3, status: 1, category: '采购', remark: '需部门负责人审批', tags: '["采购"]', createTime: '2026-07-30T14:00:00', updateTime: '2026-11-20T11:05:00' },
  { id: 8104, code: 'seal_apply', name: '用印申请单', tableDefinitionId: 44, version: 2, status: 1, category: '行政', remark: '', tags: null, createTime: '2026-06-18T09:00:00', updateTime: '2026-11-19T16:10:00' },
  { id: 8105, code: 'overtime_apply', name: '加班申请单', tableDefinitionId: 45, version: 3, status: 1, category: '人事', remark: '', tags: null, createTime: '2026-06-01T08:30:00', updateTime: '2026-11-18T20:05:00' },
  { id: 8106, code: 'asset_borrow', name: '资产借用单', tableDefinitionId: 46, version: 1, status: 1, category: '行政', remark: '', tags: null, createTime: '2026-05-20T10:00:00', updateTime: '2026-11-17T09:10:00' },
  { id: 8107, code: 'legacy_gift', name: '旧版礼品申领单', tableDefinitionId: null, version: 1, status: 0, category: '行政', remark: '已由新版替代', tags: null, createTime: '2025-12-01T10:00:00', updateTime: '2026-10-08T10:00:00' },
  { id: 8108, code: 'legacy_car', name: '旧版用车申请单', tableDefinitionId: null, version: 2, status: 0, category: '行政', remark: '并入后勤单', tags: null, createTime: '2025-11-11T10:00:00', updateTime: '2026-09-15T10:00:00' },
  { id: 8109, code: 'legacy_visit', name: '旧版来访登记单', tableDefinitionId: null, version: 1, status: 0, category: '行政', remark: '', tags: null, createTime: '2025-10-01T10:00:00', updateTime: '2026-08-01T10:00:00' },
];

/** 报销单的字段配置：详情接口才带回来（列表行里没有） */
const FIELDS_8101 = [
  { id: 92001, formId: 8101, columnMetaId: 411, fieldLabel: '出差事由', widgetType: 'textarea', showInList: 1, showInForm: 1, editable: 1, sortNo: 1, colSpan: 24, status: 1 },
  { id: 92002, formId: 8101, columnMetaId: 412, fieldLabel: '费用金额', widgetType: 'number', showInList: 1, showInForm: 1, editable: 1, sortNo: 2, colSpan: 12, status: 1 },
  { id: 92003, formId: 8101, columnMetaId: 413, fieldLabel: '费用类型', widgetType: 'select', showInList: 1, showInForm: 1, editable: 1, sortNo: 3, colSpan: 12, status: 1 },
  { id: 92004, formId: 8101, columnMetaId: 414, fieldLabel: '发票扫描件', widgetType: 'text', showInList: 0, showInForm: 1, editable: 1, sortNo: 4, colSpan: 24, status: 0 },
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

await page.route('**/form/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

await page.route('**/form/v1/definition/*', async (route) => {
  const url = new URL(route.request().url());
  const id = Number(url.pathname.split('/').pop());
  detailHits.push(id);
  const found = DEFINITIONS.find((row) => row.id === id);
  const data = found ? { ...found, fields: id === 8101 ? FIELDS_8101 : [] } : null;
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.route('**/form/v1/definition/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 FormQueryRequest 的条件叫 name / code / status / category
  const name = url.searchParams.get('name');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    name,
    status,
    /* 把全部参数名记下来，用来断言"没有发明接口不认的参数（例如 keyword）" */
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = DEFINITIONS.filter((row) => (name ? row.name.includes(name) : true))
    .filter((row) => (status === null || status === '' ? true : String(row.status) === status));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是表单定义页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="kaogong"]');
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
check('考功殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是考功殿 · 表单定义', String(mounted?.title).includes('表单定义'), mounted?.title);
check('入口标签来自概览 links（表单定义）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '表单定义', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI（启用表单/今日提交/待审批/待修差异）+ 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（启用表单/今日提交/待审批/待修差异）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日提交 + Top 表单）', probe?.overview.charts === 2, probe?.overview.charts);
check('两条告警都下发（差异 + 超时待审，根因不同）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiTones = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="kaogong"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('启用表单 KPI 副标写停用数（考核基数不能虚高）',
  String(kpiTones[0]?.label).includes('共 12 个定义 · 停用 3'), kpiTones[0]?.label);
check('启用表单不亮灯（停用是主动决定，不是故障）', kpiTones[0]?.tone === 'flat', kpiTones[0]);
check('今日提交副标写近 7 日总量', String(kpiTones[1]?.label).includes('近 7 日 40 条'), kpiTones[1]?.label);
check('待审批亮黄灯（有 3 条超 24h 未动）', kpiTones[2]?.tone === 'warn', kpiTones[2]);
check('待审批副标写超时数', String(kpiTones[2]?.label).includes('超 24h 未动 3'), kpiTones[2]?.label);
check('待修差异亮黄灯（主从不一致）', kpiTones[3]?.tone === 'warn', kpiTones[3]);
check('待修差异副标点明"主表与物理表不一致"',
  String(kpiTones[3]?.label).includes('主表与物理表不一致'), kpiTones[3]?.label);

/* ---- 3. 单序列折线：只画一条、不画图例 ---- */
const line = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="kaogong"]');
  const svg = scope?.querySelector('.bwsp-linewrap svg.bwsp-line');
  return {
    polylines: svg ? [...svg.querySelectorAll('polyline')].length : 0,
    legend: [...(scope?.querySelectorAll('.bwsp-linewrap .bwsp-legend-item') ?? [])]
      .map((item) => item.textContent),
    labels: [...(scope?.querySelectorAll('.bwsp-barlabels span') ?? [])].map((item) => item.textContent),
  };
});
check('折线只画一条序列（提交）', line.polylines === 1, line.polylines);
check('单序列不画图例（图例只会重复 KPI 里已有的数）', line.legend.length === 0, line.legend);
check('横轴是 7 天（缺日补 0 后仍 7 个刻度）', line.labels.length === 7, line.labels);

/* ---- 4. 搜索参数名是 name，不是 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="kaogong"]');
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
check('有搜索框（定义接口支持 name 模糊）', filters?.searchHidden === false, filters);
check('搜索框提示写的是真字段（表单名称）', String(filters?.placeholder).includes('表单名称'), filters?.placeholder);
check('有状态下拉（接口支持 status）', filters?.statusVisible === true, filters);
check('状态值用后端的 1/0，不用自造枚举',
  filters?.statusOptions?.join(',') === ':全部状态,1:正常,0:禁用',
  filters?.statusOptions);

/* ---- 5. 首屏列表：9 条分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword/name）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.name === null
    && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="kaogong"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    code: tr.children[1]?.textContent ?? '',
    category: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    updated: tr.children[4]?.textContent ?? '',
  })),
);
check('表单名列是真表单名', rows[0]?.name === '差旅费用报销单', rows.map((row) => row.name));
check('编码原样显示（不在前端翻译成人话）', rows[0]?.code === 'expense_trip', rows.map((row) => row.code));
check('分类原样显示', rows[0]?.category === '财务', rows.map((row) => row.category));
check('正常给绿灯', rows[0]?.status === '正常' && rows[0]?.statusTone === 'good', rows[0]);
check('更新时间到分钟（ISO 串按位置切，不做时区换算）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.updated ?? ''), rows.map((row) => row.updated));

await page.screenshot({ path: `${OUT}/60-kaogong-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="kaogong"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="kaogong"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="kaogong"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'kaogong', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 关键字搜索：参数名是 name，且结果真被筛过 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('报销');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
const lastName = queried.filter((item) => item.name === '报销').pop();
check('关键字按 name 发给服务端（不是 keyword）', Boolean(lastName), queried.slice(-3));
check('只发 name，不夹带 keyword', lastName ? !lastName.names.includes('keyword') : false, lastName?.names);
check('关键字确实筛掉其它行（只剩 1 条）', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(800);

/* ---- 8. 状态筛选：发的是 '0'（禁用），且禁用给灰灯 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('0');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
const lastStatus = queried.filter((item) => item.status === '0').pop();
check('状态按 status=0 发给服务端（后端只认 1/0）', Boolean(lastStatus), queried.slice(-3));
check('筛选后只剩禁用的 3 条', probe?.list.total === 3, probe?.list);
const filteredRows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="kaogong"] .bwsp-table tbody tr')].map((tr) => ({
    status: tr.children[3]?.textContent ?? '',
    tone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('列表里没有正常态的表单', filteredRows.every((row) => row.status === '禁用'), filteredRows);
check('禁用给灰灯（停用是主动决定，给红灯会让人天天来查）',
  filteredRows.every((row) => row.tone === 'flat'), filteredRows);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(800);

/* ---- 9. 分页：第 2 页 3 条，末页禁用下一页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 点行看详情：字段配置只有详情接口才有（分录表） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是表单名', probe?.detail.title === '差旅费用报销单', probe?.detail.title);
check('详情 9 个字段（含编码/分类/版本/标签）', probe?.detail.fields === 9, probe?.detail);
check('详情按 id 打了真实详情接口（字段配置只有详情里有）', detailHits.includes(8101), detailHits);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="kaogong"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的备注没被吞掉', detailText.includes('按季度结算'), detailText);
check('详情里的标签是原文 JSON（不在前端猜着拆）', detailText.includes('["报销","财务"]'), detailText);

/* ---- 11. 字段配置分录：4 行、控件类型原样、状态按字典翻译 ---- */
const lines = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="kaogong"] .bwsp-lines tbody tr')].map((tr) => ({
    label: tr.children[0]?.textContent ?? '',
    widget: tr.children[1]?.textContent ?? '',
    sort: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
  })),
);
check('字段配置 4 行', lines.length === 4, lines.length);
check('字段名是真字段名', lines[0]?.label === '出差事由', lines.map((row) => row.label));
check('控件类型保持后端原值（text/textarea，不猜中文）', lines[0]?.widget === 'textarea', lines.map((row) => row.widget));
check('排序号是数字', lines[0]?.sort === '1', lines.map((row) => row.sort));
/* 分录表只做文字翻译、不打灯（与政事殿审批链一致）：分录是配置明细/节点明细，
   不是"这一屏整体健康度"，全线亮灯反而看不出重点。 */
check('停用字段按字典译成"禁用"', lines[3]?.status === '禁用', lines.map((row) => row.status));
await page.screenshot({ path: `${OUT}/61-kaogong-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('kaogong'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 12. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="kaogong"]');
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
await page.route('**/form/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/form/v1/definition/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="kaogong"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="kaogong"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('kaogong');
  const empty = document.querySelector('[data-screen-id="kaogong"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="kaogong"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/62-kaogong-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
