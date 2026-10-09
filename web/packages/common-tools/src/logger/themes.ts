import { LOG_LEVELS } from './levels';
import type { Theme, ThemeColors, ThemeInput, ThemePalette } from './types';

/** 深色控制台主题（默认，适配 Chrome / Edge 默认深色 DevTools）。 */
export const DEFAULT_THEME: Theme = {
  colors: {
    trace: { fg: '#cbd5e1', bg: '#1e293b', border: '#64748b' },
    debug: { fg: '#c4b5fd', bg: '#2e1065', border: '#8b5cf6' },
    perf: { fg: '#67e8f9', bg: '#164e63', border: '#06b6d4' },
    info: { fg: '#7dd3fc', bg: '#0c4a6e', border: '#0ea5e9' },
    success: { fg: '#86efac', bg: '#14532d', border: '#22c55e' },
    warn: { fg: '#fde047', bg: '#713f12', border: '#eab308' },
    error: { fg: '#fca5a5', bg: '#7f1d1d', border: '#ef4444' },
  },
  palette: {
    timestamp: '#64748b',
    namespace: '#cbd5e1',
    message: '#e2e8f0',
    muted: '#94a3b8',
    accent: '#38bdf8',
    link: '#60a5fa',
    divider: '#334155',
  },
};

/** 高对比主题：适合浅色控制台或投影演示。 */
export const CONTRAST_THEME: Theme = {
  colors: {
    trace: { fg: '#ffffff', bg: '#475569', border: '#334155' },
    debug: { fg: '#ffffff', bg: '#6d28d9', border: '#5b21b6' },
    perf: { fg: '#ffffff', bg: '#0e7490', border: '#155e75' },
    info: { fg: '#ffffff', bg: '#2563eb', border: '#1d4ed8' },
    success: { fg: '#ffffff', bg: '#15803d', border: '#166534' },
    warn: { fg: '#ffffff', bg: '#b45309', border: '#92400e' },
    error: { fg: '#ffffff', bg: '#dc2626', border: '#b91c1c' },
  },
  palette: {
    timestamp: '#6b7280',
    namespace: '#111827',
    message: '#000000',
    muted: '#4b5563',
    accent: '#1d4ed8',
    link: '#1d4ed8',
    divider: '#d1d5db',
  },
};

/** Banner 逐行渐变取色。 */
export const BANNER_GRADIENT: readonly string[] = [
  '#22d3ee',
  '#2dd4bf',
  '#34d399',
  '#4ade80',
  '#a3e635',
  '#facc15',
  '#fb923c',
  '#f87171',
  '#e879f9',
  '#c084fc',
];

const DEFAULT_PALETTE_KEYS = Object.keys(DEFAULT_THEME.palette) as (keyof ThemePalette)[];

/**
 * 主题深合并。
 *
 * - `colors[level]` 按字段合并，只传 fg 不会清掉 bg / border；
 * - `palette` 按字段合并；
 * - 未提供的级别与字段完整保留基主题的值。
 */
export function mergeTheme(base: Theme, patch: ThemeInput | undefined): Theme {
  if (!patch) return base;

  const colors: ThemeColors = { ...base.colors };
  if (patch.colors) {
    for (const level of LOG_LEVELS) {
      const scheme = patch.colors[level];
      if (scheme) {
        colors[level] = { ...base.colors[level], ...scheme };
      }
    }
  }

  const palette: ThemePalette = { ...base.palette };
  if (patch.palette) {
    for (const key of DEFAULT_PALETTE_KEYS) {
      const value = patch.palette[key];
      if (value !== undefined) palette[key] = value;
    }
  }

  return { colors, palette };
}

/** 依次叠加多个主题覆盖，得到最终主题。 */
export function resolveTheme(...patches: (ThemeInput | undefined)[]): Theme {
  let theme = DEFAULT_THEME;
  for (const patch of patches) {
    theme = mergeTheme(theme, patch);
  }
  return theme;
}
