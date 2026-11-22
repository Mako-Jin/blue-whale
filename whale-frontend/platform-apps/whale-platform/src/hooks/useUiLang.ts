// 基座界面语言状态：复用共享语言工具，监听 admin（qiankun 子应用）的切换广播。
// 基座本身没有语言切换器，偏好由 admin 的 LanguageSwitcher 写入 whale-admin-lang；

import { useEffect, useState } from 'react';
import { detectUiLang, subscribeUiLang, type UiLang } from '@bluewhale/whale-auth';

/**
 * 读取当前界面语言并订阅变更。
 *
 * @returns 当前语言；admin 切换后自动更新
 */
export function useUiLang(): UiLang {
  const [language, setLanguage] = useState<UiLang>(() => detectUiLang());

  useEffect(() => subscribeUiLang(setLanguage), []);

  return language;
}
