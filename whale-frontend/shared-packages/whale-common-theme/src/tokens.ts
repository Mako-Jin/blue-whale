// 主题 Token 定义 - 与 CSS 变量保持一致，默认取主题包默认配色（LOTUS_POND_GREEN 亮色）

import { DEFAULT_THEME_ID, getThemeById } from './palettes';

const defaultPalette = getThemeById(DEFAULT_THEME_ID).light;

export const tokens = {
  // Brand colors
  colorPrimary: defaultPalette.primary,
  colorSuccess: '#52c41a',
  colorWarning: '#faad14',
  colorError: '#ff4d4f',
  colorInfo: defaultPalette.accent,

  // Neutral
  colorText: defaultPalette.text,
  colorTextSecondary: defaultPalette.neutralLight,
  colorBorder: defaultPalette.border,
  colorBgLayout: defaultPalette.background,
  colorBgContainer: defaultPalette.cardBackground,

  // Spacing
  spacingXs: '4px',
  spacingSm: '8px',
  spacingMd: '16px',
  spacingLg: '24px',
  spacingXl: '32px',

  // Font
  fontSizeBase: '14px',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',

  // Radius
  borderRadius: '6px',
  borderRadiusSm: '4px',
  borderRadiusLg: '8px',

  // Shadow
  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
  boxShadowLg: '0 6px 16px rgba(0, 0, 0, 0.12)',
} as const;

export type ThemeTokens = typeof tokens;
