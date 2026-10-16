// 主题上下文 - 亮/暗模式状态

import { createContext, useContext } from 'react';

export type ThemeMode = 'light' | 'dark';

export interface ThemeContextValue {
  mode: ThemeMode;
  isDark: boolean;
  toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextValue>({
  mode: 'light',
  isDark: false,
  toggleTheme: () => undefined,
});

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
