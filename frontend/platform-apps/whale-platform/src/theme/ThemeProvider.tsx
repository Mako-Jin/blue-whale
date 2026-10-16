// 主题 Provider - 接入 @bluewhale/whale-common-theme 的 antd token，
// 暗色模式叠加 antd darkAlgorithm，选择结果持久化到 localStorage 并同步 <html data-theme>

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { theme as antdTheme, ConfigProvider } from 'antd';
import { antdThemeConfig } from '@bluewhale/whale-common-theme';
import { ThemeContext, type ThemeMode } from './context';

const THEME_STORAGE_KEY = 'whale_theme';

function getInitialMode(): ThemeMode {
  return localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(getInitialMode);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    localStorage.setItem(THEME_STORAGE_KEY, mode);
  }, [mode]);

  const toggleTheme = useCallback(() => {
    setMode((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }, []);

  const contextValue = useMemo(
    () => ({ mode, isDark: mode === 'dark', toggleTheme }),
    [mode, toggleTheme],
  );

  const themeConfig = useMemo(
    () => (mode === 'dark' ? { ...antdThemeConfig, algorithm: antdTheme.darkAlgorithm } : antdThemeConfig),
    [mode],
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      <ConfigProvider theme={themeConfig}>{children}</ConfigProvider>
    </ThemeContext.Provider>
  );
}
