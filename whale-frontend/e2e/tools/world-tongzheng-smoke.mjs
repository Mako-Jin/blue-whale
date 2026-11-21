// 通政殿消息记录（S5 / T5.8）冒烟：屏风里是"信送出去没有、卡在哪、被谁退回"的真业务页。
//
// 与前面几块屏风的分工：
//   枢密殿（T5.2）验"接口没有 keyword 就不给搜索框"；
//   度支殿（T5.3）验"接口支持 keyword 就给搜索框"；
//   藏书殿（T5.4）验"关键字参数名要跟接口真字段一致（originalName 不是 keyword）"；
//   时辰殿（T5.5）验"keyword + status 两个筛选都给、状态值用后端原值"；
//   政事殿（T5.6）验"多序列折线不能只画 series[0]、列级状态字典不能串味"；
//   考功殿（T5.7）验"详情分录只有详情接口才有（fields）"；
//   这条验通政殿自己的四件事：
//     ① 接口没有关键字条件 → **连搜索框都不给**（给一个按了没反应的框比没有更糟）；
//     ② 状态筛选项按"问题优先"排，值一律用后端枚举原值（FAILED/PARTIAL/PENDING…）；
//     ③ 详情里的投递明细是**另一套状态码**（READ / BOUNCED 在消息状态字典里根本不存在），
//        必须靠列级 statusMap 覆盖，否则会原样显示英文且不报错；
//     ④ 概览"积压未发"与"退信"各一盏黄灯、两条告警根因不同（收下了没送出 / 地址失效）。
//
// 数据来源：page.route 拦 /message/overview、/message/v1/record/page 与
// /message/v1/record/{id}，返回与后端同形状的信封。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着九块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="tongzheng"] ';

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
  service: 'message',
  nodeKey: 'tongzheng-hall',
  title: '通政殿',
  subtitle: '消息投递 · 11-21',
  generatedAt: '2026-11-21T09:00:00+08:00',
  state: 'live',
  kpis: [
    { key: 'today', label: '今日投递', value: 128, format: 'count', tone: 'flat', hint: '本租户 · 按渠道 × 接收人计', display: '128' },
    { key: 'rate', label: '送达率', value: 0.94, format: 'percent', tone: 'flat', hint: '近 24h · 成功 1880 / 失败 120', display: '94%' },
    { key: 'backlog', label: '积压未发', value: 12, format: 'count', tone: 'warn', hint: '超 10 分钟未投出', display: '12' },
    { key: 'bounced', label: '退信', value: 3, format: 'count', tone: 'warn', hint: '近 24h · 地址失效，重试无用', display: '3' },
  ],
  charts: [
    {
      key: 'sent-trend',
      title: '近 7 日投递',
      type: 'line',
      format: 'count',
      categories: ['11-15', '11-16', '11-17', '11-18', '11-19', '11-20', '11-21'],
      series: [{ name: '投递', values: [96, 0, 141, 118, 132, 120, 128] }],
    },
    {
      key: 'channel-fail',
      title: '近 7 日失败最多渠道',
      type: 'bar',
      format: 'count',
      categories: ['EMAIL', 'SMS', 'IM'],
      series: [{ name: '失败', values: [5, 3, 2] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '12 条消息超过 10 分钟仍未投出，业务方已认为发送成功', count: 12 },
    { level: 'warning', text: '近 24 小时 3 条退信，接收地址可能已失效（重试不会成功）', count: 3 },
  ],
  links: [
    { key: 'records', label: '消息记录', path: '/message/v1/record/page', permission: 'sys:message:record:list' },
  ],
  notes: ['口径：投递按渠道 × 接收人各记一条；送达率与 2D 投递看板同口径，取近 24 小时'],
};

/** 8 条消息记录：状态覆盖失败 / 部分失败 / 待投递 / 过期 / 正常态 */
const RECORDS = [
  { id: 9001, title: '报销审批结果通知', bizType: 'OA_APPROVAL_RESULT', bizId: 'AP-2026-0031', templateCode: 'OA_APPROVAL_RESULT', channels: 'INAPP,EMAIL', status: 'FAILED', priority: 20, createTime: '2026-11-21T08:52:00' },
  { id: 9002, title: '请假审批通过', bizType: 'OA_APPROVAL_RESULT', bizId: 'LV-2026-0112', templateCode: 'OA_APPROVAL_RESULT', channels: 'INAPP', status: 'DELIVERED', priority: 20, createTime: '2026-11-21T08:40:00' },
  { id: 9003, title: '采购待办提醒', bizType: 'OA_APPROVAL_TODO', bizId: 'PO-2026-0090', templateCode: 'OA_APPROVAL_TODO', channels: 'INAPP,IM', status: 'PARTIAL', priority: 30, createTime: '2026-11-21T08:15:00' },
  { id: 9004, title: '合同到期提醒', bizType: 'CONTRACT_EXPIRE', bizId: 'CT-1182', templateCode: 'SYSTEM_NOTICE', channels: 'EMAIL,SMS', status: 'PENDING', priority: 20, createTime: '2026-11-21T07:58:00' },
  { id: 9005, title: '月度经营报表已生成', bizType: 'REPORT_READY', bizId: 'RP-2026-10', templateCode: 'SYSTEM_NOTICE', channels: 'INAPP', status: 'EXPIRED', priority: 10, createTime: '2026-11-21T07:30:00' },
  { id: 9006, title: '系统维护通知', bizType: 'SYSTEM_MAINTENANCE', bizId: null, templateCode: 'SYSTEM_NOTICE', channels: 'INAPP,IM', status: 'SENT', priority: 20, createTime: '2026-11-21T07:05:00' },
  { id: 9007, title: '外部客户回访邀请', bizType: 'CRM_MARKETING', bizId: 'MK-771', templateCode: null, channels: 'EMAIL,SMS', status: 'SKIPPED', priority: 10, createTime: '2026-11-20T19:20:00' },
  { id: 9008, title: '演示消息（未真实投递）', bizType: 'DEMO', bizId: null, templateCode: null, channels: 'INAPP', status: 'MOCKED', priority: 10, createTime: '2026-11-20T18:00:00' },
];

/** 报销通知的投递明细：4 条渠道，含 READ / BOUNCED 这两个消息状态里不存在的码 */
const DELIVERIES_9001 = [
  { id: 71001, channel: 'INAPP', recipient: '1024', status: 'READ', providerMessageId: null, errorMessage: null, attempts: 1, sentAt: '2026-11-21T08:52:01', deliveredAt: '2026-11-21T08:52:01', readAt: '2026-11-21T09:01:20' },
  { id: 71002, channel: 'EMAIL', recipient: 'c***@bluewhale.cn', status: 'DELIVERED', providerMessageId: 'sg-77f1', errorMessage: null, attempts: 1, sentAt: '2026-11-21T08:52:02', deliveredAt: '2026-11-21T08:52:06', readAt: null },
  { id: 71003, channel: 'SMS', recipient: '138****6621', status: 'BOUNCED', providerMessageId: 'sms-9911', errorMessage: '号码不存在', attempts: 2, sentAt: '2026-11-21T08:52:03', deliveredAt: null, readAt: null },
  { id: 71004, channel: 'IM', recipient: '1024', status: 'FAILED', providerMessageId: null, errorMessage: 'IM 网关超时（3000ms）', attempts: 3, sentAt: '2026-11-21T08:52:04', deliveredAt: null, readAt: null },
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

await page.route('**/message/overview**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配，
   所以 `/{id}` 这条不会抢走 `/page`（`*` 不跨斜杠，但 page 本身就是一个段落）。 */
await page.route('**/message/v1/record/*', async (route) => {
  const url = new URL(route.request().url());
  const id = Number(url.pathname.split('/').pop());
  detailHits.push(id);
  const found = RECORDS.find((row) => row.id === id);
  const data = found ? { ...found, deliveries: id === 9001 ? DELIVERIES_9001 : [] } : null;
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.route('**/message/v1/record/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  // 后端 MessageRecordQueryRequest 的条件叫 bizType / bizId / status / channel，没有 keyword
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    status,
    /* 把全部参数名记下来，用来断言"没有发明接口不认的参数（例如 keyword）" */
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  const matched = RECORDS.filter((row) => (status === null || status === '' ? true : row.status === status));
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是消息记录页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="tongzheng"]');
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
check('通政殿屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是通政殿 · 消息记录', String(mounted?.title).includes('消息记录'), mounted?.title);
check('入口标签来自概览 links（消息记录）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '消息记录', mounted?.tabs);

/* ---- 2. 概览层：四张 KPI（今日投递/送达率/积压未发/退信）+ 两张图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('tongzheng'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（今日投递/送达率/积压未发/退信）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('两张图（7 日投递 + 失败渠道）', probe?.overview.charts === 2, probe?.overview.charts);
check('两条告警都下发（积压 + 退信，根因不同）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiTones = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="tongzheng"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('今日投递不亮灯（有量本身不是问题）',
  kpiTones[0]?.tone === 'flat' && String(kpiTones[0]?.label).includes('按渠道 × 接收人计'), kpiTones[0]);
check('送达率显示成百分比（不显示成 0.94）', /^\d+(\.\d+)?%$/.test(kpiTones[1]?.value ?? ''), kpiTones[1]?.value);
check('送达率副标写成功/失败条数（避免"100% 其实只发了 1 条"）',
  String(kpiTones[1]?.label).includes('成功 1880 / 失败 120'), kpiTones[1]?.label);
check('积压未发亮黄灯（收下了没送出）',
  kpiTones[2]?.tone === 'warn' && String(kpiTones[2]?.label).includes('超 10 分钟未投出'), kpiTones[2]);
check('退信亮黄灯（地址失效，重试无用）',
  kpiTones[3]?.tone === 'warn' && String(kpiTones[3]?.label).includes('重试无用'), kpiTones[3]);

/* ---- 3. 单序列折线：只画一条、不画图例；失败渠道柱图三个类目 ---- */
const line = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="tongzheng"]');
  const svg = scope?.querySelector('.bwsp-linewrap svg.bwsp-line');
  return {
    polylines: svg ? [...svg.querySelectorAll('polyline')].length : 0,
    legend: [...(scope?.querySelectorAll('.bwsp-linewrap .bwsp-legend-item') ?? [])]
      .map((item) => item.textContent),
    labels: [...(scope?.querySelectorAll('.bwsp-barlabels span') ?? [])].map((item) => item.textContent),
  };
});
check('折线只画一条序列（投递）', line.polylines === 1, line.polylines);
check('单序列不画图例（图例只会重复 KPI 里已有的数）', line.legend.length === 0, line.legend);
check('横轴是 7 天（缺日补 0 后仍 7 个刻度）', line.labels.length === 7, line.labels);

/* ---- 4. 没有搜索框：接口没有关键字条件就不给框 ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="tongzheng"]');
  if (!scope) return null;
  const input = scope.querySelector('.bwsp-search');
  const status = scope.querySelector('.bwsp-status');
  return {
    searchHidden: input ? input.hidden : null,
    statusVisible: status ? !status.hidden : false,
    statusOptions: status ? [...status.options].map((option) => `${option.value}:${option.textContent}`) : [],
  };
});
check('没有搜索框（记录接口只认 bizType/bizId/status/channel，不认 keyword）',
  filters?.searchHidden === true, filters);
check('有状态下拉（接口支持 status）', filters?.statusVisible === true, filters);
check('状态值用后端枚举原值，且"问题优先"排在最前',
  filters?.statusOptions?.slice(0, 5).join(',')
    === ':全部状态,FAILED:失败,PARTIAL:部分失败,PENDING:待投递,DISPATCHING:投递中',
  filters?.statusOptions);

/* ---- 5. 首屏列表：8 条分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 8 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 8 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / bizType）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const rows = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="tongzheng"] .bwsp-table tbody tr')].map((tr) => ({
    title: tr.children[0]?.textContent ?? '',
    bizType: tr.children[1]?.textContent ?? '',
    channels: tr.children[2]?.textContent ?? '',
    status: tr.children[3]?.textContent ?? '',
    statusTone: tr.children[3]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    created: tr.children[4]?.textContent ?? '',
  })),
);
check('标题列是真标题', rows[0]?.title === '报销审批结果通知', rows.map((row) => row.title));
check('业务类型原样显示（不在前端翻译成人话）', rows[0]?.bizType === 'OA_APPROVAL_RESULT', rows.map((row) => row.bizType));
check('渠道列原样显示后端逗号串', rows[0]?.channels === 'INAPP,EMAIL', rows.map((row) => row.channels));
check('失败给红灯', rows[0]?.status === '失败' && rows[0]?.statusTone === 'bad', rows[0]);
check('待投递给黄灯（不是"进行中"的中性色：它意味着收件人什么都收不到）',
  rows[3]?.status === '待投递' && rows[3]?.statusTone === 'warn', rows[3]);
check('创建时间到分钟（ISO 串按位置切，不做时区换算）',
  /^\d{2}-\d{2} \d{2}:\d{2}$/.test(rows[0]?.created ?? ''), rows.map((row) => row.created));

await page.screenshot({ path: `${OUT}/70-tongzheng-list.png` });

/* ---- 6. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="tongzheng"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="tongzheng"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="tongzheng"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'tongzheng', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 7. 状态筛选：只发 status，值用后端枚举原值 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('FAILED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('tongzheng'));
const lastStatus = queried.filter((item) => item.status === 'FAILED').pop();
check('状态按 status=FAILED 发给服务端（不是自造中文值）', Boolean(lastStatus), queried.slice(-3));
check('筛选时也不夹带 keyword', lastStatus ? !lastStatus.names.includes('keyword') : false, lastStatus?.names);
check('筛选后只剩失败的 1 条', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(800);

/* ---- 8. 分页：第 2 页 2 条，末页禁用下一页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('tongzheng'));
check('第 2 页只剩 2 条', probe?.list.page === 2 && probe?.list.rows === 2, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 9. 点行看详情：投递明细只有详情接口才有（分录表） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('tongzheng'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是消息标题', probe?.detail.title === '报销审批结果通知', probe?.detail.title);
check('详情 8 个字段（含业务标识/模板/优先级）', probe?.detail.fields === 8, probe?.detail);
check('详情按 id 打了真实详情接口（投递明细只有详情里有）', detailHits.includes(9001), detailHits);
const detailText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="tongzheng"] .bwsp-kv > dd')].map((el) => el.textContent),
);
check('详情里的模板编码没被吞掉', detailText.includes('OA_APPROVAL_RESULT'), detailText);

/* ---- 10. 投递明细分录：4 行，且用列级字典翻译（READ/BOUNCED 消息状态里没有） ---- */
const lines = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="tongzheng"] .bwsp-lines tbody tr')].map((tr) => ({
    channel: tr.children[0]?.textContent ?? '',
    recipient: tr.children[1]?.textContent ?? '',
    status: tr.children[2]?.textContent ?? '',
    tone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    error: tr.children[3]?.textContent ?? '',
    sentAt: tr.children[4]?.textContent ?? '',
  })),
);
check('投递明细 4 行', lines.length === 4, lines.length);
check('渠道是真渠道', lines[0]?.channel === 'INAPP', lines.map((row) => row.channel));
check('接收人已脱敏（后端脱敏，前端不再处理）', lines[1]?.recipient === 'c***@bluewhale.cn', lines.map((row) => row.recipient));
/* 分录表只做文字翻译、不打灯（与政事殿审批链、考功殿字段配置一致）：
   分录是明细，不是"这一屏整体健康度"。这里要验的是**列级字典生效**——
   READ / BOUNCED 这两个码在消息状态字典里根本不存在，若串用页面级字典会原样显示英文。 */
check('READ 译成"已读"（列级字典生效，否则会原样显示 READ）',
  lines[0]?.status === '已读', lines[0]);
check('BOUNCED 译成"退信"（消息状态字典里没有这个码）',
  lines[2]?.status === '退信', lines[2]);
check('FAILED 译成"失败"', lines[3]?.status === '失败', lines[3]);
check('失败原因原样显示', lines[2]?.error === '号码不存在', lines.map((row) => row.error));
check('投递时间到分钟', /^\d{2}-\d{2} \d{2}:\d{2}$/.test(lines[0]?.sentAt ?? ''), lines.map((row) => row.sentAt));
await page.screenshot({ path: `${OUT}/71-tongzheng-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('tongzheng'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出（顺带验"退出后纸面糊在镜头上"的 bug 没回来） ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="tongzheng"]');
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
await page.route('**/message/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/message/v1/record/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="tongzheng"]').click();
await page.waitForTimeout(1200);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="tongzheng"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('tongzheng');
  const empty = document.querySelector('[data-screen-id="tongzheng"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="tongzheng"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/72-tongzheng-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
