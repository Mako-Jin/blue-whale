/**
 * 屏风业务页（S4 / T4.5）：把一个"真业务页"装进屏风里。
 *
 * <p>与数据厅（`datahall.ts`）的分工：数据厅是<b>只读</b>的 3D 展厅，画在 Canvas 上；
 * 屏风页是<b>可交互</b>的真 DOM —— 搜索框能输入中文、表格能滚、行能点、
 * 详情能返回。两者共用同一份 `/<服务>/overview` 快照，数字不会两处不一致。</p>
 *
 * <p><b>为什么要一个通用渲染器，而不是给户部写死一张页：</b>S5 有 9 殿 + 12 坊，
 * 每殿都手写一张页，三周后就是 21 套各不相同的表格与分页逻辑。这里把
 * "取数 / 筛选 / 分页 / 详情 / 降级"做成一次，接入成本降为
 * {@link ScreenListPageSpec} 一条配置（见 `screenpages.ts`）。</p>
 *
 * <p><b>三条硬约束</b>（与 `overview.ts` 同口径，都是踩过的坑）：</p>
 * <ol>
 *   <li>没权限不发请求：已知缺权限码直接显示"无此殿凭据"，不刷网关日志。</li>
 *   <li>永不抛：所有失败折成状态 + 一句人话，渲染层不需要 try/catch。</li>
 *   <li>概览挂了页面还在：概览（KPI/图表/入口）失败只是少一层，列表照常用本地
 *       登记表打开——屏风页不该因为"看一眼"的接口挂了就变成一块死板。</li>
 * </ol>
 */

import { fetchOverview, formatKpiValue, formatValue, type OverviewResult } from './overview';
import type { OverviewChart, OverviewSnapshot } from './overview.types';
import type { ScreenColumn, ScreenListPageSpec } from './screenpages';
import type { ScreenTone } from './screen';

/* ---------------------------------------------------------------- 类型 */

/** 一行业务数据（字段由后端决定，前端只按列配置取值） */
type Row = Record<string, unknown>;

/** 列表状态：ready 与 empty 分开，是为了区分"没查到"与"没数据" */
type ListState = 'idle' | 'loading' | 'ready' | 'empty' | 'denied' | 'error';

/** 屏风页选项 */
export interface ScreenPageOptions {
  /** 城内节点 key，与 city-layout.json 对齐（概览按它取数） */
  nodeKey: string;
  /** 概览路径，例如 `/erp/overview` */
  overviewPath: string;
  /** 概览要求的权限码；缺它则概览层显示"无凭据"，列表仍可用 */
  overviewPermission?: string;
  /** 本殿可用的列表页（key 与概览 `links[].key` 对齐） */
  pages: ScreenListPageSpec[];
  /** 取访问令牌；返回 null 视为未登录 */
  getToken: () => string | null;
  /** 取当前用户权限码；返回 null 表示未知（不预判，交给接口 403） */
  getPermissions?: () => string[] | null;
  /** 单次请求超时，默认 6 秒 */
  timeoutMillis?: number;
  /** 自动刷新间隔，默认 60 秒；设 0 关闭（e2e 断言"不自己变"时用） */
  refreshMillis?: number;
  /** 状态灯变化（绿/黄/红/灰），由屏风题头显示 */
  onTone?: (tone: ScreenTone) => void;
  /** 概览拿到标题/期间后回调，让屏风题头显示服务端口径 */
  onTitle?: (title: string, subtitle?: string) => void;
}

/** 给 e2e 的只读探针（正常路径不依赖它） */
export interface ScreenPageProbe {
  mounted: boolean;
  mocked: boolean;
  overview: { state: string; note: string; kpis: number; charts: number; alerts: number };
  tabs: { key: string; label: string; enabled: boolean }[];
  active: string | null;
  list: {
    state: ListState;
    rows: number;
    total: number;
    page: number;
    pages: number;
    keyword: string;
    status: string;
    error: string;
  };
  detail: { open: boolean; title: string; fields: number; lines: number; state: string };
}

/** 屏风页句柄 */
export interface ScreenPageHandle {
  /** 手动刷新（概览 + 当前列表） */
  refresh: () => Promise<void>;
  dispose: () => void;
  probe: () => ScreenPageProbe;
  /** 仅 ?debug=1 暴露：注入一份确定的假数据，让无后端环境也能评审/断言 */
  mock: () => void;
}

/* ---------------------------------------------------------------- 小工具 */

/** 建元素：整个模块只用它，避免 innerHTML 拼字符串（业务数据不可信） */
function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  cls?: string,
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}

/** 取数不抛的返回值 */
interface JsonResult {
  ok: boolean;
  status: number;
  data: unknown;
  error: string;
}

/** 缺权限且能确定时返回 true（未注入权限时留一次机会，真 403 再降级） */
function lacksPermission(permission: string | undefined, options: ScreenPageOptions): boolean {
  if (!permission) return false;
  const perms = options.getPermissions?.();
  if (!perms || perms.length === 0) return false;
  return !perms.includes(permission);
}

/**
 * 按列配置取一行里的值，支持 `a.b` 取嵌套字段。
 *
 * <p>只认对象与数组下标，不做表达式求值：列配置是代码里写死的，但业务数据不可信，
 * 走到一半遇到 `null` 就返回 `undefined`，交给单元格兜底成 `—` 或列的 `emptyText`。</p>
 *
 * @param row 一行数据
 * @param key 字段名，可带点
 * @returns 取到的值；路径断了给 undefined
 */
function pick(row: Row, key: string): unknown {
  if (!key.includes('.')) return row[key];
  let current: unknown = row;
  for (const part of key.split('.')) {
    if (current === null || typeof current !== 'object') return undefined;
    current = (current as Row)[part];
  }
  return current;
}

/** 数字兜底：字符串数字也认，非数字一律 0（NaN 会画成空白柱） */
function num(value: unknown): number {
  const n = typeof value === 'string' ? Number(value) : (value as number);
  return Number.isFinite(n) ? n : 0;
}

/* ---------------------------------------------------------------- 渲染 */

/**
 * 挂载一张屏风业务页。
 *
 * @param body 屏风内容区（`.bws-body`）
 * @param options 概览路径、列表配置、令牌与权限来源
 * @returns 句柄（刷新 / 销毁 / 探针 / 假数据）
 */
export function mountScreenPage(body: HTMLElement, options: ScreenPageOptions): ScreenPageHandle {
  const timeoutMillis = options.timeoutMillis ?? 6_000;
  const refreshMillis = options.refreshMillis ?? 60_000;
  const pagesByKey = new Map(options.pages.map((page) => [page.key, page]));

  /* 屏风立着但没进入（远处隔着 3D 看它）时，行、按钮都不该响应：
     玩家那一刻是在"走近"，不是在对着一张表操作。真正的点击要留给外层
     screen.ts 的"进屏风"，否则点一下行会先弹出详情、再被过场盖住。 */
  const engaged = (): boolean =>
    (body.closest('.bws-screen') as HTMLElement | null)?.dataset.focus === 'true';

  /* ---- 状态 ---- */
  let overview: OverviewResult | null = null;
  let activeKey: string | null = null;
  let rows: Row[] = [];
  /** 一次取回的整份数组（仅 `clientPaged` 页使用；其余页为空） */
  let allRows: Row[] = [];
  let total = 0;
  let page = 1;
  let keyword = '';
  let status = '';
  let listState: ListState = 'idle';
  let listError = '';
  let detail: {
    open: boolean;
    row: Row | null;
    data: Row | null;
    state: 'idle' | 'loading' | 'ready' | 'error';
    error: string;
  } = { open: false, row: null, data: null, state: 'idle', error: '' };
  let mocked = false;
  let mockData: Row[] | null = null;
  let disposed = false;
  let refreshTimer: number | null = null;
  let keywordTimer: number | null = null;
  /** 已铺过状态选项的页 key：切页时重建，避免"长度相同但选项不同"的脏下拉 */
  let statusPageKey: string | null = null;

  /* ---- DOM 骨架 ---- */
  const root = h('div', 'bwsp');
  const kpiBox = h('div', 'bwsp-kpis');
  const chartBox = h('div', 'bwsp-chart');
  const tabsBox = h('nav', 'bwsp-tabs');
  const tool = h('div', 'bwsp-tool');
  const search = h('input', 'bws-input bwsp-search');
  search.type = 'text';
  search.placeholder = '搜索…';
  const statusSel = h('select', 'bws-input bwsp-status');
  const refreshBtn = h('button', 'bwsp-btn', '刷新');
  refreshBtn.type = 'button';
  const countEl = h('span', 'bwsp-count', '');
  tool.append(search, statusSel, refreshBtn, countEl);

  const mainBox = h('div', 'bwsp-main');
  const tableWrap = h('div', 'bwsp-tablewrap');
  const table = h('table', 'bws-list bwsp-table');
  const thead = h('thead');
  const tbody = h('tbody');
  table.append(thead, tbody);
  tableWrap.appendChild(table);
  const pager = h('div', 'bwsp-pager');
  const prevBtn = h('button', 'bwsp-btn', '上一页');
  const nextBtn = h('button', 'bwsp-btn', '下一页');
  prevBtn.type = 'button';
  nextBtn.type = 'button';
  const pageEl = h('span', 'bwsp-page', '');
  pager.append(prevBtn, pageEl, nextBtn);
  const emptyBox = h('div', 'bwsp-empty');
  mainBox.append(tableWrap, pager, emptyBox);

  const detailBox = h('section', 'bwsp-detail');
  const detailHead = h('div', 'bwsp-dhead');
  const backBtn = h('button', 'bwsp-btn', '← 返回列表');
  backBtn.type = 'button';
  const detailTitle = h('b', 'bwsp-dtitle', '');
  detailHead.append(backBtn, detailTitle);
  const detailBody = h('div', 'bwsp-dbody');
  detailBox.append(detailHead, detailBody);

  root.append(kpiBox, chartBox, tabsBox, tool, mainBox, detailBox);
  body.appendChild(root);

  /* ---- 请求 ---- */

  /** 发起一次 GET 并拆掉 `{code,message,data}` 信封；任何失败都折成结果对象 */
  async function getJson(path: string, params?: Record<string, string>): Promise<JsonResult> {
    const query = params && Object.keys(params).length > 0 ? `?${new URLSearchParams(params)}` : '';
    const token = options.getToken();
    if (!token) {
      return { ok: false, status: 401, data: null, error: '未登录' };
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeoutMillis);
    try {
      const response = await fetch(`${path}${query}`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
        credentials: 'omit',
        signal: controller.signal,
      });
      if (response.status === 401 || response.status === 403) {
        return {
          ok: false,
          status: response.status,
          data: null,
          error: permissionNote(response.status),
        };
      }
      if (!response.ok) {
        return { ok: false, status: response.status, data: null, error: `HTTP ${response.status}` };
      }
      const contentType = response.headers.get('content-type') ?? '';
      if (!contentType.includes('json')) {
        return {
          ok: false,
          status: response.status,
          data: null,
          error: '非 JSON 响应（多半是代理未配）',
        };
      }
      const payload: unknown = await response.json();
      const wrapped = Boolean(payload && typeof payload === 'object' && 'code' in (payload as Row));
      if (wrapped) {
        const body = payload as Row;
        if (num(body.code) !== 0) {
          return { ok: false, status: 200, data: null, error: String(body.message ?? '业务拒绝') };
        }
        return { ok: true, status: 200, data: body.data, error: '' };
      }
      return { ok: true, status: 200, data: payload, error: '' };
    } catch (error) {
      const reason = controller.signal.aborted
        ? '问询超时'
        : (error instanceof Error && error.message ? error.message : '未接通').slice(0, 28);
      return { ok: false, status: 0, data: null, error: reason };
    } finally {
      window.clearTimeout(timer);
    }
  }

  function permissionNote(code: number): string {
    return code === 401 ? '登录已失效' : '无此殿凭据';
  }

  /* ---- 概览层 ---- */

  function toneOf(result: OverviewResult | null): ScreenTone {
    if (!result) return 'idle';
    if (result.state === 'denied') return 'idle';
    if (result.state === 'live') {
      const alerts = result.snapshot?.alerts ?? [];
      return alerts.some((alert) => alert.level !== 'info') ? 'warn' : 'good';
    }
    if (result.state === 'stale') return 'warn';
    return 'bad';
  }

  async function loadOverview(): Promise<void> {
    // 演示数据模式下不再问后端：否则 60s 后的自动刷新会把注入的 KPI 冲成"未登录"
    if (mocked) return;
    const next = await fetchOverview(
      {
        nodeKey: options.nodeKey,
        path: options.overviewPath,
        permission: options.overviewPermission,
      },
      { getToken: options.getToken, getPermissions: options.getPermissions, timeoutMillis },
      overview,
    );
    if (disposed) return;
    overview = next;
    options.onTone?.(toneOf(next));
    if (next.snapshot) {
      options.onTitle?.(next.snapshot.title, next.snapshot.subtitle);
    }
    paintTabs();
    paintOverview();
  }

  /** 概览里的入口优先；概览不可用时退回本地登记表，屏风页不因概览挂了而消失 */
  function tabItems(): { key: string; label: string; enabled: boolean }[] {
    const links = overview?.snapshot?.links ?? [];
    if (links.length > 0) {
      return links.map((link) => {
        const spec = pagesByKey.get(link.key);
        return {
          key: link.key,
          label: link.label || spec?.title || link.key,
          enabled: Boolean(spec) && !lacksPermission(spec?.permission, options),
        };
      });
    }
    return options.pages.map((spec) => ({
      key: spec.key,
      label: spec.title,
      enabled: !lacksPermission(spec.permission, options),
    }));
  }

  function activeSpec(): ScreenListPageSpec | null {
    return activeKey ? (pagesByKey.get(activeKey) ?? null) : null;
  }

  function paintTabs(): void {
    const items = tabItems();
    tabsBox.replaceChildren();
    if (items.length === 0) {
      tabsBox.hidden = true;
      return;
    }
    tabsBox.hidden = false;
    if (!activeKey || !items.some((item) => item.key === activeKey && item.enabled)) {
      const first = items.find((item) => item.enabled);
      activeKey = first ? first.key : activeKey;
    }
    for (const item of items) {
      const tab = h('button', 'bwsp-tab', item.label);
      tab.type = 'button';
      tab.dataset.tabKey = item.key;
      tab.dataset.active = String(item.key === activeKey);
      tab.disabled = !item.enabled;
      if (!item.enabled) tab.title = '尚未接入或缺少权限';
      tab.addEventListener('click', () => {
        if (!engaged() || !item.enabled || item.key === activeKey) return;
        activeKey = item.key;
        page = 1;
        keyword = '';
        search.value = '';
        detail = { open: false, row: null, data: null, state: 'idle', error: '' };
        paintTabs();
        paintTool();
        void loadList();
      });
      tabsBox.appendChild(tab);
    }
  }

  function paintOverview(): void {
    kpiBox.replaceChildren();
    const snapshot = overview?.snapshot ?? null;
    const kpis = snapshot?.kpis ?? [];
    if (kpis.length === 0) {
      kpiBox.hidden = true;
    } else {
      kpiBox.hidden = false;
      // 屏风高度有限：只放前 4 个，剩下的看列表与详情
      for (const kpi of kpis.slice(0, 4)) {
        const card = h('div', 'bwsp-kpi');
        card.dataset.tone = kpi.tone ?? 'flat';
        const value = h('b', 'bwsp-kpi-value', formatKpiValue(kpi));
        const label = h(
          'span',
          'bwsp-kpi-label',
          kpi.hint ? `${kpi.label} · ${kpi.hint}` : kpi.label,
        );
        card.append(value, label);
        kpiBox.appendChild(card);
      }
    }
    paintChart(snapshot);
    paintTool();
    paintMain();
  }

  function paintChart(snapshot: OverviewSnapshot | null): void {
    chartBox.replaceChildren();
    const chart = snapshot?.charts?.[0];
    if (!chart) {
      chartBox.hidden = true;
      return;
    }
    chartBox.hidden = false;
    const head = h('div', 'bwsp-chart-head');
    head.append(h('span', 'bwsp-chart-title', chart.title));
    if (overview?.state === 'stale') head.append(h('span', 'bwsp-badge', '陈旧'));
    chartBox.appendChild(head);
    if (chart.type === 'line') {
      chartBox.appendChild(lineChart(chart));
    } else if (chart.type === 'donut') {
      chartBox.appendChild(donutChart(chart));
    } else {
      chartBox.appendChild(barChart(chart));
    }
  }

  function barChart(chart: OverviewChart): HTMLElement {
    const values = chart.series[0]?.values ?? [];
    const max = Math.max(1, ...values.map((value) => Math.abs(num(value))));
    /* 横向条形：屏风是 1000px 宽 620px 高的横屏，竖柱会把 3 个值摊在
       1100px 宽里、只剩几根发丝细的柱子；横条让"标签—长度—数值"一行读完。 */
    const wrap = h('div', 'bwsp-bars');
    chart.categories.forEach((category, index) => {
      const value = num(values[index]);
      const row = h('div', 'bwsp-barrow');
      const track = h('div', 'bwsp-bartrack');
      const bar = h('div', 'bwsp-bar');
      bar.style.width = `${Math.max(2, (Math.abs(value) / max) * 100)}%`;
      track.appendChild(bar);
      row.append(
        h('span', 'bwsp-barlabel', category),
        track,
        h('span', 'bwsp-barval', formatValue(chart.format, value)),
      );
      wrap.appendChild(row);
    });
    return wrap;
  }

  /* 折线支持多条序列：发起 vs 办结这种"进 / 出"对比，只有画在同一张图上才有意义。
     早前这里只取 series[0]，第二条序列会被**静默丢弃**——图上看着完全正常，
     只是少了一半数。多条时按调色板分别描线，并补一个最小图例（两根线不加图例，
     看的人分不清哪根是发起）。 */
  function lineChart(chart: OverviewChart): Element {
    const palette = ['#a8702c', '#3f9e6e', '#4f7fa8', '#c9962f', '#8c6b9e'];
    const series =
      chart.series.length > 0 ? chart.series : [{ name: '数值', values: [] as number[] }];
    const slots = Math.max(chart.categories.length, ...series.map((item) => item.values.length));
    const all = series.flatMap((item) => item.values.map(num));
    const max = Math.max(1, ...all.map((value) => Math.abs(value)));
    const step = slots > 1 ? 100 / (slots - 1) : 100;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 100 34');
    /* **必须拉伸而不是等比缩放**：容器是 830×48（宽高比 17:1），viewBox 是 100:34（3:1）。
       默认 preserveAspectRatio="xMidYMid meet" 会按高度等比缩放，线只占中间约 17% 宽、
       两侧一大片空白——七座用折线的殿（时辰/政事/考功/通政/起居/度支/兵部）全是这个样子，
       而横轴标签是 space-between 铺满整宽的，线和标签对不上。 */
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.setAttribute('class', 'bwsp-line');
    series.forEach((item, index) => {
      const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
      polyline.setAttribute(
        'points',
        item.values
          .map(
            (value, position) =>
              `${(position * step).toFixed(2)},${(30 - (num(value) / max) * 26).toFixed(2)}`,
          )
          .join(' '),
      );
      polyline.setAttribute('fill', 'none');
      polyline.setAttribute('stroke', palette[index % palette.length]);
      /* 非等比缩放会把描边一起拉扁（竖线粗 8 倍、横线几乎消失）：
         non-scaling-stroke 让线宽按屏幕像素算，与缩放无关。 */
      polyline.setAttribute('vector-effect', 'non-scaling-stroke');
      polyline.setAttribute('stroke-width', '1.2');
      svg.appendChild(polyline);
    });
    const wrap = h('div', 'bwsp-linewrap');
    wrap.appendChild(svg);
    const labels = h('div', 'bwsp-barlabels');
    for (const category of chart.categories) labels.appendChild(h('span', '', category));
    wrap.appendChild(labels);
    if (series.length > 1) {
      const legend = h('div', 'bwsp-legend');
      series.forEach((item, index) => {
        const entry = h('span', 'bwsp-legend-item');
        const dot = h('i', 'bwsp-dot');
        dot.style.background = palette[index % palette.length];
        entry.append(dot, h('span', '', item.name));
        legend.appendChild(entry);
      });
      wrap.appendChild(legend);
    }
    return wrap;
  }

  function donutChart(chart: OverviewChart): Element {
    const values = (chart.series[0]?.values ?? []).map((value) => Math.max(0, num(value)));
    const sum = values.reduce((acc, value) => acc + value, 0) || 1;
    const wrap = h('div', 'bwsp-donutwrap');
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 42 42');
    svg.setAttribute('class', 'bwsp-donut');
    const palette = ['#a8702c', '#3f9e6e', '#4f7fa8', '#c9962f', '#8c6b9e', '#b5452f'];
    let offset = 0;
    values.forEach((value, index) => {
      const share = (value / sum) * 100;
      const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      circle.setAttribute('cx', '21');
      circle.setAttribute('cy', '21');
      circle.setAttribute('r', '15.915');
      circle.setAttribute('fill', 'none');
      circle.setAttribute('stroke', palette[index % palette.length]);
      circle.setAttribute('stroke-width', '6');
      circle.setAttribute('stroke-dasharray', `${share} ${100 - share}`);
      circle.setAttribute('stroke-dashoffset', String(25 - offset));
      svg.appendChild(circle);
      offset += share;
    });
    wrap.appendChild(svg);
    const legend = h('div', 'bwsp-legend');
    chart.categories.forEach((category, index) => {
      const item = h('span', 'bwsp-legend-item');
      item.append(
        h('i', 'bwsp-dot'),
        h('span', '', `${category} ${formatValue(chart.format, values[index] ?? 0)}`),
      );
      legend.appendChild(item);
    });
    wrap.appendChild(legend);
    return wrap;
  }

  /* ---- 工具栏 / 列表 ---- */

  function paintTool(): void {
    const spec = activeSpec();
    search.hidden = !spec?.keywordParam;
    if (spec?.searchPlaceholder) search.placeholder = spec.searchPlaceholder;
    statusSel.hidden = !spec?.statusParam;
    if (spec?.statuses && statusPageKey !== spec.key) {
      statusPageKey = spec.key;
      statusSel.replaceChildren();
      for (const item of spec.statuses) {
        const option = h('option', '', item.label);
        option.value = item.value;
        statusSel.appendChild(option);
      }
      statusSel.value = status;
    }
    countEl.textContent =
      listState === 'ready' || listState === 'empty'
        ? `共 ${total} 条 · 第 ${page}/${pageCount()} 页`
        : '';
  }

  function pageCount(): number {
    return Math.max(1, Math.ceil(total / pageSize()));
  }

  function pageSize(): number {
    return activeSpec()?.pageSize ?? 8;
  }

  /**
   * 本地切页：只给 `clientPaged` 页用。
   *
   * <p>页码越界时先收回末页再切——刷新后列表变短是常事（会话被解散、消息被删），
   * 停在"第 3/2 页"会看到一片空白，而数据其实是有的。</p>
   */
  function slicePage(): void {
    total = allRows.length;
    const size = pageSize();
    const pages = Math.max(1, Math.ceil(total / size));
    if (page > pages) page = pages;
    if (page < 1) page = 1;
    rows = allRows.slice((page - 1) * size, page * size);
    listState = total === 0 ? 'empty' : 'ready';
  }

  /** 翻页：本地分页的页只切数组，其余页回后端取（保持原行为） */
  function stepPage(delta: number): void {
    const next = page + delta;
    if (next < 1 || next > pageCount()) return;
    page = next;
    if (activeSpec()?.clientPaged) {
      slicePage();
      paintTool();
      paintMain();
      return;
    }
    void loadList();
  }

  /**
   * 字节数排成可读文本（1024 进制）
   *
   * <p>不足 1KB 保留整数（"512 B"），其余一位小数——磁盘数字的用处是"量级对不对"，
   * 精确到字节只会把一列撑宽。</p>
   *
   * @param bytes 字节数
   * @returns 形如 "1.2 MB" 的文本
   */
  function formatBytes(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit += 1;
    }
    return unit === 0 ? `${Math.round(value)} B` : `${value.toFixed(1)} ${units[unit]}`;
  }

  function cellText(spec: ScreenListPageSpec, column: ScreenColumn, row: Row): string {
    const value = pick(row, column.key);
    switch (column.format) {
      case 'money':
      case 'count':
      case 'percent':
        return formatValue(column.format, num(value));
      case 'date':
        return typeof value === 'string' && value.length >= 10 ? value.slice(0, 10) : '—';
      /* 日期+时分：登录历史这类"同一天很多条"的表只显示日期等于没有时间信息，
         而屏风宽度又放不下秒。ISO 串按位置切，不做 new Date()——时区换算会把
         后端给的事件时间再挪一次，同一行在城里和后台显示成两个时刻。 */
      case 'datetime':
        return typeof value === 'string' && value.length >= 16
          ? `${value.slice(5, 10)} ${value.slice(11, 16)}`
          : typeof value === 'string' && value.length >= 10
            ? value.slice(0, 10)
            : '—';
      case 'status': {
        const key = String(value ?? '');
        /* 列级字典优先：同一张表里可能混着两种状态空间（流程实例 + 它的审批链），
           两者状态码相同、含义不同，用页面级字典会把"待办"译成"运行中"且不报错。 */
        const map = column.statusMap ?? spec.statusMap;
        return map?.[key]?.label ?? (key || '—');
      }
      /* 字节数：1024 进制、一位小数。用 1024 而不是 1000 是因为后端
         配额/上限也是按 1024 算的，两边不一致时"刚满 50MB 却被拒"最难解释。 */
      case 'size':
        return formatBytes(num(value));
      default:
        return value === null || value === undefined || value === ''
          ? (column.emptyText ?? '—')
          : String(value);
    }
  }

  function paintTable(): void {
    const spec = activeSpec();
    thead.replaceChildren();
    tbody.replaceChildren();
    if (!spec) return;
    const headRow = h('tr');
    for (const column of spec.columns) {
      const th = h('th', '', column.label);
      if (column.width) th.style.width = column.width;
      if (column.align === 'right') th.style.textAlign = 'right';
      headRow.appendChild(th);
    }
    thead.appendChild(headRow);

    for (const row of rows) {
      const tr = h('tr', 'bwsp-row');
      tr.dataset.rowId = String(row[spec.idKey ?? 'id'] ?? '');
      for (const column of spec.columns) {
        const td = h('td', '', '');
        if (column.align === 'right') td.style.textAlign = 'right';
        if (column.format === 'status') {
          const key = String(row[column.key] ?? '');
          const badge = h('span', 'bwsp-badge', cellText(spec, column, row));
          const map = column.statusMap ?? spec.statusMap;
          badge.dataset.tone = map?.[key]?.tone ?? 'flat';
          td.appendChild(badge);
        } else {
          td.textContent = cellText(spec, column, row);
        }
        tr.appendChild(td);
      }
      tr.addEventListener('click', () => {
        if (!engaged()) return;
        void openDetail(row);
      });
      tbody.appendChild(tr);
    }
  }

  function paintMain(): void {
    const spec = activeSpec();
    const denied = Boolean(spec) && lacksPermission(spec?.permission, options);
    const showEmpty = listState !== 'ready' || rows.length === 0;

    detailBox.style.display = detail.open ? '' : 'none';
    tableWrap.style.display = detail.open ? 'none' : '';
    pager.style.display = detail.open ? 'none' : '';
    emptyBox.style.display = detail.open ? 'none' : '';
    /* 详情态收起 KPI/图表/标签/工具栏：大屏只有 620px 高，留着它们
       分录表会被挤到屏幕外——详情本来就该占满整块屏。 */
    const chrome = detail.open ? 'none' : '';
    kpiBox.style.display = chrome;
    chartBox.style.display = chrome;
    tabsBox.style.display = chrome;
    tool.style.display = chrome;

    if (!detail.open) {
      if (!spec) {
        emptyBox.textContent = '本殿暂无可用页面';
        emptyBox.style.display = '';
        tableWrap.style.display = 'none';
        pager.style.display = 'none';
        return;
      }
      paintTable();
      if (showEmpty) {
        tableWrap.style.display = 'none';
        pager.style.display = 'none';
        emptyBox.style.display = '';
        emptyBox.replaceChildren();
        emptyBox.appendChild(h('div', '', emptyText(denied)));
        if (listState === 'error' || listState === 'denied') {
          const retry = h('button', 'bwsp-btn', '重试');
          retry.type = 'button';
          retry.addEventListener('click', () => void loadList());
          emptyBox.appendChild(retry);
        }
      } else {
        emptyBox.style.display = 'none';
        emptyBox.replaceChildren();
      }
      prevBtn.disabled = page <= 1;
      nextBtn.disabled = page >= pageCount();
      pageEl.textContent = `${page} / ${pageCount()}`;
      prevBtn.hidden = false;
      nextBtn.hidden = false;
      pageEl.hidden = false;
    } else {
      paintDetail();
    }
  }

  function emptyText(denied: boolean): string {
    if (denied) return '无此殿凭据 · 请联系主事申请权限';
    switch (listState) {
      case 'loading':
        return '问询中…';
      case 'error':
        return `未能取到列表：${listError}`;
      case 'denied':
        return listError || '无此殿凭据';
      case 'empty':
        return '没有符合条件的记录';
      default:
        return '暂无数据';
    }
  }

  /* ---- 详情 ---- */

  function paintDetail(): void {
    const spec = activeSpec();
    detailBody.replaceChildren();
    if (!spec || !detail.data) {
      detailTitle.textContent = '详情';
      detailBody.appendChild(
        h('div', 'bwsp-empty', detail.state === 'loading' ? '问询中…' : '暂无详情'),
      );
      return;
    }
    const row = detail.data;
    detailTitle.textContent = String(pick(row, spec.titleKey ?? spec.columns[0]?.key ?? 'id') ?? '详情');

    if (spec.detailFields && spec.detailFields.length > 0) {
      const grid = h('dl', 'bwsp-kv');
      for (const field of spec.detailFields) {
        grid.appendChild(h('dt', '', field.label));
        const dd = h('dd', '');
        if (field.format === 'status') {
          const key = String(pick(row, field.key) ?? '');
          const badge = h('span', 'bwsp-badge', cellText(spec, field, row));
          const map = field.statusMap ?? spec.statusMap;
          badge.dataset.tone = map?.[key]?.tone ?? 'flat';
          dd.appendChild(badge);
        } else {
          dd.textContent = cellText(spec, field, row);
        }
        grid.appendChild(dd);
      }
      detailBody.appendChild(grid);
    }

    const lineSource = spec.lineSource ?? 'lines';
    const lines = Array.isArray(row[lineSource]) ? (row[lineSource] as Row[]) : [];
    if (spec.lineColumns && spec.lineColumns.length > 0 && lines.length > 0) {
      detailBody.appendChild(h('h5', 'bwsp-h5', `分录 · ${lines.length} 行`));
      const wrap = h('div', 'bwsp-lines');
      const tableEl = h('table', 'bws-list');
      const head = h('thead');
      const headRow = h('tr');
      for (const column of spec.lineColumns) {
        const th = h('th', '', column.label);
        if (column.align === 'right') th.style.textAlign = 'right';
        headRow.appendChild(th);
      }
      head.appendChild(headRow);
      const bodyEl = h('tbody');
      for (const line of lines) {
        const tr = h('tr');
        for (const column of spec.lineColumns) {
          const td = h('td', '', cellText(spec, column, line));
          if (column.align === 'right') td.style.textAlign = 'right';
          tr.appendChild(td);
        }
        bodyEl.appendChild(tr);
      }
      tableEl.append(head, bodyEl);
      wrap.appendChild(tableEl);
      detailBody.appendChild(wrap);
    }

    const remark = row.remark;
    if (typeof remark === 'string' && remark.trim() !== '') {
      detailBody.appendChild(h('p', 'bwsp-note', `备注：${remark}`));
    }
    if (detail.state === 'error') {
      detailBody.appendChild(h('p', 'bwsp-note', `详情未能取到：${detail.error}`));
    }
  }

  async function openDetail(row: Row): Promise<void> {
    const spec = activeSpec();
    if (!spec) return;
    detail = { open: true, row, data: row, state: 'ready', error: '' };
    paintMain();
    if (!spec.detailPath || mocked) return;
    const id = String(row[spec.idKey ?? 'id'] ?? '');
    if (!id) return;
    detail.state = 'loading';
    paintMain();
    const result = await getJson(spec.detailPath(id));
    if (disposed) return;
    if (result.ok && result.data && typeof result.data === 'object') {
      detail = { open: true, row, data: result.data as Row, state: 'ready', error: '' };
    } else {
      // 详情失败不清空：列表行里已有的字段仍然可读，只是少了分录
      detail = { open: true, row, data: row, state: 'error', error: result.error };
    }
    paintMain();
  }

  /* ---- 列表取数 ---- */

  function applyMockPage(): void {
    const spec = activeSpec();
    if (!spec || !mockData) return;
    const filtered = mockData.filter((row) => {
      if (status && spec.statusParam && String(row.status ?? '') !== status) return false;
      if (!keyword) return true;
      const hay =
        `${row.docNo ?? ''} ${row.supplierName ?? ''} ${row.itemName ?? ''}`.toLowerCase();
      return hay.includes(keyword.toLowerCase());
    });
    total = filtered.length;
    const size = pageSize();
    const start = (page - 1) * size;
    rows = filtered.slice(start, start + size);
    listState = total === 0 ? 'empty' : 'ready';
    listError = '';
    paintTool();
    paintMain();
  }

  async function loadList(): Promise<void> {
    const spec = activeSpec();
    if (!spec || disposed) return;
    if (mocked) {
      applyMockPage();
      return;
    }
    if (!options.getToken()) {
      listState = 'denied';
      listError = '未登录';
      total = 0;
      rows = [];
      allRows = [];
      paintTool();
      paintMain();
      return;
    }
    if (lacksPermission(spec.permission, options)) {
      listState = 'denied';
      listError = '无此殿凭据';
      total = 0;
      rows = [];
      allRows = [];
      paintTool();
      paintMain();
      return;
    }
    listState = 'loading';
    paintTool();
    paintMain();
    /* 本地分页的接口（IM 会话列表）不认 current/size，带上只会让网关日志里
       多两个没人读的参数；取回整份后由 slicePage 切。
       分页参数名可覆盖（舆图司的围栏接口读 page/size）：字段名对不上时服务端
       会忽略参数、永远返回第 1 页，页码照样从 1 变到 3 且不报错——这类"看着
       在翻页、其实没翻"的坑只能靠配置对齐，不能靠猜。 */
    const params: Record<string, string> = spec.clientPaged
      ? {}
      : {
          [spec.pageParam ?? 'current']: String(page),
          [spec.sizeParam ?? 'size']: String(pageSize()),
        };
    if (spec.keywordParam && keyword) params[spec.keywordParam] = keyword;
    if (spec.statusParam && status) params[spec.statusParam] = status;
    const result = await getJson(spec.listPath, params);
    if (disposed) return;
    if (!result.ok) {
      listState = result.status === 401 || result.status === 403 ? 'denied' : 'error';
      listError = result.error;
      total = 0;
      rows = [];
      allRows = [];
    } else {
      const data = (result.data ?? {}) as Row;
      const records = Array.isArray(data)
        ? (data as unknown as Row[])
        : Array.isArray(data.records)
          ? (data.records as Row[])
          : [];
      if (spec.clientPaged) {
        allRows = records;
        slicePage();
      } else {
        rows = records;
        total = num(data.total ?? records.length);
        listState = rows.length === 0 ? 'empty' : 'ready';
      }
      listError = '';
    }
    paintTool();
    paintMain();
  }

  /* ---- 交互 ---- */

  search.addEventListener('input', () => {
    keyword = search.value.trim();
    if (keywordTimer !== null) window.clearTimeout(keywordTimer);
    // 260ms：短到"打完就出结果"，长到不会每敲一个字打一次网关
    keywordTimer = window.setTimeout(() => {
      page = 1;
      void loadList();
    }, 260);
  });

  statusSel.addEventListener('change', () => {
    if (!engaged()) return;
    status = statusSel.value;
    page = 1;
    void loadList();
  });

  prevBtn.addEventListener('click', () => {
    if (!engaged() || page <= 1) return;
    stepPage(-1);
  });

  nextBtn.addEventListener('click', () => {
    if (!engaged() || page >= pageCount()) return;
    stepPage(1);
  });

  refreshBtn.addEventListener('click', () => {
    if (!engaged()) return;
    void refresh();
  });

  backBtn.addEventListener('click', () => {
    if (!engaged()) return;
    detail = { open: false, row: null, data: null, state: 'idle', error: '' };
    paintMain();
  });

  /** 概览 + 当前列表一起刷：两处数字来自同一时刻，不会出现"KPI 是新的、列表是旧的" */
  async function refresh(): Promise<void> {
    if (mocked) {
      applyMockPage();
      return;
    }
    await loadOverview();
    await loadList();
  }

  /* ---- 启动 ---- */

  paintTabs();
  paintOverview();
  void refresh();

  if (refreshMillis > 0) {
    refreshTimer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      void refresh();
    }, refreshMillis);
  }

  /* ---- 句柄 ---- */

  function mockSnapshot(): OverviewSnapshot {
    const spec = activeSpec() ?? options.pages[0];
    return {
      service: options.nodeKey,
      nodeKey: options.nodeKey,
      title: '户部',
      subtitle: '经营驾驶舱 · 演示数据',
      generatedAt: new Date().toISOString(),
      state: 'live',
      kpis: [
        { key: 'revenue', label: '营收', value: 1234567.89, format: 'money', tone: 'good' },
        { key: 'orders', label: '订单', value: 4321, format: 'count', tone: 'flat' },
        { key: 'alerts', label: '预警', value: 2, format: 'count', tone: 'warn', hint: '未关闭' },
      ],
      charts: [
        {
          key: 'pnl',
          title: '本期 营收 / 成本 / 利润',
          type: 'bar',
          format: 'money',
          categories: ['营收', '成本', '利润'],
          series: [{ name: '金额', values: [1234567.89, 700000, 534567.89] }],
        },
      ],
      alerts: [{ level: 'warning', text: '有 2 条未关闭预警', count: 2 }],
      links: spec
        ? [{ key: spec.key, label: spec.title, path: spec.listPath, permission: spec.permission }]
        : [],
      notes: ['演示数据 · 仅 ?debug=1 下注入'],
    };
  }

  function mockRows(): Row[] {
    const statuses = ['DRAFT', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'];
    const suppliers = ['东海铜业', '云梦木行', '中原铁器', '江南丝坊', '西凉马场'];
    // 20 条 = 4 页（每页 6 条）：足够验证"翻页后不是同一批行"
    return Array.from({ length: 20 }, (_, index) => {
      const amount = 18600 + index * 3275.5;
      // 12 行分录：详情区必须能滚，否则接到真单据（几十行）时会被裁掉
      const lines = Array.from({ length: 12 }, (_, lineIndex) => ({
        lineNo: lineIndex + 1,
        itemCode: `MT-${String(index + 1).padStart(3, '0')}-${lineIndex + 1}`,
        itemName: lineIndex % 2 === 0 ? '青铜锭' : '辅料',
        uom: '件',
        quantity: 4 + lineIndex,
        unitPrice: 1550 + lineIndex * 35,
        amount: (4 + lineIndex) * (1550 + lineIndex * 35),
      }));
      return {
        id: 9000 + index,
        docNo: `PO-202611-${String(index + 1).padStart(4, '0')}`,
        supplierName: suppliers[index % suppliers.length],
        docDate: `2026-11-${String((index % 20) + 1).padStart(2, '0')}`,
        expectedDate: `2026-12-${String((index % 20) + 1).padStart(2, '0')}`,
        status: statuses[index % statuses.length],
        currency: 'CNY',
        totalAmount: amount,
        receivedAmount: index % 3 === 0 ? amount : amount / 2,
        remark: index === 0 ? '演示数据：首行带备注' : '',
        lines,
      };
    });
  }

  const handle: ScreenPageHandle = {
    refresh,
    dispose: () => {
      disposed = true;
      if (refreshTimer !== null) window.clearInterval(refreshTimer);
      if (keywordTimer !== null) window.clearTimeout(keywordTimer);
      root.remove();
    },
    probe: () => ({
      mounted: root.isConnected,
      mocked,
      overview: {
        state: overview?.state ?? 'idle',
        note: overview?.note ?? '',
        kpis: overview?.snapshot?.kpis?.length ?? 0,
        charts: overview?.snapshot?.charts?.length ?? 0,
        alerts: overview?.snapshot?.alerts?.length ?? 0,
      },
      tabs: tabItems(),
      active: activeKey,
      list: {
        state: listState,
        rows: rows.length,
        total,
        page,
        pages: pageCount(),
        keyword,
        status,
        error: listError,
      },
      detail: {
        open: detail.open,
        title: detailTitle.textContent ?? '',
        fields: spec0Fields(),
        lines: detailLines(),
        state: detail.state,
      },
    }),
    mock: () => {
      mocked = true;
      mockData = mockRows();
      detail = { open: false, row: null, data: null, state: 'idle', error: '' };
      overview = {
        nodeKey: options.nodeKey,
        state: 'live',
        note: '演示数据',
        updatedAt: Date.now(),
        snapshot: mockSnapshot(),
      };
      if (!activeKey) {
        activeKey = tabItems().find((item) => item.enabled)?.key ?? options.pages[0]?.key ?? null;
      }
      listState = 'ready';
      options.onTone?.(toneOf(overview));
      paintTabs();
      paintOverview();
      applyMockPage();
    },
  };

  /** 探针里数详情字段/分录，不把整行数据抛给 e2e（体积大且含业务字段） */
  function spec0Fields(): number {
    const spec = activeSpec();
    return detail.open && detail.data && spec?.detailFields ? spec.detailFields.length : 0;
  }

  function detailLines(): number {
    const spec = activeSpec();
    if (!detail.open || !detail.data || !spec) return 0;
    const lines = detail.data[spec.lineSource ?? 'lines'];
    return Array.isArray(lines) ? lines.length : 0;
  }

  return handle;
}
