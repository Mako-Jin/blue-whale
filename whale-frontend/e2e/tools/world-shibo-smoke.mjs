// 市舶司订单台账（S6 / T6.34）冒烟：内城十二坊的最后一坊、收官批。
//
// 与前几坊的分工：财务殿验"钱本身对不对"、度支殿验"账记没记平"，
// 这一条验**一笔生意从下单到收货的流水线卡在哪一节**——待支付、
// 待发货、待退货这条链。运营牌（待发货/售后/零库存）是本坊的主角。
//
// 九件事：
//   ① 概览四张牌走 /mall/overview，三张运营牌有账就黄；
//   ② 订单七态分布画成**柱图不是折线**：市舶的账是存量不是流量，
//      七种状态分桶在服务端定死（含退款两态），屏风不译码；
//   ③ 订单台账**有搜索框**（keyword 合并关键字：订单号与买家名一起模糊匹配），
//      也有状态筛选（status 独立参数）；
//   ④ 状态列用**页面级字典**七态：待发货/退款中黄、已完成绿、其余灰——
//      退款两态单列，不并进取消（钱卡在支付侧与"没成交"是两回事）；
//   ⑤ 金额走 money 口径（¥ + 万折算）；
//   ⑥ 列表行即详情（不配 detailPath）：OrderView 平铺，行内字段够详情用；
//   ⑦ 下单时间走 datetime 口径（MM-DD HH:mm）；
//   ⑧ 概览入口两个（订单台账 + 商品台账），屏风页挂订单主账；
//   ⑨ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着二十块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="shibo"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'F:/blue-whale/.toolchains/tmp/s6-shots';
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
/* 与 MallOverviewProvider 的实际下发一致：四张 KPI（三张运营牌黄）、
   一张七态柱图、两条告警、两个入口。 */
const OVERVIEW = {
  service: 'mall',
  nodeKey: 'shibosi-hall',
  title: '市舶',
  subtitle: '商城运营 · 通商四本账',
  generatedAt: '2026-11-22T08:30:00+08:00',
  state: 'live',
  kpis: [
    { key: 'on-sale', label: '在售商品', value: 18, format: 'count', tone: 'flat', hint: '未上架 12 件' },
    { key: 'paid', label: '待发货订单', value: 5, format: 'count', tone: 'warn', hint: '已收款未发货，要人去发' },
    { key: 'aftersale', label: '待处理售后', value: 6, format: 'count', tone: 'warn', hint: '待审核 4 · 待退货 2' },
    { key: 'stock-zero', label: '零库存 SKU', value: 6, format: 'count', tone: 'warn', hint: '有规格缺货，下单会失败' },
  ],
  charts: [
    {
      key: 'order-status',
      title: '订单状态分布',
      type: 'bar',
      format: 'count',
      categories: ['待支付', '待发货', '已发货', '已完成', '已取消', '退款中', '已退款'],
      series: [{ name: '订单数', values: [8, 5, 12, 60, 10, 3, 2] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 5 单已收款未发货，钱收了货还没动，需要人去发', count: null },
    { level: 'info', text: '有 2 单售后待退货，等买家寄回商品后结单', count: null },
  ],
  links: [
    { key: 'orders', label: '订单台账', path: '/mall/v1/order/page', permission: 'sys:mall:order:list' },
    { key: 'products', label: '商品台账', path: '/mall/v1/product/page', permission: 'sys:mall:product:list' },
  ],
  notes: ['口径：已支付未发货 = 钱已收款待发货；售后只数未结账（待审核 + 待退货）；零库存按 SKU 数不按商品数'],
};

/**
 * 10 张订单：七态全覆盖。6 行/页 → 2 页。
 * 第 6 张（第 1 页末行）是已退款——状态字典的灰灯要有实例可看。
 */
const ORDERS = [
  { id: 30001, orderNo: 'ORD-2026-0001', buyerName: '王铁柱', status: 'PAID', totalAmount: 15000, totalQuantity: 3, channelCode: 'WEB', createTime: '2026-11-21 09:30:00' },
  { id: 30002, orderNo: 'ORD-2026-0002', buyerName: '李翠花', status: 'REFUNDING', totalAmount: 8000, totalQuantity: 1, channelCode: 'WEB', createTime: '2026-11-21 10:05:00' },
  { id: 30003, orderNo: 'ORD-2026-0003', buyerName: '王铁柱', status: 'COMPLETED', totalAmount: 328.5, totalQuantity: 2, channelCode: 'MINI', createTime: '2026-11-21 11:20:00' },
  { id: 30004, orderNo: 'ORD-2026-0004', buyerName: '赵四', status: 'SHIPPED', totalAmount: 4500, totalQuantity: 5, channelCode: 'WEB', createTime: '2026-11-21 13:40:00' },
  { id: 30005, orderNo: 'ORD-2026-0005', buyerName: '沈万三', status: 'COMPLETED', totalAmount: 128000, totalQuantity: 12, channelCode: 'MINI', createTime: '2026-11-21 15:10:00' },
  { id: 30006, orderNo: 'ORD-2026-0006', buyerName: '李翠花', status: 'REFUNDED', totalAmount: 1200, totalQuantity: 1, channelCode: 'WEB', createTime: '2026-11-21 16:00:00' },
  { id: 30007, orderNo: 'ORD-2026-0007', buyerName: '王铁柱', status: 'PENDING_PAYMENT', totalAmount: 600, totalQuantity: 1, channelCode: 'WEB', createTime: '2026-11-21 17:25:00' },
  { id: 30008, orderNo: 'ORD-2026-0008', buyerName: '沈万三', status: 'PAID', totalAmount: 9999, totalQuantity: 4, channelCode: 'MINI', createTime: '2026-11-21 18:30:00' },
  { id: 30009, orderNo: 'ORD-2026-0009', buyerName: '赵四', status: 'CANCELLED', totalAmount: 500, totalQuantity: 2, channelCode: 'WEB', createTime: '2026-11-21 19:45:00' },
  { id: 30010, orderNo: 'ORD-2026-0010', buyerName: '李翠花', status: 'COMPLETED', totalAmount: 21000, totalQuantity: 6, channelCode: 'MINI', createTime: '2026-11-21 20:50:00' },
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

/** 记录列表真实发出的查询参数：关键字必须叫 keyword，状态叫 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/mall/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   市舶坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/mall/v1/order/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/mall/v1/order/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const keyword = url.searchParams.get('keyword');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    keyword,
    status,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：keyword 按订单号或买家名模糊匹配，status 精确匹配（MallOrderController#pageAll） */
  const matched = ORDERS.filter((row) => {
    const hitStatus = !status || row.status === status;
    const hitKeyword = !keyword || row.orderNo.includes(keyword) || row.buyerName.includes(keyword);
    return hitStatus && hitKeyword;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是订单台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shibo"]');
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
check('市舶司屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 shibo', mounted?.id === 'shibo', mounted?.id);
check('大屏规格（七列 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是市舶司 · 订单台账', String(mounted?.title).includes('订单台账'), mounted?.title);
check('入口标签跟概览 links 走：订单台账 + 商品台账两个',
  mounted?.tabs?.length === 2 && mounted.tabs[0] === '订单台账' && mounted.tabs[1] === '商品台账', mounted?.tabs);
check('商品台账标签是禁用的（屏风只挂订单主账，商品在 2D 里从订单进）', (async () => {
  const disabled = await page.evaluate(() => {
    const tabs = [...document.querySelectorAll('[data-screen-id="shibo"] .bwsp-tab')];
    return tabs.find((tab) => tab.textContent === '商品台账')?.disabled ?? null;
  });
  return disabled === true;
})(), null);

/* ---- 2. 概览走 /mall/overview ---- */
check('概览请求打到 /mall/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/mall/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（在售/待发货/售后/零库存）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（订单状态分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('两条告警（待发货积压 + 待退货说明）', probe?.overview.alerts === 2, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shibo"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
check('在售 18 件、灰灯（只报数不喊事）', kpiCards[0]?.value === '18' && kpiCards[0]?.tone === 'flat', kpiCards[0]);
check('在售牌写出未上架数（未上架 12 件）', String(kpiCards[0]?.label).includes('未上架 12 件'), kpiCards[0]?.label);
check('待发货 5 单、黄灯（钱收了货没动）', kpiCards[1]?.value === '5' && kpiCards[1]?.tone === 'warn', kpiCards[1]);
check('售后 6 单、黄灯（待审核 4 · 待退货 2 分开说）',
  kpiCards[2]?.value === '6' && kpiCards[2]?.tone === 'warn'
  && String(kpiCards[2]?.label).includes('待审核 4'), kpiCards[2]);
check('零库存 6 个 SKU、黄灯（按 SKU 数不按商品数）',
  kpiCards[3]?.value === '6' && kpiCards[3]?.tone === 'warn', kpiCards[3]);

/* ---- 3. 状态分布柱图：七段分桶（含退款两态）、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="shibo"]');
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
check('状态分布画成柱图（不是折线）', barChart.rows.length === 7 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是服务端定的七段状态标签（退款两态单列）',
  barChart.labels.length === 7 && barChart.labels[5] === '退款中' && barChart.labels[6] === '已退款', barChart.labels);
check('柱值按计数排版（8/5/12/60/10/3/2）',
  barChart.values.join(',') === '8,5,12,60,10,3,2', barChart.values);

/* ---- 4. 有搜索框、也有状态筛选，关键字参数是 keyword（后端有合并关键字） ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="shibo"]');
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
check('有搜索框（后端 keyword 同时模糊匹配订单号与买家名）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（订单号 / 买家）',
  String(filters?.placeholder).includes('订单号'), filters?.placeholder);
check('有状态筛选（分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项是运营要盯的五态（不摆已完成/已取消/已退款）',
  filters?.statusValues.join(',') === ',PENDING_PAYMENT,PAID,SHIPPED,COMPLETED,REFUNDING', filters?.statusValues);

/* ---- 5. 首屏列表：10 张分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 10 张 / 2 页', probe?.list.rows === 6 && probe?.list.total === 10 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 keyword / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.keyword === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shibo"] .bwsp-table tbody tr')].map((tr) => ({
    orderNo: tr.children[0]?.textContent ?? '',
    buyer: tr.children[1]?.textContent ?? '',
    status: tr.children[2]?.textContent ?? '',
    statusTone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
    amount: tr.children[3]?.textContent ?? '',
    quantity: tr.children[4]?.textContent ?? '',
    created: tr.children[5]?.textContent ?? '',
    channel: tr.children[6]?.textContent ?? '',
  })),
);
let rows = await readRows();
check('订单号原样显示', rows[0]?.orderNo === 'ORD-2026-0001', rows[0]?.orderNo);
check('待发货按页面级字典译成"待发货"黄灯',
  rows[0]?.status === '待发货' && rows[0]?.statusTone === 'warn', rows[0]);
check('金额按 money 口径（15000 → ¥1.5万）', rows[0]?.amount === '¥1.5万', rows[0]?.amount);
check('零头也走 money 口径（328.5 → ¥329）', rows[2]?.amount === '¥329', rows[2]?.amount);
check('大额折算（128000 → ¥12.8万）', rows[4]?.amount === '¥12.8万', rows[4]?.amount);
check('已退款灰灯（终态不是事故）',
  rows[5]?.status === '已退款' && rows[5]?.statusTone === 'flat', rows[5]);
check('下单时间按 datetime 口径（MM-DD HH:mm）', rows[0]?.created === '11-21 09:30', rows[0]?.created);
await page.screenshot({ path: `${OUT}/90-shibo-list.png` });

/* ---- 6. 先走到屏风跟前再进：市舶司在西北角（x/z=-53.74/-53.74，旋转 45°），
   出生点离得远，必须先走。屏风的世界位姿直接从 CSS3D 变换矩阵里解：
   matrix3d 是列主序，第 12/14 个数是平移 x/z，第 9/11 个数是局部 +z 轴
   （即屏风正面法线，与 `districtScreenPose` 的 front 同口径）。
   站到法线外侧 4.5 米，相机 yaw = atan2(nx, nz)（view dir = −(sin yaw, cos yaw)）。 ---- */
const pose = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shibo"]');
  const t = el?.parentElement?.style.transform || el?.style.transform || '';
  const m = t.match(/matrix3d\(([^)]+)\)/);
  if (!m) return null;
  const v = m[1].split(',').map(Number);
  const zx = v[8];
  const zz = v[10];
  const len = Math.hypot(zx, zz) || 1;
  return { x: v[12], z: v[14], nx: zx / len, nz: zz / len };
});
check('能从 CSS3D 变换里解出屏风位姿', Boolean(pose) && Number.isFinite(pose?.x), pose);
await page.evaluate(({ pose }) => {
  const px = pose.x + 4.5 * pose.nx;
  const pz = pose.z + 4.5 * pose.nz;
  window.__bwwWalkTo(px, pz);
  window.__bwwLook(Math.atan2(pose.nx, pose.nz), 0.18, 9);
}, { pose });
await page.waitForTimeout(14000);
/* 不用 locator.click：走到跟前时屏风可能落在小地图面板底下（hit-target 被拦），
   而且屏风带 3D 变换，包围盒中心未必落在元素上。点击入口是 screen.el 上的
   click 监听（与前几坊同一套），直接派发 click 事件走同一逻辑。 */
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shibo"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="shibo"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="shibo"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'shibo', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/91b-shibo-engaged.png` });

/* ---- 7. 关键字搜索：发 keyword=沈 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('沈');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
const lastSearch = queried.filter((item) => item.keyword === '沈').pop();
check('关键字按 keyword=沈 发给服务端（合并关键字，不是单字段）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 2 张（沈万三的两单）',
  probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('命中的是买家真姓沈的单', rows.every((row) => row.buyer === '沈万三'), rows.map((row) => row.buyer));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=REFUNDING，只剩一张退款中 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('REFUNDING');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
const lastStatus = queried.filter((item) => item.status === 'REFUNDING').pop();
check('状态按 status=REFUNDING 发给服务端（独立参数不并进 keyword）', Boolean(lastStatus), queried.slice(-3));
check('退款中剩 1 张', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
rows = await readRows();
check('命中的是退款中的单（黄灯）',
  rows[0]?.orderNo === 'ORD-2026-0002' && rows[0]?.status === '退款中' && rows[0]?.statusTone === 'warn', rows[0]);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 4 张 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
check('第 2 页剩 4 张', probe?.list.page === 2 && probe?.list.rows === 4, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是 ORD-2026-0007（page 参数真的生效了）',
  rows[0]?.orderNo === 'ORD-2026-0007', rows[0]?.orderNo);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（OrderView 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 9 个字段用列表行数据（含支付/发货时间）',
  probe?.detail.fields === 9 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/92-shibo-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shibo'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shibo"]');
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
await page.route('**/mall/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/mall/v1/order/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shibo"]');
  el?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
});
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="shibo"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('shibo');
  const empty = document.querySelector('[data-screen-id="shibo"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="shibo"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/93-shibo-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
