/**
 * console 劫持：采集 debug/info/warn/error 调用，同时保留原始输出与可还原能力。
 *
 * <p>回环防护：SDK 内部若需输出日志，必须使用 {@link originalConsole} 导出的原始引用，
 * 禁止经被劫持的 console 输出，避免自生自报。</p>
 */

/* eslint-disable no-console -- 本模块职责即包装 console，需直接引用原始 console 方法 */

import { DEFAULT_CONSOLE_LEVELS } from './constants';
import type { LogLevel } from './types';

/** console 采集级别对应的方法名。 */
type ConsoleMethod = 'debug' | 'info' | 'warn' | 'error';

/** 原始（未被劫持）console 方法引用，供 SDK 内部安全输出使用。 */
export const originalConsole: Record<ConsoleMethod, (...args: unknown[]) => void> = {
  debug: console.debug.bind(console),
  info: console.info.bind(console),
  warn: console.warn.bind(console),
  error: console.error.bind(console),
};

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
