/**
 * console 劫持：采集 debug/info/warn/error 调用，同时保留原始输出与可还原能力。
 *
 * <p>回环防护：SDK 内部与结构化 Logger 必须使用 {@link originalConsole}（或
 * {@link getOriginalConsole}）导出的原始引用，禁止经被劫持的 console 输出，
 * 避免“同一条日志被 onRecord 与 console 劫持各采集一次”。</p>
 */

import { DEFAULT_CONSOLE_LEVELS } from './constants';
import type { LogLevel } from './types';

/** 支持的 console 方法名。 */
export type NativeConsoleMethod =
  | 'log'
  | 'info'
  | 'debug'
  | 'warn'
  | 'error'
  | 'table'
  | 'group'
  | 'groupCollapsed'
  | 'groupEnd'
  | 'clear';

/** console 采集级别对应的方法名。 */
type ConsoleMethod = 'debug' | 'info' | 'warn' | 'error';

/** 全局原始 console 存档键（供 qiankun 子应用复用宿主已保存的原始引用）。 */
const ORIGINAL_CONSOLE_KEY = '__BLUEWHALE_ORIGINAL_CONSOLE__';

/** 取原始方法并绑定 console。 */
function bindNative(method: NativeConsoleMethod): (...args: unknown[]) => void {
  const target = console as unknown as Record<string, unknown>;
  const fn = target[method];
  if (typeof fn !== 'function') {
    return () => undefined;
  }
  return (fn as (...args: unknown[]) => void).bind(console);
}

/** 原始（未被劫持）console 方法引用，供 SDK 与 Logger 安全输出使用。 */
export const originalConsole: Record<NativeConsoleMethod, (...args: unknown[]) => void> = {
  log: bindNative('log'),
  info: bindNative('info'),
  debug: bindNative('debug'),
  warn: bindNative('warn'),
  error: bindNative('error'),
  table: bindNative('table'),
  group: bindNative('group'),
  groupCollapsed: bindNative('groupCollapsed'),
  groupEnd: bindNative('groupEnd'),
  clear: bindNative('clear'),
};

/** 把本次的原始 console 存档到全局，供其他构建副本复用。 */
export function publishOriginalConsole(): void {
  try {
    (globalThis as Record<string, unknown>)[ORIGINAL_CONSOLE_KEY] = originalConsole;
  } catch {
    // 全局对象不可写时忽略，本副本仍可使用模块内快照
  }
}

/**
 * 获取原始 console。
 *
 * <p>优先返回全局存档：qiankun 子应用加载时宿主的 SDK 早已劫持 console，
 * 子应用模块内的快照可能拿到的是“已劫持版本”，用全局存档才能避免重复采集。</p>
 */
export function getOriginalConsole(): Record<NativeConsoleMethod, (...args: unknown[]) => void> {
  try {
    const stored = (globalThis as Record<string, unknown>)[ORIGINAL_CONSOLE_KEY];
    if (stored) {
      return stored as Record<NativeConsoleMethod, (...args: unknown[]) => void>;
    }
  } catch {
    // 忽略读取失败
  }
  return originalConsole;
}

/** console 级别到 console 方法名的映射。 */
const LEVEL_METHOD: Record<LogLevel, ConsoleMethod> = {
  DEBUG: 'debug',
  INFO: 'info',
  WARN: 'warn',
  ERROR: 'error',
};

/** console 采集选项。 */
export interface ConsoleCaptureOptions {
  /** 采集的级别集合（默认四级） */
  levels?: LogLevel[];
  /** 采集回调：收到级别与原始参数 */
  collect: (level: LogLevel, args: unknown[]) => void;
}

/** 已安装的 console 劫持句柄。 */
export interface ConsoleCaptureHandle {
  /** 还原 console 原始方法 */
  restore: () => void;
}

/**
 * 安装 console 劫持。
 *
 * <p>每个级别的包装函数先调用原始方法保证原输出不变，再以重入锁保护地触发采集回调；
 * {@link ConsoleCaptureHandle.restore} 可完整还原。</p>
 *
 * @param options 采集级别与回调
 * @returns 还原句柄
 */
export function installConsoleCapture(options: ConsoleCaptureOptions): ConsoleCaptureHandle {
  const levels = options.levels ?? DEFAULT_CONSOLE_LEVELS;
  const restorers: Array<() => void> = [];
  let reentrant = false;

  for (const level of levels) {
    const method = LEVEL_METHOD[level];
    const original = originalConsole[method];
    const patched = (...args: unknown[]): void => {
      original(...args);
      if (reentrant) {
        return;
      }
      reentrant = true;
      try {
        options.collect(level, args);
      } catch {
        // 采集失败不得影响业务与原始输出
      } finally {
        reentrant = false;
      }
    };
    (console as unknown as Record<string, unknown>)[method] = patched;
    restorers.push(() => {
      (console as unknown as Record<string, unknown>)[method] = original;
    });
  }

  let restored = false;
  return {
    restore: () => {
      if (restored) {
        return;
      }
      restored = true;
      for (const restore of restorers) {
        restore();
      }
    },
  };
}
