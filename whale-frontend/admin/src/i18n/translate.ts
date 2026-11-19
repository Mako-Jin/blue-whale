// 文案翻译：占位符替换 + 英文缺失时回落中文，保证永不展示空串

import type { Language } from './language';
import { messages, type MessageKey } from './messages';

/** 占位符参数 */
export type MessageVars = Record<string, string | number>;

/**
 * 翻译指定键。
 *
 * @param language 目标语言
 * @param key 文案键
 * @param vars 占位符参数，占位形式 `{name}`
 * @returns 翻译结果；键不存在时返回键本身
 */
export function translate(language: Language, key: MessageKey, vars?: MessageVars): string {
  const template = messages[language][key] ?? messages['zh-CN'][key] ?? key;
  if (!vars) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (matched, name: string) =>
    vars[name] === undefined ? matched : String(vars[name]),
  );
}
