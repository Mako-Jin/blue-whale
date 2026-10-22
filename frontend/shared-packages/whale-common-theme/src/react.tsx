// React 适配层：useTheme Hook、ThemeProvider 与统一切换组件

import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Button, ConfigProvider, Select, Space, Tooltip } from 'antd';
import { themeCore } from './core';
import { getAntdTheme } from './antd-config';
import { AVAILABLE_THEMES, getThemeName } from './palettes';
import type { ThemeId, ThemeMode } from './types';

/** ThemeProvider 属性 */
export interface ThemeProviderProps {
  children: ReactNode;
}

/**
 * 统一主题提供者：初始化 themeCore，将当前主题映射为 antd ConfigProvider 配置。
 *
 * @param props 组件属性
 * @returns 主题上下文包裹后的子树
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  const [state, setState] = useState(() => {
    themeCore.init();
    return themeCore.getState();
  });

  useEffect(() => {
    const unsubscribe = themeCore.subscribe(setState);
    const unbindSync = themeCore.bindCrossTabSync();
    return () => {
      unsubscribe();
      unbindSync();
    };
  }, []);

  const theme = useMemo(
    () => getAntdTheme(state.mode === 'dark', state.themeId),
    [state.mode, state.themeId],
  );

  return <ConfigProvider theme={theme}>{children}</ConfigProvider>;
}

/**
 * 订阅并操作统一主题。
 *
 * @returns 主题状态与操作方法
 */
// eslint-disable-next-line react-refresh/only-export-components -- Provider 与 hook 同文件导出，保持包入口聚合
export function useTheme() {
  const [state, setState] = useState(() => {
    themeCore.init();
    return themeCore.getState();
  });

  useEffect(() => themeCore.subscribe(setState), []);

  return useMemo(
    () => ({
      themeId: state.themeId,
      themeName: getThemeName(state.themeId),
      mode: state.mode,
      isDark: state.mode === 'dark',
      isUserCustomMode: state.isUserCustomMode ?? false,
      /** 切换配色主题 */
      setTheme: (themeId: ThemeId) => themeCore.setTheme(themeId),
      /** 设置明暗模式 */
      setMode: (mode: ThemeMode) => themeCore.setMode(mode),
      /** 切换明暗模式 */
      toggleMode: () => themeCore.toggleMode(),
      /** 重置为按时间自动选择 */
      resetToAutoMode: () => themeCore.resetToAutoMode(),
      /** 读取当前 CSS 变量 */
      getCSSVars: () => themeCore.getCSSVariables(),
    }),
    [state],
  );
}

/** ThemeSwitcher 属性 */
export interface ThemeSwitcherProps {
  /** 紧凑尺寸（用于顶栏） */
  compact?: boolean;
}

/**
 * 统一切换组件：主题配色下拉 + 明暗切换按钮。
 *
 * @param props 组件属性
 * @returns 切换控件
 */
export function ThemeSwitcher({ compact = false }: ThemeSwitcherProps) {
  const { themeId, mode, setTheme, toggleMode } = useTheme();
  const options = AVAILABLE_THEMES.map((id) => ({ value: id, label: getThemeName(id) }));

  return (
    <Space size={compact ? 4 : 8}>
      <Select
        aria-label="选择主题"
        size={compact ? 'small' : 'middle'}
        value={themeId}
        options={options}
        style={{ width: compact ? 132 : 160 }}
        onChange={(value: ThemeId) => setTheme(value)}
      />
      <Tooltip title={mode === 'dark' ? '切换到亮色' : '切换到暗色'}>
        <Button size={compact ? 'small' : 'middle'} aria-label="切换明暗模式" onClick={toggleMode}>
          {mode === 'dark' ? '亮色' : '暗色'}
        </Button>
      </Tooltip>
    </Space>
  );
}
