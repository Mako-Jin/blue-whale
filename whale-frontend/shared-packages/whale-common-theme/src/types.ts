// 主题类型定义（移植自 platform-terminator/packages/common-theme）

/** 主题 ID（业务层定义角色，主题包只提供配色） */
export type ThemeId =
  | 'CLASSIC_BLUE'
  | 'LOTUS_POND_GREEN'
  | 'WISTERIA_BLUE'
  | 'NAVY_ROSE'
  | 'OCHRE_BROWN'
  | 'PINE_GREEN'
  | 'PEONY_RED';

/** 明暗模式类型 */
export type ThemeMode = 'light' | 'dark';

/** 单个主题配色变量集合 */
export interface ThemePalette {
  primary: string;
  secondary: string;
  accent: string;
  neutralDark: string;
  neutralLight: string;
  background: string;
  cardBackground: string;
  text: string;
  border: string;
}

/** 主题完整配置（亮/暗两套配色） */
export interface ThemeConfig {
  id: ThemeId;
  light: ThemePalette;
  dark: ThemePalette;
}

/** 主题核心状态 */
export interface ThemeState {
  themeId: ThemeId;
  mode: ThemeMode;
  /** true = 用户手动选择过模式 */
  isUserCustomMode?: boolean;
}

/** 主题变更回调函数 */
export type ThemeListener = (state: ThemeState) => void;
