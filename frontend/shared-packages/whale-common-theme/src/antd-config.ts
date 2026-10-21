// Ant Design v5 主题配置 - 由主题配色 + 明暗算法动态派生

import { theme as antdTheme } from 'antd';
import type { ThemeConfig } from 'antd';
import { DEFAULT_THEME_ID, getThemeById } from './palettes';
import type { ThemeId } from './types';
import { tokens } from './tokens';

/** 暗色下的次级文字颜色 */
const DARK_TEXT_SECONDARY = 'rgba(255, 255, 255, 0.65)';

/**
 * 按明暗模式与主题 ID 生成 antd `ConfigProvider` 主题配置。
 *
 * @param isDark 是否暗色，默认 false
 * @param themeId 主题 ID，默认取主题包默认配色
 * @returns antd 主题配置
 */
export function getAntdTheme(isDark = false, themeId: ThemeId = DEFAULT_THEME_ID): ThemeConfig {
  const config = getThemeById(themeId);
  const palette = isDark ? config.dark : config.light;

  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: palette.primary,
      colorSuccess: tokens.colorSuccess,
      colorWarning: tokens.colorWarning,
      colorError: tokens.colorError,
      colorInfo: palette.accent,
      colorText: palette.text,
      colorTextSecondary: isDark ? DARK_TEXT_SECONDARY : tokens.colorTextSecondary,
      colorBorder: palette.border,
      colorBgLayout: palette.background,
      colorBgContainer: palette.cardBackground,
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
        headerBg: palette.background,
        rowHoverBg: palette.background,
      },
      Layout: {
        headerBg: palette.cardBackground,
        bodyBg: palette.background,
        siderBg: isDark ? palette.cardBackground : palette.neutralDark,
      },
    },
  };
}

/** 默认主题（LOTUS_POND_GREEN 亮色）的 antd 配置，保留供旧代码直接引用 */
export const antdThemeConfig: ThemeConfig = getAntdTheme(false);
