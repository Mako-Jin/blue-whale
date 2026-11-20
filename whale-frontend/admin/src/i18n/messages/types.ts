// 文案模块定义：中文为键集合基线，英文必须逐键覆盖

/** 单个文案模块 */
export interface MessageModule<T extends Record<string, string>> {
  /** 中文文案（键集合基线） */
  zh: T;
  /** 英文文案（键必须与中文完全一致） */
  en: Record<keyof T & string, string>;
}

/**
 * 声明一个文案模块，编译期校验英文键与中文键一一对应。
 *
 * @param zh 中文文案
 * @param en 英文文案
 * @returns 文案模块
 */
export function defineMessages<T extends Record<string, string>>(
  zh: T,
  en: Record<keyof T & string, string>,
): MessageModule<T> {
  return { zh, en };
}
