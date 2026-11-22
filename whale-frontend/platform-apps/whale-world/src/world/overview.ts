/**
 * 概览客户端（S4 契约的前端落地）。
 *
 * <p>与 `metrics.ts` 的分工：牌坊那套是"一殿一个异构接口 + 前端猜字段"，
 * 是 S3 的历史做法；这里接的是契约化的 `/<服务>/overview` —— 字段、单位、
 * 百分比口径都由服务端定好，前端只做"格式化 + 画"。</p>
 *
 * <p>三条与 metrics.ts 一致的硬约束（都是踩过的坑）：</p>
 * <ol>
 *   <li><b>没权限不发请求</b>：已知缺权限码直接判 denied，别把网关日志刷穿。</li>
 *   <li><b>失败不拖累场景</b>：每路独立 AbortController + 独立超时。</li>
 *   <li><b>失败沿用旧值</b>：本次失败但手里有上次成功的快照时标 stale 并写明时间；
 *       服务端也会返回 stale（它自己降级），两边都不许把旧数伪装成实时。</li>
 * </ol>
 */

import type { HallData } from './datahall';
import type { GlyphMetric, GlyphState, GlyphTone } from './metrics';
import type { OverviewFormat, OverviewKpi, OverviewSnapshot } from './overview.types';

/* ---------------------------------------------------------------- 格式化 */

const num = (value: unknown): number => {
  const n = typeof value === 'string' ? Number(value) : (value as number);
  return Number.isFinite(n) ? n : 0;
};

const timeLabel = (ms: number): string => {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** 千分位 + 万/亿折算 */
const formatCount = (value: number): string => {
  const n = Number(value);
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`;
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(1)}万`;
  return n.toLocaleString('zh-CN');
};

/** 金额：￥ + 万/亿折算 */
const formatMoney = (value: number): string => {
  const n = Number(value);
  const abs = Math.abs(n);
  if (abs >= 1e8) return `¥${(n / 1e8).toFixed(2)}亿`;
  if (abs >= 1e4) return `¥${(n / 1e4).toFixed(1)}万`;
  return `¥${Math.round(n).toLocaleString('zh-CN')}`;
};

/**
 * 百分比：契约规定服务端一律传比率（0.82 = 82%）。
 *
 * 这里不再做 "小于 1.5 就当比率" 的猜测——S3 那套兜底在真实的 0.8% 上会显示成 80%，
 * 契约化之后猜就是错，宁可显示 0.0%。
 */
const formatPercent = (ratio: number): string => `${(Number(ratio) * 100).toFixed(1)}%`;

/** 时长：秒 → 可读文本 */
const formatDuration = (seconds: number): string => {
  const s = Math.max(0, Math.round(Number(seconds)));
  if (s >= 86400) return `${(s / 86400).toFixed(1)}天`;
  if (s >= 3600) return `${(s / 3600).toFixed(1)}小时`;
  if (s >= 60) return `${Math.round(s / 60)}分钟`;
  return `${s}秒`;
};

/** 按契约的 format 排版一个裸数值（屏风页的表格与图表也用它，保证全城一套口径） */
export function formatValue(format: OverviewFormat, value: number): string {
  const n = num(value);
  switch (format) {
    case 'money':
      return formatMoney(n);
    case 'percent':
      return formatPercent(n);
    case 'duration':
      return formatDuration(n);
    case 'text':
      return String(n);
    case 'count':
    default:
      return formatCount(n);
  }
}

/** 按契约的 format 排版一个数值 */
export function formatKpiValue(kpi: OverviewKpi): string {
  const value = num(kpi.value);
  const text = formatValue(kpi.format, value);
  // 只有计数类追加单位（「件」「人」）；money/percent 已自带符号，再拼会出现 ￥12万 元
  return kpi.format === 'count' ? `${text}${kpi.unit ?? ''}` : text;
}

/* ---------------------------------------------------------------- 映射 */

const TONE_ORDER: Record<GlyphTone, number> = { flat: 0, good: 1, warn: 2, bad: 3 };

/** 快照状态 → 牌/厅的状态（三态之外的两种是"根本拿不到"） */
export function toGlyphState(snapshot: OverviewSnapshot | null, denied: boolean): GlyphState {
  if (denied) return 'denied';
  if (!snapshot) return 'unreachable';
  if (snapshot.state === 'live') return 'live';
  if (snapshot.state === 'stale') return 'stale';
  return 'unreachable';
}

/** 指标语气取最"严重"的一档，没有指标时随快照状态 */
function hallTone(metrics: GlyphMetric[], state: GlyphState): GlyphTone {
  let tone: GlyphTone = 'flat';
  for (const metric of metrics) {
    const next = metric.tone ?? 'flat';
    if (TONE_ORDER[next] > TONE_ORDER[tone]) tone = next;
  }
  if (metrics.length === 0) {
    if (state === 'unreachable') return 'bad';
    if (state === 'stale' || state === 'denied') return 'warn';
  }
  return tone;
}

/** 快照 → 牌坊/厅上的若干行指标 */
export function toGlyphMetrics(snapshot: OverviewSnapshot | null): GlyphMetric[] {
  if (!snapshot) return [];
  return (snapshot.kpis ?? []).map((kpi) => ({
    label: kpi.label,
    value: formatKpiValue(kpi),
    tone: kpi.tone ?? 'flat',
  }));
}

/**
 * 快照 → 数据厅的一厅数据。
 *
 * 名字（name）仍取城里的殿名，副标题（sub）用服务端给的口径与期间——
 * 让服务端决定"营收算的是哪个月"，是这套契约的主要收益之一。
 */
export function toHallData(
  result: OverviewResult,
  fallbackName: string,
  fallbackSub: string,
): HallData {
  const snapshot = result.snapshot;
  const metrics = toGlyphMetrics(snapshot);
  const state = result.state;
  return {
    nodeKey: result.nodeKey,
    name: fallbackName,
    sub: snapshot?.subtitle ?? fallbackSub,
    state,
    tone: hallTone(metrics, state),
    metrics,
    note: result.note,
    updatedAt: result.updatedAt,
  };
}

/* ---------------------------------------------------------------- 请求 */

/** 一个节点的取数结果 */
export interface OverviewResult {
  nodeKey: string;
  /** 牌/厅状态：live / stale / denied / unreachable */
  state: GlyphState;
  /** 牌底说明：「刷新于 10:24」「陈旧 · 上次 09:50」「无此殿凭据」 */
  note: string;
  /** 数据时刻（毫秒）；0 表示从未成功 */
  updatedAt: number;
  snapshot: OverviewSnapshot | null;
}

/** 取数选项 */
export interface OverviewFetchOptions {
  /** 取访问令牌；返回 null 视为未登录 */
  getToken: () => string | null;
  /** 取当前用户权限码；返回 null 表示未知（不预判，交给接口 403） */
  getPermissions?: () => string[] | null;
  /** 单路超时，默认 6 秒（比 metrics 的 8s 短：概览有服务端 TTL 缓存，慢就是不正常） */
  timeoutMillis?: number;
}

/** 一个概览节点的接入配置（"厅配置"就是这份数组） */
export interface OverviewHallSpec {
  /** 城内节点 key，与 city-layout.json 对齐 */
  nodeKey: string;
  /** 网关路径，例如 /erp/overview */
  path: string;
  /** 接口要求的权限码；缺它就不发请求 */
  permission?: string;
}

/** 缺权限且能确定时返回 true（未注入权限时留一次机会，真 403 再降级） */
function lacksPermission(permission: string | undefined, options: OverviewFetchOptions): boolean {
  if (!permission) return false;
  const perms = options.getPermissions?.();
  if (!perms || perms.length === 0) return false;
  return !perms.includes(permission);
}

/**
 * 取一个节点的概览。
 *
 * 永不抛异常：所有失败都折成 `OverviewResult`（denied / unreachable + 说明），
 * 让调用方不必为"取数失败"写第二套分支。
 */
export async function fetchOverview(
  spec: OverviewHallSpec,
  options: OverviewFetchOptions,
  previous: OverviewResult | null,
): Promise<OverviewResult> {
  const timeoutMillis = options.timeoutMillis ?? 6_000;
  const token = options.getToken();

  if (!token) {
    return { nodeKey: spec.nodeKey, state: 'denied', note: '未登录', updatedAt: 0, snapshot: null };
  }
  if (lacksPermission(spec.permission, options)) {
    return {
      nodeKey: spec.nodeKey,
      state: 'denied',
      note: '无此殿凭据',
      updatedAt: 0,
      snapshot: null,
    };
  }

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMillis);
  try {
    const response = await fetch(spec.path, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      credentials: 'omit',
      signal: controller.signal,
    });

    if (response.status === 401 || response.status === 403) {
      return {
        nodeKey: spec.nodeKey,
        state: 'denied',
        note: response.status === 401 ? '登录已失效' : '无此殿凭据',
        updatedAt: 0,
        snapshot: null,
      };
    }
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('json')) {
      throw new Error('非 JSON 响应（多半是代理未配）');
    }

    const body: unknown = await response.json();
    const wrapped = body && typeof body === 'object' && 'code' in (body as Record<string, unknown>);
    const code = wrapped ? num((body as Record<string, unknown>).code) : 0;
    if (wrapped && code !== 0) {
      throw new Error(String((body as Record<string, unknown>).message ?? '业务拒绝'));
    }

    const snapshot = (wrapped ? (body as Record<string, unknown>).data : body) as OverviewSnapshot;
    const now = Date.now();
    const state = toGlyphState(snapshot, false);
    const note =
      state === 'live'
        ? `刷新于 ${timeLabel(now)}`
        : `陈旧 · 上次 ${timeLabel(now)}${snapshot?.notes?.length ? ` · ${snapshot.notes[0]}` : ''}`;
    return { nodeKey: spec.nodeKey, state, note, updatedAt: now, snapshot };
  } catch (error) {
    const aborted = controller.signal.aborted;
    const reason = aborted
      ? '问询超时'
      : (error instanceof Error && error.message ? error.message : '未接通').slice(0, 24);
    if (previous && previous.updatedAt > 0) {
      return {
        ...previous,
        state: 'stale',
        note: `${reason} · 上次 ${timeLabel(previous.updatedAt)}`,
      };
    }
    return {
      nodeKey: spec.nodeKey,
      state: 'unreachable',
      note: reason,
      updatedAt: 0,
      snapshot: null,
    };
  } finally {
    window.clearTimeout(timer);
  }
}

/* ---------------------------------------------------------------- 采集器 */

/** 采集器选项 */
export interface OverviewFeedOptions extends OverviewFetchOptions {
  /** 厅配置：哪些节点走契约接口 */
  halls: OverviewHallSpec[];
  /** 任一节点结果变化后回调（传的是内部原地更新的 Map） */
  onChange: (results: Map<string, OverviewResult>) => void;
  /** 自动刷新间隔，默认 30 秒（须 ≥ 服务端 TTL） */
  intervalMillis?: number;
}

/** 采集器句柄 */
export interface OverviewFeedHandle {
  readonly results: Map<string, OverviewResult>;
  refresh: () => Promise<void>;
  start: () => void;
  stop: () => void;
  isRunning: () => boolean;
  dispose: () => void;
}

/**
 * 创建概览采集器：把一组厅配置变成可渲染的数据源。
 *
 * 与 `metrics` 的两套定时器不并存：进厅时启动本采集器、城池采集停掉；
 * 出厅反向。否则同一批数字会被刷两遍（8 路 + N 路）。
 */
export function createOverviewFeed(options: OverviewFeedOptions): OverviewFeedHandle {
  const intervalMillis = options.intervalMillis ?? 30_000;
  const results = new Map<string, OverviewResult>();
  for (const spec of options.halls) {
    results.set(spec.nodeKey, {
      nodeKey: spec.nodeKey,
      state: 'loading',
      note: '问询中',
      updatedAt: 0,
      snapshot: null,
    });
  }

  let inflight: Promise<void> | null = null;
  let timer: number | null = null;
  let disposed = false;

  const emit = (): void => {
    options.onChange(results);
  };

  async function run(): Promise<void> {
    if (disposed) return;
    const tasks = options.halls.map(async (spec) => {
      const previous = results.get(spec.nodeKey) ?? null;
      const next = await fetchOverview(spec, options, previous);
      results.set(spec.nodeKey, next);
    });
    await Promise.allSettled(tasks);
    if (!disposed) emit();
  }

  const handle: OverviewFeedHandle = {
    get results() {
      return results;
    },
    refresh() {
      if (!inflight) {
        inflight = run().finally(() => {
          inflight = null;
        });
      }
      return inflight;
    },
    start() {
      if (timer !== null || disposed) return;
      void handle.refresh();
      timer = window.setInterval(() => {
        if (document.visibilityState !== 'visible') return;
        void handle.refresh();
      }, intervalMillis);
    },
    stop() {
      if (timer !== null) {
        window.clearInterval(timer);
        timer = null;
      }
    },
    isRunning() {
      return timer !== null;
    },
    dispose() {
      disposed = true;
      handle.stop();
      results.clear();
    },
  };
  return handle;
}
