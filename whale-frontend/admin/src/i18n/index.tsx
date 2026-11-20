/* eslint-disable react-refresh/only-export-components -- Provider 与 hook / 切换组件同文件导出，保持 i18n 入口聚合 */

// 轻量 i18n：无第三方依赖，语言持久化 + antd / dayjs 语言同步

import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';
import { ConfigProvider, Select } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { detectLanguage, saveLanguage, type Language } from './language';
import type { MessageKey } from './messages';
import { translate, type MessageVars } from './translate';

/** i18n 上下文值 */
interface I18nContextValue {
  /** 当前语言 */
  language: Language;
  /** 切换语言（持久化） */
  setLanguage: (language: Language) => void;
  /** 翻译函数 */
  t: (key: MessageKey, vars?: MessageVars) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

/**
 * 从配置读取 antd 语言包。
 *
 * @param language 当前语言
 * @returns antd locale 对象
 */
export function getAntdLocale(language: Language) {
  return language === 'en-US' ? enUS : zhCN;
}

/**
 * 国际化提供者：初始化语言并同步 html lang 与 dayjs locale。
 *
 * @param props 组件属性
 * @returns 国际化上下文包裹后的子树
 */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>(() => detectLanguage());

  useEffect(() => {
    saveLanguage(language);
    document.documentElement.lang = language;
    dayjs.locale(language === 'en-US' ? 'en' : 'zh-cn');
  }, [language]);

  const setLanguage = useCallback((next: Language) => setLanguageState(next), []);
  const t = useCallback(
    (key: MessageKey, vars?: MessageVars) => translate(language, key, vars),
    [language],
  );

  const value = useMemo<I18nContextValue>(
    () => ({ language, setLanguage, t }),
    [language, setLanguage, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

/**
 * 读取国际化上下文。
 *
 * @returns 当前语言、切换方法与翻译函数
 */
export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useI18n 必须在 I18nProvider 内使用');
  }
  return context;
}

/**
 * antd 语言提供者：把当前语言同步到 ConfigProvider，日期/分页等内置文案随之切换。
 *
 * @param props 组件属性
 * @returns 带 locale 的 ConfigProvider 子树
 */
export function AntdLocaleProvider({ children }: { children: ReactNode }) {
  const { language } = useI18n();
  return <ConfigProvider locale={getAntdLocale(language)}>{children}</ConfigProvider>;
}

/**
 * 顶栏语言切换器。
 *
 * @returns 语言下拉框
 */
export function LanguageSwitcher() {
  const { language, setLanguage, t } = useI18n();
  return (
    <Select
      aria-label={t('language.label')}
      size="small"
      value={language}
      style={{ width: 96 }}
      onChange={(value: Language) => setLanguage(value)}
      options={[
        { value: 'zh-CN', label: t('language.zh-CN') },
        { value: 'en-US', label: t('language.en-US') },
      ]}
    />
  );
}
