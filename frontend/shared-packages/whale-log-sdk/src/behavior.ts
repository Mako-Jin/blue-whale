/**
 * 自动行为埋点：PAGE_VIEW（history 补丁 + popstate）、CLICK（[data-track]）、SUBMIT。
 *
 * <p>仅采集元信息（事件名 / 目标定位 / 页面地址），不采集表单字段值，避免隐私泄露；
 * {@link BehaviorCaptureHandle.destroy} 会还原 history 补丁并移除全部监听。</p>
 */

import { DEFAULT_TRACK_ATTR } from './constants';
import type { EventType } from './types';

/** 行为事件发射回调。 */
export type BehaviorEmit = (
  eventType: EventType,
  eventName: string,
  target: string | null,
  payload: unknown,
) => void;

/** 自动行为埋点选项。 */
export interface BehaviorCaptureOptions {
  /** 是否自动采集 PAGE_VIEW（默认 true） */
  autoPageView?: boolean;
  /** 是否自动采集 CLICK（默认 true） */
  autoClick?: boolean;
  /** 是否自动采集 SUBMIT（默认 true） */
  autoSubmit?: boolean;
  /** 点击埋点选择器属性名（默认 data-track） */
  trackAttr?: string;
  /** 事件发射回调 */
  emit: BehaviorEmit;
}

/** 已安装的行为采集句柄。 */
export interface BehaviorCaptureHandle {
  /** 移除监听并还原 history 补丁 */
  destroy: () => void;
}

/** 无操作句柄（非浏览器环境）。 */
const NOOP_HANDLE: BehaviorCaptureHandle = { destroy: () => undefined };

/** 元素定位串：优先 id，其次 tag + 首个 class。 */
function describeElement(element: Element): string {
  if (element.id) {
    return `#${element.id}`;
  }
  const tag = element.tagName.toLowerCase();
  const className = element.getAttribute('class');
  const firstClass = className ? className.trim().split(/\s+/)[0] : '';
  return firstClass ? `${tag}.${firstClass}` : tag;
}

/**
 * 安装自动行为采集。
 *
 * <p>非浏览器环境（无 {@code window}/{@code document}）直接返回空句柄，便于 SSR / Node 冒烟复用。</p>
 *
 * @param options 采集开关、埋点属性与发射回调
 * @returns 销毁句柄
 */
export function installBehaviorCapture(options: BehaviorCaptureOptions): BehaviorCaptureHandle {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return NOOP_HANDLE;
  }

  const trackAttr = options.trackAttr ?? DEFAULT_TRACK_ATTR;
  const dispose: Array<() => void> = [];

  if (options.autoPageView !== false) {
    const historyLike = window.history as unknown as Record<
      string,
      (...args: unknown[]) => unknown
    >;
    const originalPush = historyLike.pushState.bind(window.history);
    const originalReplace = historyLike.replaceState.bind(window.history);
    const emitPageView = (): void => {
      options.emit(
        'PAGE_VIEW',
        document.title || document.location.pathname,
        document.location.href,
        null,
      );
    };
    historyLike.pushState = (...args: unknown[]): unknown => {
      const result = originalPush(...args);
      emitPageView();
      return result;
    };
    historyLike.replaceState = (...args: unknown[]): unknown => {
      const result = originalReplace(...args);
      emitPageView();
      return result;
    };
    window.addEventListener('popstate', emitPageView);
    dispose.push(() => {
      historyLike.pushState = originalPush;
      historyLike.replaceState = originalReplace;
      window.removeEventListener('popstate', emitPageView);
    });
    emitPageView();
  }

  if (options.autoClick !== false) {
    const onClick = (event: MouseEvent): void => {
      const target = event.target as Element | null;
      const element = target?.closest?.(`[${trackAttr}]`) ?? null;
      if (!element) {
        return;
      }
      const eventName = element.getAttribute(trackAttr) || describeElement(element);
      options.emit('CLICK', eventName, describeElement(element), null);
    };
    document.addEventListener('click', onClick, true);
    dispose.push(() => document.removeEventListener('click', onClick, true));
  }

  if (options.autoSubmit !== false) {
    const onSubmit = (event: Event): void => {
      const form = event.target as Element | null;
      if (!form) {
        return;
      }
      const eventName = form.getAttribute(trackAttr) || describeElement(form);
      options.emit('SUBMIT', eventName, describeElement(form), null);
    };
    document.addEventListener('submit', onSubmit, true);
    dispose.push(() => document.removeEventListener('submit', onSubmit, true));
  }

  let destroyed = false;
  return {
    destroy: () => {
      if (destroyed) {
        return;
      }
      destroyed = true;
      for (const action of dispose) {
        action();
      }
    },
  };
}
