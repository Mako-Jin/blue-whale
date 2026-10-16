import type { ThemeConfig } from 'antd';
import { tokens } from './tokens';

// Ant Design ConfigProvider theme 配置
export const antdThemeConfig: ThemeConfig = {
  token: {
    colorPrimary: tokens.colorPrimary,
    colorSuccess: tokens.colorSuccess,
    colorWarning: tokens.colorWarning,
    colorError: tokens.colorError,
    colorInfo: tokens.colorInfo,
    colorText: tokens.colorText,
    borderRadius: 6,
    fontFamily: tokens.fontFamily,
    fontSize: 14,
  },
  components: {
    Button: {
      controlHeight: 32,
      borderRadius: 6,
    },
    Input: {
      controlHeight: 32,
    },
    Modal: {
      titleFontSize: 16,
    },
    Table: {
      headerBg: '#fafafa',
      rowHoverBg: '#f5faff',
    },
  },
};

export function getAntdTheme(isDark = false): ThemeConfig {
  if (!isDark) return antdThemeConfig;
  // TODO: 暗色主题配置
  return {
    ...antdThemeConfig,
    algorithm: undefined, // antd.darkAlgorithm
  };
}
