// 主题 Token 定义 - 与 CSS 变量保持一致

export const tokens = {
  // Brand colors
  colorPrimary: '#1890ff',
  colorSuccess: '#52c41a',
  colorWarning: '#faad14',
  colorError: '#ff4d4f',
  colorInfo: '#1890ff',

  // Neutral
  colorText: 'rgba(0, 0, 0, 0.88)',
  colorTextSecondary: 'rgba(0, 0, 0, 0.65)',
  colorBorder: '#d9d9d9',
  colorBgLayout: '#f5f5f5',
  colorBgContainer: '#ffffff',

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
