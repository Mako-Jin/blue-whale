/**
 * 自动生成，请勿手改。
 *
 * 来源：docs/specs/overview-contract/overview.schema.json
 * 重新生成：pnpm --filter whale-world gen:overview
 * 校验同步：pnpm --filter whale-world check:overview
 */
/** live 实时成功 / stale 沿用上次成功 / degraded 不可用 */
export type OverviewState = 'live' | 'stale' | 'degraded';

/** 只表达排版口径；percent 一律传比率（0.82） */
export type OverviewFormat = 'count' | 'money' | 'percent' | 'duration' | 'text';

export type OverviewTone = 'good' | 'warn' | 'bad' | 'flat';

export interface OverviewKpi {
  key: string;
  label: string;
  /** 百分比传比率；文本类也必填（用于比对） */
  value: number;
  format: OverviewFormat;
  unit?: string;
  tone?: OverviewTone;
  /** 环比比率，+12% 记 0.12 */
  deltaPercent?: number;
  /** 口径说明，≤20 字 */
  hint?: string;
}

export interface OverviewSeries {
  name: string;
  values: number[];
}

export interface OverviewChart {
  key: string;
  title: string;
  type: 'line' | 'bar' | 'donut';
  format: OverviewFormat;
  /** 长度必须等于每个 series.values 长度，缺数补 0 */
  categories: string[];
  series: OverviewSeries[];
}

export interface OverviewAlert {
  level: 'info' | 'warning' | 'critical';
  text: string;
  count?: number;
  /** 首现时刻 ISO-8601 */
  since?: string;
}

export interface OverviewLink {
  key: string;
  label: string;
  path: string;
  permission?: string;
}

export interface OverviewSnapshot {
  /** 服务一级前缀，与网关路由一致（erp / mes / oa …） */
  service: string;
  /** 城内节点 key，与 city-layout.json 对齐 */
  nodeKey: string;
  /** 厅/牌主标题 */
  title: string;
  /** 口径与统计期间，避免前端拼接 */
  subtitle?: string;
  /** 生成时刻，ISO-8601 带时区 */
  generatedAt: string;
  state: OverviewState;
  kpis: OverviewKpi[];
  charts?: OverviewChart[];
  alerts?: OverviewAlert[];
  links?: OverviewLink[];
  notes?: string[];
}
