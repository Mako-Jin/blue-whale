// 将作坊物料台账（S6 / T6.19）冒烟：内城十二坊的第九块屏风页。
//
// 与工部/吏部/礼部/兵部/杏坛的分工：工部验"车间在不在跑"、吏部验"人有没有到岗"、
// 礼部验"客户"、兵部验"哨探"、杏坛验"人有没有在长本事"，这一条验**器物**——
// 城里要造什么料、BOM 改到第几版、有多少单变更还压在流程里。
//
// 九件事：
//   ① 概览四张牌走 /pdm/overview，四张都是计数（没有比率牌），
//      "未关闭变更"是唯一带语气的牌——有单压着亮黄灯、清空了亮绿灯；
//   ② 类型分布画成**柱图不是折线**：将作的账是存量不是流量，四种类型码值固定；
//   ③ 物料台账**有搜索框也有状态筛选**，但关键字参数是 `name` 不是 `keyword`——
//      后端 `PdmProductQueryRequest` 是 code / name 分开的两个条件，没有合并 keyword；
//   ④ 类型列用**列级字典**（PRODUCT/ASSEMBLY/PART/RAW_MATERIAL），
//      页面级字典留给状态列（ENABLED/DISABLED）——两套码空间同屏，只有列级覆盖分得开；
//   ⑤ 列表行即详情（不配 detailPath）：`PdmProductVO` 平铺，行内字段够详情用；
//   ⑥ 停用件给灰灯——停用是沉淀不是事故；
//   ⑦ 接口全挂时不白屏：标 stale + 给一句人话。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里现在挂着十六块业务页，
   '.bwsp-status' 这类选择器会同时命中多张表（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="jiangzuo"] ';

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
/* 与 PdmOverviewProvider 的实际下发一致：四张 KPI（全是计数）、
   一张四类物料柱图、四条告警、一个物料台账入口 */
const OVERVIEW = {
  service: 'pdm',
  nodeKey: 'jiangzuo-bureau',
  title: '将作',
  subtitle: '产品数据 · 四本账',
  generatedAt: '2026-11-22T06:45:00+08:00',
  state: 'live',
  kpis: [
    { key: 'parts', label: '在册物料', value: 1200, format: 'count', tone: 'flat', hint: '停用 8 件' },
    { key: 'boms', label: '生效 BOM', value: 12, format: 'count', tone: 'flat', hint: '草稿 5 · 共 40 版' },
    { key: 'chg-open', label: '未关闭变更', value: 7, format: 'count', tone: 'warn', hint: '评估/审批 3 · 执行中 2' },
    { key: 'docs', label: '图纸文档', value: 25, format: 'count', tone: 'flat', hint: '草稿 6 · 借出 2' },
  ],
  charts: [
    {
      key: 'part-types',
      title: '在册物料类型分布',
      type: 'bar',
      format: 'count',
      categories: ['成品', '部件', '零件', '原材料'],
      series: [{ name: '在册件数', values: [300, 200, 400, 300] }],
    },
  ],
  alerts: [
    { level: 'warning', text: '有 3 张变更单卡在评估或审批，需要人去催', count: 3 },
    { level: 'warning', text: '有 2 份图纸被检出未还，相关的变更与修订要排队', count: 2 },
    { level: 'info', text: '有 2 张变更单正在执行，完成前以旧版 BOM / 图纸为准', count: null },
    { level: 'info', text: '有 5 份 BOM 草稿还没发布，发布前车间看不到', count: null },
  ],
  links: [{ key: 'parts', label: '物料台账', path: '/pdm/v1/part/page', permission: 'sys:pdm:part:list' }],
  notes: ['口径：在册物料 = pdm_product ENABLED；生效 BOM = RELEASED 版本；未关闭变更 = 未进 完成/关闭/驳回/取消 的变更单；借出 = 图纸被检出未还'],
};

/**
 * 9 件物料：类型四种全覆盖，第 8、9 件停用。
 * 6 行/页 → 2 页。
 */
const PARTS = [
  { id: 7001, code: 'XH-2412', name: '数控龙门铣 XH-2412', partType: 'PRODUCT', material: '—', spec: '工作台 2400×1200', uom: '台', categoryCode: 'finished', erpItemCode: 'ERP-0001', status: 'ENABLED', updateTime: '2026-11-01T09:12:00' },
  { id: 7002, code: 'ASM-SPDL', name: '主轴部件', partType: 'ASSEMBLY', material: '—', spec: 'BT40', uom: '套', categoryCode: 'assembly', erpItemCode: 'ERP-0002', status: 'ENABLED', updateTime: '2026-11-02T10:20:00' },
  { id: 7003, code: 'PT-SPDL-25', name: '精密主轴', partType: 'PART', material: '40Cr', spec: 'φ25×300', uom: '件', categoryCode: 'shaft', erpItemCode: 'ERP-0003', status: 'ENABLED', updateTime: '2026-11-03T14:40:00' },
  { id: 7004, code: 'PT-TAILSTK', name: '铸铁尾座体', partType: 'PART', material: 'HT300', spec: '320×180×150', uom: '件', categoryCode: 'casting', erpItemCode: 'ERP-0004', status: 'ENABLED', updateTime: '2026-10-30T16:05:00' },
  { id: 7005, code: 'RM-STEEL45', name: '45 号圆钢', partType: 'RAW_MATERIAL', material: '45', spec: 'φ60×6000', uom: '根', categoryCode: 'bar', erpItemCode: 'ERP-0005', status: 'ENABLED', updateTime: '2026-09-19T08:00:00' },
  { id: 7006, code: 'RM-HT300', name: 'HT300 铸锭', partType: 'RAW_MATERIAL', material: 'HT300', spec: '炉号 2026-114', uom: '吨', categoryCode: 'ingot', erpItemCode: 'ERP-0006', status: 'ENABLED', updateTime: '2026-08-02T09:00:00' },
  { id: 7007, code: 'PT-BALLSCREW', name: '滚珠丝杠副', partType: 'PART', material: 'GCr15', spec: 'φ40×2000 C3', uom: '套', categoryCode: 'drive', erpItemCode: 'ERP-0007', status: 'ENABLED', updateTime: '2026-07-22T11:15:00' },
  { id: 7008, code: 'PT-SCREW-OLD', name: '旧型丝杠', partType: 'PART', material: '45', spec: 'φ32×1500', uom: '套', categoryCode: 'drive', erpItemCode: 'ERP-0008', status: 'DISABLED', updateTime: '2025-12-01T08:30:00' },
  { id: 7009, code: 'RM-SCRAP', name: '报废铸件', partType: 'RAW_MATERIAL', material: 'HT250', spec: '批次 2025-07', uom: '吨', categoryCode: 'scrap', erpItemCode: 'ERP-0009', status: 'DISABLED', updateTime: '2025-07-15T15:00:00' },
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

/** 记录列表真实发出的查询参数：关键字必须叫 name，状态叫 status */
const queried = [];
const detailHits = [];
const overviewHits = [];

await page.route('**/pdm/overview**', async (route) => {
  overviewHits.push(new URL(route.request().url()).pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
});

/* 详情路由先注册、列表路由后注册：Playwright 后注册的优先匹配。
   将作坊不配 detailPath，这条路由只用来断言"点行不会打详情接口"。 */
await page.route('**/pdm/v1/part/*', async (route) => {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith('/page')) {
    await route.fallback();
    return;
  }
  detailHits.push(url.pathname);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(null)) });
});

await page.route('**/pdm/v1/part/page**', async (route) => {
  const url = new URL(route.request().url());
  const current = Number(url.searchParams.get('current') ?? '1');
  const size = Number(url.searchParams.get('size') ?? '6');
  const name = url.searchParams.get('name');
  const status = url.searchParams.get('status');
  queried.push({
    current,
    size,
    name,
    status,
    names: [...url.searchParams.keys()],
    path: url.pathname,
  });
  /* 与后端同口径：name 按物料名模糊匹配，status 精确匹配（`PdmProductQueryRequest`） */
  const matched = PARTS.filter((row) => {
    const hitStatus = !status || row.status === status;
    const hitName = !name || row.name.includes(name);
    return hitStatus && hitName;
  });
  const from = Math.min(matched.length, (current - 1) * size);
  const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是物料台账页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="jiangzuo"]');
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
check('将作坊屏风挂上了业务页', Boolean(mounted?.hasPage), mounted);
check('屏风 id 是 jiangzuo', mounted?.id === 'jiangzuo', mounted?.id);
check('大屏规格（五列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('题头是将作坊 · 物料台账', String(mounted?.title).includes('物料台账'), mounted?.title);
check('入口标签来自概览 links（物料台账）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '物料台账', mounted?.tabs);

/* ---- 2. 概览走 /pdm/overview ---- */
check('概览请求打到 /pdm/overview', overviewHits.length > 0 && overviewHits.every((p) => p === '/pdm/overview'), overviewHits);

let probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（物料/BOM/变更/图纸）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('一张图（物料类型分布）', probe?.overview.charts === 1, probe?.overview.charts);
check('四条告警（变更积压 + 图纸借出 + 执行中 + BOM 草稿）', probe?.overview.alerts === 4, probe?.overview.alerts);

const kpiCards = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="jiangzuo"] .bwsp-kpi')].map((card) => ({
    value: card.querySelector('.bwsp-kpi-value')?.textContent ?? '',
    label: card.querySelector('.bwsp-kpi-label')?.textContent ?? '',
    tone: card.dataset.tone ?? '',
  })),
);
/* 计数走格式化层的既有口径：超过三位加千分位（与契约 SDK 的 formatValue 同口径） */
check('在册物料按计数排版（1200 → 1,200）', kpiCards[0]?.value === '1,200', kpiCards[0]);
check('生效 BOM 的提示语写出版本结构（草稿 5 · 共 40 版）',
  String(kpiCards[1]?.label).includes('草稿 5 · 共 40 版'), kpiCards[1]?.label);
/* 关键断言：未关闭变更是四张牌里唯一带语气的——有单压着黄灯，不是绿灯 */
check('未关闭变更 7 张亮黄灯', kpiCards[2]?.value === '7' && kpiCards[2]?.tone === 'warn', kpiCards[2]);
check('变更提示语拆评估与执行两段（评估/审批 3 · 执行中 2）',
  String(kpiCards[2]?.label).includes('评估/审批 3 · 执行中 2'), kpiCards[2]?.label);
check('在册物料与图纸都是灰灯（只报数不喊事，语气留给告警条）',
  kpiCards[0]?.tone === 'flat' && kpiCards[3]?.tone === 'flat',
  { parts: kpiCards[0]?.tone, docs: kpiCards[3]?.tone });

/* ---- 3. 类型分布柱图：四种类型、不是折线 ---- */
const barChart = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="jiangzuo"]');
  const rows = [...(scope?.querySelectorAll('.bwsp-barrow') ?? [])];
  /* 柱图的标签/数值都在行内（.bwsp-barlabel / .bwsp-barval）；
     .bwsp-barlabels 是折线图的横轴，柱图里没有 */
  const labels = [...(scope?.querySelectorAll('.bwsp-barlabel') ?? [])].map((span) => span.textContent);
  const values = [...(scope?.querySelectorAll('.bwsp-barval') ?? [])].map((span) => span.textContent);
  return {
    rows,
    labels,
    values,
    lineCount: scope?.querySelectorAll('svg.bwsp-line').length ?? 0,
  };
});
check('类型分布画成柱图（不是折线）', barChart.rows.length === 4 && barChart.lineCount === 0,
  { rows: barChart.rows.length, line: barChart.lineCount });
check('横轴是成品/部件/零件/原材料（服务端定的标签，屏风不译码）',
  barChart.labels.join(',') === '成品,部件,零件,原材料', barChart.labels);
check('柱值按计数排版（300/200/400/300，最大值是零件）',
  barChart.values.join(',') === '300,200,400,300', barChart.values);

/* ---- 4. 有搜索框、也有状态筛选，但关键字参数是 name 不是 keyword ---- */
const filters = await page.evaluate(() => {
  const scope = document.querySelector('[data-screen-id="jiangzuo"]');
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
check('有搜索框（后端按物料名模糊匹配）', filters?.searchHidden === false, filters);
check('搜索框提示语写清搜什么（物料名称）',
  String(filters?.placeholder).includes('物料名称'), filters?.placeholder);
check('有状态筛选（分页接口真收 status）', filters?.statusHidden === false, filters);
check('状态筛选项是在册 / 停用两态',
  filters?.statusValues.join(',') === ',ENABLED,DISABLED', filters?.statusValues);

/* ---- 5. 首屏列表：9 件分 2 页，只带分页参数 ---- */
check('首屏 6 行 / 共 9 件 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('首屏只带分页参数（不发明 name / status 空值）',
  queried[0]?.current === 1 && queried[0]?.size === 6
    && queried[0]?.name === null && queried[0]?.status === null
    && queried[0].names.every((item) => ['current', 'size'].includes(item)),
  queried[0]);

const readRows = () => page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="jiangzuo"] .bwsp-table tbody tr')].map((tr) => ({
    code: tr.children[0]?.textContent ?? '',
    name: tr.children[1]?.textContent ?? '',
    partType: tr.children[2]?.textContent ?? '',
    spec: tr.children[3]?.textContent ?? '',
    status: tr.children[4]?.textContent ?? '',
    statusTone: tr.children[4]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
let rows = await readRows();
check('物料编码原样显示', rows[0]?.code === 'XH-2412', rows[0]?.code);
check('物料名原样显示', rows[0]?.name === '数控龙门铣 XH-2412', rows[0]?.name);
check('类型列按列级字典译成中文（PRODUCT → 成品）', rows[0]?.partType === '成品', rows[0]?.partType);
check('第二行类型是部件（ASSEMBLY → 部件，不蹭页面级状态字典）',
  rows[1]?.partType === '部件', rows[1]?.partType);
check('ENABLED 译成"在册"并打绿灯',
  rows[0]?.status === '在册' && rows[0]?.statusTone === 'good', rows[0]);
await page.screenshot({ path: `${OUT}/50-jiangzuo-list.png` });

/* ---- 6. 进入屏风（贴脸态）：筛选、翻页、点行都要"在屏风里"才生效 ----
   `screenpage.ts` 里 statusSel / pager / row 的事件都带 `if (!engaged()) return;`，
   所以在 3D 里隔空点搜索框是没反应的——必须先点屏风进去。 */
await page.locator('[data-screen-id="jiangzuo"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="jiangzuo"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="jiangzuo"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'jiangzuo', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);
await page.screenshot({ path: `${OUT}/50b-jiangzuo-engaged.png` });

/* ---- 7. 关键字搜索：发 name=主轴 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('主轴');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
const lastSearch = queried.filter((item) => item.name === '主轴').pop();
check('关键字按 name=主轴 发给服务端（不是 keyword）', Boolean(lastSearch), queried.slice(-3));
check('搜索后剩 2 条（主轴部件 + 精密主轴）',
  probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('命中的是名字里真有"主轴"的两件',
  rows.map((row) => row.name).join('|') === '主轴部件|精密主轴', rows.map((row) => row.name));
check('编码列同时可见（XH-2412 换成 ASM-SPDL / PT-SPDL-25）',
  rows.map((row) => row.code).join('|') === 'ASM-SPDL|PT-SPDL-25', rows.map((row) => row.code));
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(900);

/* ---- 8. 状态筛选：发 status=DISABLED，只剩两件停用 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('DISABLED');
await page.waitForTimeout(900);
probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
const lastStatus = queried.filter((item) => item.status === 'DISABLED').pop();
check('状态按 status=DISABLED 发给服务端', Boolean(lastStatus), queried.slice(-3));
check('停用只剩 2 件', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
rows = await readRows();
check('两件停用是旧型丝杠与报废铸件',
  rows.map((row) => row.name).join('|') === '旧型丝杠|报废铸件', rows.map((row) => row.name));
check('停用给灰灯（沉淀不是事故，不亮红灯）',
  rows.every((row) => row.status === '停用' && row.statusTone === 'flat'), rows);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(900);

/* ---- 9. 分页：第 2 页 3 条 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
check('第 2 页剩 3 件', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
rows = await readRows();
check('第 2 页第一行是滚珠丝杠副（page 参数真的生效了）',
  rows[0]?.name === '滚珠丝杠副', rows[0]?.name);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(600);

/* ---- 10. 列表行即详情：点行不打详情接口 ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
check('点行打开详情态', probe?.detail.open === true, probe?.detail);
check('不配 detailPath：不打详情接口（PdmProductVO 平铺，行内字段够用）',
  detailHits.length === 0, detailHits);
check('详情 10 个字段用列表行数据（含材质 / 单位 / ERP 编码）',
  probe?.detail.fields === 10 && probe?.detail.lines === 0, probe?.detail);
await page.screenshot({ path: `${OUT}/51-jiangzuo-detail.png` });
await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('jiangzuo'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 11. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="jiangzuo"]');
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
await page.route('**/pdm/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.route('**/pdm/v1/part/page**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="jiangzuo"]').click();
await page.waitForTimeout(1200);
await page.locator('[data-screen-id="jiangzuo"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('jiangzuo');
  const empty = document.querySelector('[data-screen-id="jiangzuo"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="jiangzuo"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/52-jiangzuo-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
