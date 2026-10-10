import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { setLoggerSink } from '../src/logger';
import {
  bindHost,
  getHostGlobalState,
  getHostProps,
  isMicroApp,
  setHostGlobalState,
  unbindHost,
} from '../src/micro';
import type { HostGlobalState, HostProps } from '../src/micro';

/** 造一个可控的基座：能看到子应用回写了什么，也能主动推送状态变化。 */
function createHost(initial: HostGlobalState = { theme: 'light', locale: 'zh-CN', userId: null }) {
  const container = document.createElement('div');
  document.body.appendChild(container);

  let state: HostGlobalState = { ...initial };
  const listeners: Array<(next: HostGlobalState, prev: HostGlobalState) => void> = [];
  const written: Partial<HostGlobalState>[] = [];

  const host: HostProps = {
    name: 'demo-app',
    container,
    basename: '/demo',
    getGlobalState: () => ({ ...state }),
    setGlobalState: (next) => {
      written.push(next);
      state = { ...state, ...next };
    },
    onGlobalStateChange: (callback, fireImmediately) => {
      listeners.push(callback);
      if (fireImmediately) {
        callback(state, state);
      }
    },
  };

  return {
    host,
    container,
    written,
    push(next: HostGlobalState) {
      const prev = state;
      state = { ...state, ...next };
      listeners.forEach((fn) => fn(state, prev));
    },
  };
}

beforeEach(() => {
  // 本模块会写日志，测试里不需要看输出
  setLoggerSink(() => {});
});

afterEach(() => {
  unbindHost();
  document.body.innerHTML = '';
});

describe('micro host bridge', () => {
  it('未绑定基座时视为独立运行', () => {
    expect(isMicroApp()).toBe(false);
    expect(getHostProps()).toBeNull();
    expect(getHostGlobalState()).toEqual({});
  });

  it('未绑定基座时回写状态不抛错', () => {
    expect(() => setHostGlobalState({ userId: 'u-1' })).not.toThrow();
  });

  it('绑定后能读到基座下发的全局状态', () => {
    const { host } = createHost();
    bindHost(host, 'demo-app');

    expect(isMicroApp()).toBe(true);
    expect(getHostGlobalState()).toEqual({ theme: 'light', locale: 'zh-CN', userId: null });
  });

  it('绑定后能把状态回写给基座', () => {
    const { host, written } = createHost();
    bindHost(host, 'demo-app');

    setHostGlobalState({ userId: 'u-42' });

    expect(written).toEqual([{ userId: 'u-42' }]);
  });

  it('基座状态变化时刷新页面上的调试标记', () => {
    const { host, push } = createHost();
    bindHost(host, 'demo-app');

    expect(document.getElementById('micro-host-indicator')?.textContent).toContain(
      'userId=(未登录)',
    );

    push({ userId: 'u-42' });

    expect(document.getElementById('micro-host-indicator')?.textContent).toContain('userId=u-42');
  });

  it('标记挂在基座给的容器里，卸载时清理干净', () => {
    const { host, container } = createHost();
    bindHost(host, 'demo-app');

    expect(container.querySelector('#micro-host-indicator')).not.toBeNull();

    unbindHost();

    expect(container.querySelector('#micro-host-indicator')).toBeNull();
    expect(document.getElementById('micro-host-indicator')).toBeNull();
    expect(isMicroApp()).toBe(false);
    expect((window as unknown as Record<string, unknown>).__MICRO_HOST__).toBeUndefined();
  });

  it('把 props 暴露到 window 上供 DevTools 检查', () => {
    const { host } = createHost();
    bindHost(host, 'demo-app');

    expect((window as unknown as Record<string, unknown>).__MICRO_HOST__).toBe(host);
  });
});
