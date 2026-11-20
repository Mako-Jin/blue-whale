// 数枢殿总控（S5 / T5.1）冒烟：屏风里是"八方宫署"真业务页，不是一块好看的看板。
//
// 与 T4.5 的户部页冒烟的分工：
//   户部那条验的是"一张列表页该有的交互"（筛选/分页/详情/分录）。
//   这条验的是"总控最要紧的那件事"——**每座殿的连通状态是真的**：
//   服务端给 live/stale/degraded/denied，前端如实标成徽标，
//   点进去看到的是同一时刻的那份快照，而不是第二次问出来的另一个数。
//
// 数据来源：用 page.route 拦 /monitor/overview**，返回**与后端同形状**的信封
// （{code,message,data}）。不用 __bwwScreenMock：这里还要验"分页/筛选参数
// 真的发给了服务端"，注入假数据会把这条最关键的链路绕过去。

import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/* 屏风页选择器一律限定到本屏风：城里同时挂着户部台账与总控两张业务页，
   '.bwsp-search' 这类选择器会同时命中两个（Playwright strict mode 直接报错）。 */
const SCOPE = '[data-screen-id="shushu"] ';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8125/world/?debug=1';
const OUT = 'D:/BlueWhale/BlueWhale/.toolchains/tmp/s5-shots';
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
const HALLS = [
  { id: 'hubu-hall', nodeKey: 'hubu-hall', name: '户部', state: 'live', headline: '营收 ¥123.4万', note: '实时 · 口径：驾驶舱快照 S-1', updatedLabel: '11-21 03:00' },
  { id: 'shumi-hall', nodeKey: 'shumi-hall', name: '枢密殿', state: 'live', headline: '在线会话 316', note: '实时', updatedLabel: '11-21 03:01' },
  { id: 'duzhi-hall', nodeKey: 'duzhi-hall', name: '度支殿', state: 'stale', headline: '交易额 ¥8.6万', note: '陈旧 · 沿用上次 02:00', updatedLabel: '11-21 02:00' },
  { id: 'cangshu-hall', nodeKey: 'cangshu-hall', name: '藏书殿', state: 'live', headline: '文件 1.8万', note: '实时', updatedLabel: '11-21 03:01' },
  { id: 'shichen-hall', nodeKey: 'shichen-hall', name: '时辰殿', state: 'degraded', headline: '—', note: '问询超时', updatedLabel: '' },
  { id: 'zhengshi-hall', nodeKey: 'zhengshi-hall', name: '政事殿', state: 'live', headline: '运行中 42', note: '实时', updatedLabel: '11-21 03:02' },
  { id: 'kaogong-hall', nodeKey: 'kaogong-hall', name: '考功殿', state: 'denied', headline: '—', note: '无此殿凭据', updatedLabel: '' },
  { id: 'tongzheng-hall', nodeKey: 'tongzheng-hall', name: '通政殿', state: 'live', headline: '未读 12', note: '实时', updatedLabel: '11-21 03:02' },
  { id: 'qiju-hall', nodeKey: 'qiju-hall', name: '起居殿', state: 'degraded', headline: '—', note: 'HTTP 503', updatedLabel: '' },
];

const OVERVIEW = {
  service: 'monitor',
  nodeKey: 'shushu-hall',
  title: '数枢殿',
  subtitle: '八方宫署 · 总控 · 9 殿在册',
  generatedAt: '2026-11-21T03:02:00+08:00',
  state: 'live',
  kpis: [
    { key: 'halls', label: '接入殿', value: 9, format: 'count', tone: 'flat', hint: '在册' },
    { key: 'live', label: '通畅', value: 5, format: 'count', tone: 'flat' },
    { key: 'stale', label: '陈旧', value: 1, format: 'count', tone: 'warn', hint: '沿用上次' },
    { key: 'down', label: '未通', value: 3, format: 'count', tone: 'bad' },
  ],
  charts: [
    {
      key: 'health',
      title: '各殿连通',
      type: 'donut',
      format: 'count',
      categories: ['通畅', '陈旧', '未通'],
      series: [{ name: '殿', values: [5, 1, 3] }],
    },
  ],
  alerts: [
    { level: 'critical', text: '未通：时辰殿、起居殿', count: 2 },
    { level: 'warning', text: '无凭据：考功殿', count: 1 },
  ],
  links: [{ key: 'hall-health', label: '八方宫署', path: '/monitor/overview/halls' }],
  notes: ['时辰殿 degraded · 问询超时'],
};

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
    // 屏风页没有令牌时连请求都不会发（会直接显示"未登录"），这里给一个 debug 令牌
    window.sessionStorage.setItem('bww.debug.token', 'e2e-token');
  } catch {
    /* 忽略 */
  }
});

/** 记录屏风页真实发出的查询参数：分页/筛选若没发给服务端，等于假分页 */
const queried = [];

await page.route('**/monitor/overview**', async (route) => {
  const url = new URL(route.request().url());
  const path = url.pathname;
  if (path === '/monitor/overview') {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(OVERVIEW)) });
    return;
  }
  if (path === '/monitor/overview/halls') {
    const current = Number(url.searchParams.get('current') ?? '1');
    const size = Number(url.searchParams.get('size') ?? '6');
    const keyword = (url.searchParams.get('keyword') ?? '').trim();
    const state = (url.searchParams.get('state') ?? '').trim();
    queried.push({ current, size, keyword, state });
    const matched = HALLS.filter((hall) => {
      if (keyword && !hall.name.includes(keyword)) return false;
      if (state && hall.state !== state) return false;
      return true;
    });
    const from = Math.min(matched.length, (current - 1) * size);
    const data = { records: matched.slice(from, from + size), total: matched.length, current, size };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
    return;
  }
  const match = path.match(/^\/monitor\/overview\/halls\/(.+)$/);
  if (match) {
    const row = HALLS.find((hall) => hall.id === decodeURIComponent(match[1]));
    if (!row) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 404, message: '未知殿' }) });
      return;
    }
    const data = {
      nodeKey: row.nodeKey,
      name: row.name,
      state: row.state,
      subtitle: row.headline === '—' ? null : '口径演示 · 本月',
      updatedLabel: row.updatedLabel,
      note: row.note,
      lines:
        row.headline === '—'
          ? []
          : [
              { no: 1, label: '主指标', value: row.headline.split(' ').slice(1).join(' ') },
              { no: 2, label: '环比', value: '3.2%' },
              { no: 3, label: '累计', value: '¥1,024万' },
            ],
    };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(data)) });
    return;
  }
  await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, message: 'not found' }) });
});

await page.goto(BASE, { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(9000);

/* ---- 1. 屏风立起来了，且挂的是总控页 ---- */
const mounted = await page.evaluate(() => {
  const el = document.querySelector('[data-screen-id="shushu"]');
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
check('数枢殿屏风挂上了总控页', Boolean(mounted?.hasPage), mounted);
check('大屏规格（四列表 + 6 行分页要横向空间）', mounted?.size === 'large', mounted?.size);
check('入口标签来自概览 links（八方宫署）', mounted?.tabs?.length === 1 && mounted.tabs[0] === '八方宫署', mounted?.tabs);

/* ---- 2. 概览层：KPI 与环形图 ---- */
let probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
check('概览 live', probe?.overview.state === 'live', probe?.overview);
check('四张 KPI（接入/通畅/陈旧/未通）', probe?.overview.kpis === 4, probe?.overview.kpis);
check('环形图 1 张', probe?.overview.charts === 1, probe?.overview.charts);
const kpiText = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shushu"] .bwsp-kpi')].map((el) => el.textContent),
);
check('KPI 文本带单位与口径', kpiText.some((text) => text.includes('9')), kpiText);

/* ---- 3. 首屏列表：9 殿分 2 页 ---- */
check('首屏 6 行 / 共 9 条 / 2 页', probe?.list.rows === 6 && probe?.list.total === 9 && probe?.list.pages === 2, probe?.list);
check('列表请求带上分页参数', queried[0]?.current === 1 && queried[0]?.size === 6, queried[0]);
const badges = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shushu"] .bwsp-table tbody tr')].map((tr) => ({
    name: tr.children[0]?.textContent ?? '',
    state: tr.children[2]?.textContent ?? '',
    tone: tr.children[2]?.querySelector('.bwsp-badge')?.dataset.tone ?? '',
  })),
);
check('状态列是中文徽标（通畅/陈旧/未通/无凭据）',
  badges.every((row) => ['通畅', '陈旧', '未通', '无凭据'].includes(row.state)), badges);
check('未通的殿标红、陈旧的标黄',
  badges.find((row) => row.name === '度支殿')?.tone === 'warn'
  && badges.find((row) => row.name === '时辰殿')?.tone === 'bad', badges);
const headlineCells = await page.evaluate(() =>
  [...document.querySelectorAll('[data-screen-id="shushu"] .bwsp-table tbody tr')].map((tr) => tr.children[1]?.textContent),
);
check('主指标是服务端排好版的字符串，不是裸数字', headlineCells.includes('营收 ¥123.4万'), headlineCells);

await page.screenshot({ path: `${OUT}/00-shushu-list.png` });

/* ---- 4. 进入屏风（贴脸态） ---- */
await page.locator('[data-screen-id="shushu"]').click();
await page.waitForTimeout(1400);
const engaged = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="shushu"]')?.dataset.focus ?? null,
  planar: document.querySelector('[data-screen-id="shushu"]')?.parentElement?.id ?? null,
}));
check('点击后进入屏风', engaged.engaged === 'shushu', engaged);
check('屏风进入贴脸态', engaged.focus === 'true', engaged.focus);
check('屏风搬到平面化层', engaged.planar === 'bww-planar', engaged.planar);

/* ---- 5. 关键字筛选：真的发给服务端 ---- */
await page.locator(SCOPE + '.bwsp-search').fill('殿');
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
const lastKeyword = queried.filter((item) => item.keyword === '殿').pop();
check('关键字查询参数发给服务端', Boolean(lastKeyword), queried.slice(-3));
check('筛选后 8 条（户部不含"殿"）', probe?.list.total === 8, probe?.list);

await page.locator(SCOPE + '.bwsp-search').fill('户部');
await page.waitForTimeout(700);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
check('精确到殿：1 条', probe?.list.total === 1 && probe?.list.rows === 1, probe?.list);
await page.locator(SCOPE + '.bwsp-search').fill('');
await page.waitForTimeout(700);

/* ---- 6. 状态筛选 ---- */
await page.locator(SCOPE + '.bwsp-status').selectOption('degraded');
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
const lastState = queried.filter((item) => item.state === 'degraded').pop();
check('状态筛选参数发给服务端', Boolean(lastState), queried.slice(-3));
check('只剩 2 座未通', probe?.list.total === 2 && probe?.list.rows === 2, probe?.list);
await page.locator(SCOPE + '.bwsp-status').selectOption('');
await page.waitForTimeout(600);

/* ---- 7. 分页 ---- */
const nextBtn = page.locator(SCOPE + '.bwsp-pager .bwsp-btn').last();
await nextBtn.click();
await page.waitForTimeout(500);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
check('第 2 页只剩 3 条', probe?.list.page === 2 && probe?.list.rows === 3, probe?.list);
check('末页禁用下一页', (await nextBtn.isDisabled()) === true);
await page.locator(SCOPE + '.bwsp-pager .bwsp-btn').first().click();
await page.waitForTimeout(400);

/* ---- 8. 点行看详情（与表格同一份快照） ---- */
await page.locator(SCOPE + '.bwsp-table tbody tr').first().click();
await page.waitForTimeout(600);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
check('点行打开详情', probe?.detail.open === true, probe?.detail);
check('详情标题是殿名', probe?.detail.title === '户部', probe?.detail.title);
check('详情 5 个字段 + 3 行指标', probe?.detail.fields === 5 && probe?.detail.lines === 3, probe?.detail);
await page.screenshot({ path: `${OUT}/01-shushu-detail.png` });

await page.locator(SCOPE + '.bwsp-dhead .bwsp-btn').click();
await page.waitForTimeout(400);
probe = await page.evaluate(() => window.__bwwScreenPage?.('shushu'));
check('返回后列表还在', probe?.detail.open === false && probe?.list.rows === 6, probe);

/* ---- 9. Esc 退出 ---- */
await page.keyboard.press('Escape');
await page.waitForTimeout(1300);
const exited = await page.evaluate(() => ({
  engaged: document.body.dataset.screenEngaged ?? null,
  focus: document.querySelector('[data-screen-id="shushu"]')?.dataset.focus ?? null,
}));
check('Esc 退出屏风', exited.engaged === null, exited);
check('退出后恢复非贴脸态', exited.focus === 'false', exited.focus);

/* ---- 10. 接口全挂时不白屏：有旧值就标"陈旧"，列表给一句人话 + 重试 ---- */
await page.route('**/monitor/overview**', (route) =>
  route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }),
);
await page.locator('[data-screen-id="shushu"]').click();
await page.waitForTimeout(1400);
// 屏风页只在挂载时取一次数，之后靠"刷新"或 60s 定时器；这里手动刷一次，让失败真的发生
await page.locator('[data-screen-id="shushu"] .bwsp-tool .bwsp-btn').click();
await page.waitForTimeout(1500);
const degraded = await page.evaluate(() => {
  const probeNow = window.__bwwScreenPage?.('shushu');
  const empty = document.querySelector('[data-screen-id="shushu"] .bwsp-empty')?.textContent ?? '';
  const listWrap = document.querySelector('[data-screen-id="shushu"] .bwsp-tablewrap');
  return {
    overview: probeNow?.overview,
    list: probeNow?.list,
    empty,
    listHidden: listWrap ? getComputedStyle(listWrap).display === 'none' : null,
  };
});
// 有旧值就不能装成实时：这是契约里 stale 的全部意义
check('接口挂了但有旧值：概览标 stale 而不是 live', degraded.overview?.state === 'stale', degraded.overview);
check('列表失败时收起表格并给一句人话', degraded.listHidden === true && /未能取到/.test(degraded.empty), degraded);
await page.screenshot({ path: `${OUT}/02-shushu-degraded.png` });

check('控制台无报错', errors.length === 0, errors.slice(0, 5));

console.log(`\nRESULT ${pass} 项通过 / ${fail} 项失败`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
