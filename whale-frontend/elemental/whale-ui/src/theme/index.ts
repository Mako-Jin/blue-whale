/**
 * whale-ui 主题变量注入层。
 *
 * 复用 `@bluewhale/whale-common-theme` 的 `--bw-*` 设计令牌体系（平台唯一权威主题层），
 * 仅补齐「将令牌程序化写入 CSS 自定义属性」的能力，不再新增第二套 `--whale-*` 变量。
 */

import { tokens } from '@bluewhale/whale-common-theme';
import type { ThemeTokens } from '@bluewhale/whale-common-theme';

/**
 * BlueWhale 设计令牌（Brand / Neutral / Spacing / Font / Radius / Shadow）。
 *
 * 权威定义位于 `@bluewhale/whale-common-theme/src/tokens.ts`，此处作为统一入口透出。
 */
export { tokens } from '@bluewhale/whale-common-theme';

/**
 * 主题令牌类型（`typeof tokens`），供调用方做类型约束。
 */
export type { ThemeTokens } from '@bluewhale/whale-common-theme';

/**
 * antd v5 `ConfigProvider` 主题配置（默认浅色）。
 */
export { antdThemeConfig } from '@bluewhale/whale-common-theme';

/**
 * 按明暗模式返回 antd v5 主题配置。
 *
 * @param isDark 是否启用暗色主题，默认 `false`
 */
export { getAntdTheme } from '@bluewhale/whale-common-theme';

/**
 * 令牌键 → CSS 自定义属性名映射表。
 *
 * 变量名与 `@bluewhale/whale-common-theme/styles/variables.css` 中的 `:root` 定义完全同名。
 */
const TOKEN_VAR_MAP: Record<keyof ThemeTokens, string> = {
  colorPrimary: '--bw-color-primary',
  colorSuccess: '--bw-color-success',
  colorWarning: '--bw-color-warning',
  colorError: '--bw-color-error',
  colorInfo: '--bw-color-info',
  colorText: '--bw-color-text',
  colorTextSecondary: '--bw-color-text-secondary',
  colorBorder: '--bw-color-border',
  colorBgLayout: '--bw-color-bg-layout',
  colorBgContainer: '--bw-color-bg-container',
  spacingXs: '--bw-spacing-xs',
  spacingSm: '--bw-spacing-sm',
  spacingMd: '--bw-spacing-md',
  spacingLg: '--bw-spacing-lg',
  spacingXl: '--bw-spacing-xl',
  fontSizeBase: '--bw-font-size-base',
  fontFamily: '--bw-font-family',
  borderRadius: '--bw-border-radius',
  borderRadiusSm: '--bw-border-radius-sm',
  borderRadiusLg: '--bw-border-radius-lg',
  boxShadow: '--bw-shadow',
  boxShadowLg: '--bw-shadow-lg',
};

/**
 * TS 令牌未覆盖、但 `variables.css` 仍定义的 CSS 变量缺省值表（取值与 CSS 文件一致）。
 */
const STATIC_VARS: Record<string, string> = {
  '--bw-color-text-tertiary': 'rgba(0, 0, 0, 0.45)',
  '--bw-color-border-secondary': '#f0f0f0',
  '--bw-color-bg-elevated': '#ffffff',
  '--bw-font-size-xs': '12px',
  '--bw-font-size-sm': '13px',
  '--bw-font-size-lg': '16px',
  '--bw-font-size-xl': '20px',
  '--bw-shadow-sm': '0 1px 2px rgba(0, 0, 0, 0.06)',
  '--bw-header-height': '56px',
  '--bw-sider-width': '220px',
};

/**
 * 将（合并后的）设计令牌映射为 CSS 自定义属性键值对。
 *
 * 纯函数：不访问 DOM，便于单测与 SSR 场景调用。覆盖 `variables.css` 中全部 `--bw-*` 变量，
 * 缺省值取自 `@bluewhale/whale-common-theme` 令牌与 CSS 文件。
 *
 * @param input 需要覆盖的令牌子集，缺省时全部使用默认令牌
 * @returns CSS 变量名 → 变量值的映射
 */
export function buildThemeVars(input: Partial<ThemeTokens> = {}): Record<string, string> {
  const merged = { ...tokens, ...input };
  const vars: Record<string, string> = { ...STATIC_VARS };
  for (const key of Object.keys(TOKEN_VAR_MAP) as Array<keyof ThemeTokens>) {
    vars[TOKEN_VAR_MAP[key]] = String(merged[key]);
  }
  return vars;
}

/**
 * 将（合并后的）设计令牌写入目标元素的 `style` 自定义属性。
 *
 * 默认写入 `document.documentElement`（即 `:root`）；SSR 安全（`document` 未定义时为空操作）；
 * 重复调用幂等（同名属性直接覆盖）。
 *
 * @param input 需要覆盖的令牌子集，缺省时全部使用默认令牌
 * @param target 目标元素，缺省为 `document.documentElement`
 */
export function applyThemeVars(input: Partial<ThemeTokens> = {}, target?: HTMLElement): void {
  const root = target ?? (typeof document !== 'undefined' ? document.documentElement : undefined);
  if (!root) return;
  const vars = buildThemeVars(input);
  for (const name of Object.keys(vars)) {
    root.style.setProperty(name, vars[name]);
  }
}
