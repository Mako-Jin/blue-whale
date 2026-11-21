/**
 * 城情采集 —— 殿前牌坊的数据来源。
 *
 * <p>八座殿各挂一块牌，牌上写该殿所辖业务系统的一两个真实数字。数据不是另造的：
 * 全部取自各业务服务**已有的只读概览接口**（本轮没有为 3D 城新增任何后端接口），
 * 因此 2D 侧行为不变、也不必为 3D 单独维护一套指标口径。</p>
 *
 * <p><b>为什么是前端直采 8 个接口，而不是在后端加一个聚合接口：</b>
 * 3D 城与 2D 是隔离的域（见 docs 里的隔离约定），加聚合接口意味着要动某个后端服务、
 * 还要在服务端做跨服务调用与权限头透传（服务间直连不会自动带 {@code X-Permission-Codes}，
 * 漏了就全 403）。而"某座殿取不到数"本来就是常态——服务没起、模块开关关着、
 * 这个用户没有该系统的权限，任何一种都会发生。前端并发 + 逐殿独立降级既能覆盖这些情况，
 * 又完全不侵入后端。</p>
 *
 * <p><b>三条硬约束：</b></p>
 * <ol>
 *   <li><b>没权限就不发请求。</b>已知缺 {@code perm_code} 时直接判 {@code denied}，
 *       否则每次刷新都要在每个殿上吃一次 403，把网关与业务服务的日志刷穿。</li>
 *   <li><b>取不到数不能拖累场景。</b>每殿独立 {@code AbortController} + 独立超时，
 *       任一路失败只影响自己那块牌；绝不 {@code Promise.all} 一起 reject。</li>
 *   <li><b>失败要沿用旧值。</b>上次成功的结果写进 localStorage，本次失败标 {@code stale}
 *       并显示"上次刷新于何时"——牌上写着昨天的数字却不说破，比空着更坏。</li>
 * </ol>
 */

import type { PalaceSpec } from './layout';

/** 单个数值的语气：决定牌上那行字的颜色 */
export type GlyphTone = 'good' | 'warn' | 'bad' | 'flat';

/** 一块牌的整体状态 */
export type GlyphState =
  /** 尚未开始 */
  | 'idle'
  /** 首次请求中，还没有任何值 */
  | 'loading'
  /** 本次刷新成功，数字是最新的 */
  | 'live'
  /** 本次失败，显示的是上次成功的值 */
  | 'stale'
  /** 当前用户没有该系统的权限（未发请求） */
  | 'denied'
  /** 接口不可达 / 业务拒绝（服务未起、模块开关未开等） */
  | 'unreachable';

/** 牌上的一行指标 */
export interface GlyphMetric {
  label: string;
  value: string;
  tone?: GlyphTone;
}

/** 一块牌要渲染的全部内容 */
export interface GlyphCard {
  nodeKey: string;
  state: GlyphState;
  metrics: GlyphMetric[];
  /** 状态说明，渲染在牌底：「刷新于 10:24」「无此殿凭据」「未接通」 */
  note: string;
  /** 数据对应的时刻（毫秒）；0 表示从未成功过 */
  updatedAt: number;
}

/** 一路采集的定义 */
interface ProbeSpec {
  nodeKey: string;
  /** 该接口要求的权限码；缺它就不发请求 */
  permission: string;
  /** 相对网关的路径（网关不剥前缀，类上的 @RequestMapping 就是完整路径） */
  url: string;
  /** 从响应 data 里挤出牌面上的数字；字段缺失时自己要能兜住，不要抛 */
  extract: (data: unknown) => GlyphMetric[];
}

/* ---------------------------------------------------------------- 格式化 */

const num = (value: unknown): number => {
  const n = typeof value === 'string' ? Number(value) : (value as number);
  return Number.isFinite(n) ? n : 0;
};

const formatCount = (value: unknown): string => {
  const n = num(value);
  if (Math.abs(n) >= 1e8) {
    return `${(n / 1e8).toFixed(2)}亿`;
  }
  if (Math.abs(n) >= 1e4) {
    return `${(n / 1e4).toFixed(1)}万`;
  }
  return n.toLocaleString('zh-CN');
};

const formatMoney = (value: unknown): string => {
  const n = num(value);
  const abs = Math.abs(n);
  if (abs >= 1e8) {
    return `¥${(n / 1e8).toFixed(2)}亿`;
  }
  if (abs >= 1e4) {
    return `¥${(n / 1e4).toFixed(1)}万`;
  }
  return `¥${Math.round(n).toLocaleString('zh-CN')}`;
};

/**
 * 百分比。
 *
 * <p>后端各服务的口径没统一：有的返回 {@code 0.82}（比率），有的返回 {@code 82}（已乘 100）。
 * 小于 1.5 一律按比率处理——业务上不会出现「0.8% 的达成率」这种需要保留的精度，
 * 而把 82 当成 8200% 会让牌上出现荒谬的数字，两害相权取此。</p>
 */
const formatPercent = (value: unknown): string => {
  const raw = value === null || value === undefined ? Number.NaN : Number(value);
  if (!Number.isFinite(raw)) {
    return '—';
  }
  const p = Math.abs(raw) <= 1.5 ? raw * 100 : raw;
  return `${p.toFixed(1)}%`;
};

/** 安全取嵌套字段：`dig(d, 'production.exceptionCount')` */
function dig(source: unknown, path: string): unknown {
  let cur: unknown = source;
  for (const key of path.split('.')) {
    if (cur === null || typeof cur !== 'object') {
      return undefined;
    }
    cur = (cur as Record<string, unknown>)[key];
  }
  return cur;
}

/** 列表类响应取总条数：兼容本项目自研 PageResult 与 Spring Page 两种形态 */
const totalOf = (data: unknown): unknown =>
  dig(data, 'total') ??
  dig(data, 'totalElements') ??
  dig(data, 'records.length') ??
  dig(data, 'content.length');

/* ---------------------------------------------------------------- 八路采集 */
/*
 * 只有 7 个系统有现成的概览接口。兵部（IoT）与翰林院（DOC）没有统计接口，
 * 因此走同域最近的只读接口，并在牌下的说明里写清楚取的是什么——
 * 与其编一个不存在的"设备在线数"，不如显示告警中心未处理数与藏书阁数。
 */
const PROBES: ProbeSpec[] = [
  {
    // 户部 = ERP：经营驾驶舱，一次给齐营收/订单/预警
    nodeKey: 'hubu-hall',
    permission: 'sys:erp:bi:cockpit:list',
    url: '/erp/bi/cockpit',
    extract: (d) => [
      { label: '营收', value: formatMoney(dig(d, 'revenueAmount')) },
      { label: '订单', value: formatCount(dig(d, 'orderCount')) },
      {
        label: '预警',
        value: formatCount(dig(d, 'alertCount')),
        tone: num(dig(d, 'alertCount')) > 0 ? 'warn' : 'good',
      },
    ],
  },
  {
    // 工部 = MES：生产达成率与故障设备，是"工部出没出事"最直接的两个数
    nodeKey: 'gongbu-hall',
    permission: 'sys:mes:dashboard:view',
    url: '/mes/dashboard',
    extract: (d) => [
      { label: '达成率', value: formatPercent(dig(d, 'production.achievementRate')) },
      {
        label: '故障设备',
        value: formatCount(dig(d, 'equipment.faultEquipmentCount')),
        tone: num(dig(d, 'equipment.faultEquipmentCount')) > 0 ? 'bad' : 'good',
      },
      {
        label: '生产例外',
        value: formatCount(dig(d, 'production.exceptionCount')),
        tone: num(dig(d, 'production.exceptionCount')) > 0 ? 'warn' : 'good',
      },
    ],
  },
  {
    // 吏部 = OA：待办与通告，是"我"在协同系统里的欠账
    nodeKey: 'libu-hall',
    permission: 'sys:oa:workbench:view',
    url: '/oa/workbench/summary',
    extract: (d) => [
      {
        label: '待我审批',
        value: formatCount(dig(d, 'pendingApprovalCount')),
        tone: num(dig(d, 'pendingApprovalCount')) > 0 ? 'warn' : 'good',
      },
      { label: '未读通告', value: formatCount(dig(d, 'unreadNoticeCount')) },
      {
        label: '今日会议',
        value: formatCount((dig(d, 'todayMeetings') as unknown[] | undefined)?.length ?? 0),
      },
    ],
  },
  {
    // 礼部 = CRM：新客、目标达成、风险商机
    nodeKey: 'libu-ritual-hall',
    permission: 'sys:crm:analytics:dashboard',
    url: '/crm/analytics/dashboard',
    extract: (d) => [
      { label: '今日新客', value: formatCount(dig(d, 'todayNewLeads')) },
      { label: '目标达成', value: formatPercent(dig(d, 'targetCompletionRate')) },
      {
        label: '风险商机',
        value: formatCount(dig(d, 'riskOpportunityCount')),
        tone: num(dig(d, 'riskOpportunityCount')) > 0 ? 'warn' : 'good',
      },
    ],
  },
  {
    // 兵部 = IoT：本服务没有聚合接口，取城防语义最近的「告警中心未处理数」
    nodeKey: 'bingbu-hall',
    permission: 'sys:alert:record:list',
    url: '/alert/v1/record/page?status=FIRING&pageSize=1',
    extract: (d) => [
      {
        label: '未处理告警',
        value: formatCount(totalOf(d)),
        tone: num(totalOf(d)) > 0 ? 'bad' : 'good',
      },
    ],
  },
  {
    // 翰林院 = DOC：同样没有统计接口，取「我参与的藏书阁」数量
    nodeKey: 'hanlin-academy',
    permission: 'sys:doc:space:list',
    url: '/doc/space/my',
    extract: (d) => [
      { label: '藏书阁', value: formatCount((d as unknown[] | undefined)?.length ?? 0) },
    ],
  },
  {
    // 杏坛 = LMS：学员覆盖与学习完成率
    nodeKey: 'xingtan-hall',
    permission: 'sys:lms:analytics:view',
    url: '/lms/analytics/overview',
    extract: (d) => [
      { label: '在册学员', value: formatCount(dig(d, 'learnerTotal') ?? dig(d, 'memberTotal')) },
      { label: '学习完成', value: formatPercent(dig(d, 'completionRate')) },
    ],
  },
  {
    // 市舶司 = MALL：日报是列表，取当天那一行
    nodeKey: 'shibosi-hall',
    permission: 'sys:mall:order:list',
    url: '/mall/report/daily',
    extract: (d) => {
      const rows = Array.isArray(d) ? (d as Array<Record<string, unknown>>) : [];
      const last = rows.length > 0 ? rows[rows.length - 1] : undefined;
      return [
        { label: '今日订单', value: formatCount(last?.orderCount) },
        { label: '成交额', value: formatMoney(last?.paidAmount) },
      ];
    },
  },
];

/* ---------------------------------------------------------------- 持久化 */

const STORAGE_KEY = 'bww.glyphs.v1';

interface CachedCard {
  metrics: GlyphMetric[];
  updatedAt: number;
}

/** 读上次成功的快照；localStorage 不可用（隐私模式）时静默返回空 */
function loadCache(): Record<string, CachedCard> {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {};
    }
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, CachedCard>) : {};
  } catch {
    return {};
  }
}

function saveCache(cards: Map<string, GlyphCard>): void {
  try {
    const payload: Record<string, CachedCard> = {};
    for (const card of cards.values()) {
      if (card.updatedAt > 0 && card.metrics.length > 0) {
        payload[card.nodeKey] = { metrics: card.metrics, updatedAt: card.updatedAt };
      }
    }
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // 隐私模式/配额满：不影响本次会话内的显示
  }
}

/* ---------------------------------------------------------------- 采集器 */

export interface WorldMetricsOptions {
  /** 取访问令牌；缺省视为未登录，全部判 denied */
  getToken?: () => string | null;
  /** 取当前用户权限码；返回 null 表示未知（此时不预判，交给接口自己 403） */
  getPermissions?: () => string[] | null;
  /** 牌面更新回调（每次任一路刷新完都会调一次） */
  onChange?: (cards: Map<string, GlyphCard>) => void;
  /** 自动刷新间隔，默认 60 秒 */
  intervalMillis?: number;
  /** 单路超时，默认 8 秒 */
  timeoutMillis?: number;
}

export interface WorldMetricsHandle {
  /** 当前八块牌的内容（只读视图） */
  readonly cards: Map<string, GlyphCard>;
  /** 立即刷新一遍；并发调用会复用同一次在途刷新 */
  refresh: () => Promise<void>;
  /** 开始定时刷新 */
  start: () => void;
  stop: () => void;
  /** 定时器是否在跑（e2e 断言"离厅即停"用） */
  isRunning: () => boolean;
  dispose: () => void;
}

const timeLabel = (ms: number): string => {
  const d = new Date(ms);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
};

/**
 * 创建城情采集器。
 *
 * @param palaces 宫殿列表，用于给每块牌一个初始的 loading 态（牌位与殿一一对应）
 * @param options 令牌、权限、刷新间隔
 */
export function createWorldMetrics(
  palaces: PalaceSpec[],
  options: WorldMetricsOptions = {},
): WorldMetricsHandle {
  const intervalMillis = options.intervalMillis ?? 60_000;
  const timeoutMillis = options.timeoutMillis ?? 8_000;

  const cached = loadCache();
  const cards = new Map<string, GlyphCard>();

  // 初始化：有缓存就先用缓存（stale），没有就 loading —— 牌子先立起来，数字随后补
  for (const probe of PROBES) {
    if (!palaces.some((p) => p.nodeKey === probe.nodeKey)) {
      continue;
    }
    const hit = cached[probe.nodeKey];
    cards.set(
      probe.nodeKey,
      hit
        ? {
            nodeKey: probe.nodeKey,
            state: 'stale',
            metrics: hit.metrics,
            note: `上次 ${timeLabel(hit.updatedAt)}`,
            updatedAt: hit.updatedAt,
          }
        : { nodeKey: probe.nodeKey, state: 'loading', metrics: [], note: '问询中', updatedAt: 0 },
    );
  }

  let inflight: Promise<void> | null = null;
  let timer: number | null = null;
  let disposed = false;

  /**
   * 切回标签页时补一次刷新。
   *
   * <p>只靠定时器的话，用户切走十分钟再切回来，牌上还是十分钟前的数字，
   * 而 {@code interval} 只在"页面可见"时才走，等于白等一个周期。</p>
   */
  const onVisibility = (): void => {
    if (document.visibilityState === 'visible' && timer !== null) {
      void handle.refresh();
    }
  };

  const emit = (): void => {
    // 每次都传同一张 Map（内部原地更新），省掉订阅方做深度比较
    options.onChange?.(cards);
  };

  /** 缺权限且能确定时返回 true：此时不发请求 */
  const lacksPermission = (permission: string): boolean => {
    const perms = options.getPermissions?.();
    // 未知（未注入 / 空数组）时不预判：留一次机会，真 403 再降级
    if (!perms || perms.length === 0) {
      return false;
    }
    return !perms.includes(permission);
  };

  async function fetchOne(probe: ProbeSpec, token: string): Promise<void> {
    const controller = new AbortController();
    const timerId = window.setTimeout(() => controller.abort(), timeoutMillis);
    try {
      const response = await fetch(probe.url, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
        // 不种 cookie：这些是只读统计，带上凭证只会让 401 的成因更难判断
        credentials: 'omit',
        signal: controller.signal,
      });

      if (response.status === 401 || response.status === 403) {
        cards.set(probe.nodeKey, {
          nodeKey: probe.nodeKey,
          state: 'denied',
          metrics: [],
          note: response.status === 401 ? '登录已失效' : '无此殿凭据',
          updatedAt: 0,
        });
        return;
      }

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      // 显式挡掉非 JSON：dev/preview 代理没配时请求会落到 SPA 回退，
      // 拿到的是 index.html 且状态码 200 —— 不识别就会变成"解析异常"这种含糊结论
      const contentType = response.headers.get('content-type') ?? '';
      if (!contentType.includes('json')) {
        throw new Error('非 JSON 响应（多半是代理未配，落到 SPA 回退）');
      }

      const body: unknown = await response.json();
      // 统一响应体 { code, message, data }；个别老接口直接返数组，两种都要认
      const wrapped =
        body && typeof body === 'object' && 'code' in (body as Record<string, unknown>);
      const code = wrapped ? num((body as Record<string, unknown>).code) : 0;
      if (wrapped && code !== 0) {
        const message = String((body as Record<string, unknown>).message ?? '业务拒绝');
        cards.set(probe.nodeKey, {
          nodeKey: probe.nodeKey,
          state: 'unreachable',
          metrics: [],
          note: message.slice(0, 18),
          updatedAt: 0,
        });
        return;
      }

      const data = wrapped ? (body as Record<string, unknown>).data : body;
      const metrics = probe.extract(data);
      const now = Date.now();
      cards.set(probe.nodeKey, {
        nodeKey: probe.nodeKey,
        state: 'live',
        metrics,
        note: `刷新于 ${timeLabel(now)}`,
        updatedAt: now,
      });
    } catch (error) {
      // 失败沿用旧值：有旧值标 stale，没旧值标 unreachable。
      // 把具体原因写进牌面说明——"未接通"三个字对排查毫无帮助，
      // 而 HTTP 502 / 非 JSON 响应 / 超时 三者要查的地方完全不同。
      const prev = cards.get(probe.nodeKey);
      const aborted = controller.signal.aborted;
      const reason = aborted
        ? '问询超时'
        : (error instanceof Error && error.message ? error.message : '未接通').slice(0, 16);
      if (prev && prev.updatedAt > 0) {
        cards.set(probe.nodeKey, {
          ...prev,
          state: 'stale',
          note: `${reason} · 上次 ${timeLabel(prev.updatedAt)}`,
        });
      } else {
        cards.set(probe.nodeKey, {
          nodeKey: probe.nodeKey,
          state: 'unreachable',
          metrics: [],
          note: reason,
          updatedAt: 0,
        });
      }
      // 这里不 rethrow：一路失败不该中断其余七路
    } finally {
      window.clearTimeout(timerId);
    }
  }

  async function run(): Promise<void> {
    if (disposed) {
      return;
    }
    const token = options.getToken?.() ?? null;

    const todo = PROBES.filter((p) => cards.has(p.nodeKey));

    if (!token) {
      for (const probe of todo) {
        cards.set(probe.nodeKey, {
          nodeKey: probe.nodeKey,
          state: 'denied',
          metrics: [],
          note: '未登录',
          updatedAt: 0,
        });
      }
      emit();
      return;
    }

    // 没权限的直接定案，不发请求
    const allowed: ProbeSpec[] = [];
    for (const probe of todo) {
      if (lacksPermission(probe.permission)) {
        cards.set(probe.nodeKey, {
          nodeKey: probe.nodeKey,
          state: 'denied',
          metrics: [],
          note: '无此殿凭据',
          updatedAt: 0,
        });
      } else {
        allowed.push(probe);
      }
    }
    emit();

    // 并发发出，但每路各自结算；allSettled 保证某一路抛错不影响其它路
    await Promise.allSettled(allowed.map((probe) => fetchOne(probe, token)));

    if (disposed) {
      return;
    }
    saveCache(cards);
    emit();
  }

  const handle: WorldMetricsHandle = {
    get cards() {
      return cards;
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
      if (timer !== null || disposed) {
        return;
      }
      void handle.refresh();
      document.addEventListener('visibilitychange', onVisibility);
      timer = window.setInterval(() => {
        // 页面不可见时不刷：牌坊上的数字没人看，白耗 8 个服务的连接
        if (document.visibilityState !== 'visible') {
          return;
        }
        void handle.refresh();
      }, intervalMillis);
    },
    stop() {
      if (timer !== null) {
        window.clearInterval(timer);
        timer = null;
      }
      document.removeEventListener('visibilitychange', onVisibility);
    },
    isRunning() {
      return timer !== null;
    },
    dispose() {
      disposed = true;
      handle.stop();
      cards.clear();
    },
  };
  return handle;
}
