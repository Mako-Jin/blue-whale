import { cloneDeep, debounce, throttle, isEmpty, isNil, pick, omit, merge } from 'lodash-es';

export { cloneDeep, debounce, throttle, isEmpty, isNil, pick, omit, merge };

// 对象工具占位 - 按需扩展
export function safeJsonParse<T = unknown>(str: string, fallback?: T): T | undefined {
  try {
    return JSON.parse(str) as T;
  } catch {
    return fallback;
  }
}

export function safeJsonStringify(value: unknown, fallback = ''): string {
  try {
    return JSON.stringify(value);
  } catch {
    return fallback;
  }
}
