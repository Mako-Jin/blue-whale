import type { LogLevel } from 'common-tools';

/**
 * 级别顺序与展示元数据。
 *
 * <p>顺序取自 `common-tools` 的 `LogLevel` 联合类型 —— 这里**不重新定义级别集合**，
 * 只用 `Record<LogLevel, ...>` 强制"每个级别都必须有元数据"。
 * 将来日志包新增级别，这里会直接编译失败，而不是静默少显示一个级别。
 */
export const LEVEL_ORDER: readonly LogLevel[] = [
  'trace',
  'debug',
  'perf',
  'info',
  'success',
  'warn',
  'error',
];

export interface LevelMeta {
  /** 短标签，列表里按等宽字体对齐 */
  label: string;
  /** CSS 类名后缀，样式定义在 App.less */
  className: string;
  /** 中文说明，给筛选下拉与图例用 */
  description: string;
}

export const LEVEL_META: Record<LogLevel, LevelMeta> = {
  trace: { label: 'TRACE', className: 'lv-trace', description: '最细粒度的过程' },
  debug: { label: 'DEBUG', className: 'lv-debug', description: '开发期调试信息' },
  perf: { label: 'PERF', className: 'lv-perf', description: '性能数据点' },
  info: { label: 'INFO', className: 'lv-info', description: '正常业务节点' },
  success: { label: 'OK', className: 'lv-success', description: '操作成功' },
  warn: { label: 'WARN', className: 'lv-warn', description: '可恢复的异常' },
  error: { label: 'ERROR', className: 'lv-error', description: '需要人介入的故障' },
};

/** 级别 → 展示标签；未知级别原样返回，避免把服务端的新值显示成空白 */
export function levelLabel(level: string | undefined): string {
  const meta = LEVEL_META[level as LogLevel];
  return meta ? meta.label : (level ?? '—');
}

/** 级别 → CSS 类名 */
export function levelClassName(level: string | undefined): string {
  const meta = LEVEL_META[level as LogLevel];
  return meta ? meta.className : 'lv-unknown';
}
