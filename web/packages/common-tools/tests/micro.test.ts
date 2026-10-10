import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { setLoggerSink } from '../src/logger';
import {
  bindHost,
  getHostGlobalState,
  getHostProps,
  isMicroApp,
  onHostGlobalStateChange,
  setHostGlobalState,
  unbindHost,
} from '../src/micro';
import type { HostGlobalState, HostProps } from '../src/micro';

/** 造一个可控的基座：能看到子应用回写了什么，也能主动推送状态变化。 */
function createHost(initial: HostGlobalState = { theme: 'light', locale: 'zh-CN', userId: null }) {
  const container = document.createElement('div');
  document.body.appendChild(container);

  let state: HostGlobalState = { ...initial };
  const written: Partial<HostGlobalState>[] = [];
  const overwrites: number[] = [];

  /**
   * 只留**一个**槽位 —— 这是对 qiankun 真实行为的还原，不能图省事写成数组。
   *
   * qiankun 的 `onGlobalStateChange` 每个应用只保存一个回调，新注册直接覆盖旧的。
   * 测试替身若比真实实现宽容（允许任意多个监听），恰恰会把"两个消费者互相顶掉"
   * 这类 bug 放过去 —— 这个 bug 正是先在本项目真实跑起来之后才发现的。
   */
  let listener: ((next: HostGlobalState, prev: HostGlobalState) => void) | null = null;

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
      if (listener) {
        overwrites.push(overwrites.length + 1);
      }
      listener = callback;
      if (fireImmediately) {
        callback(state, state);
      }
    },
    offGlobalStateChange: () => {
      listener = null;
    },
  };

  return {
    host,
    container,
    written,
    /** 原生回调被覆盖过几次。凡大于 0，说明有消费者会被静默饿死。 */
    overwrites,
    push(next: HostGlobalState) {
      const prev = state;
      state = { ...state, ...next };
      listener?.(state, prev);
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

  /* ---------------- 多路复用（回归：曾因覆盖导致调试标记静默失效） ---------------- */

  it('绑定后只注册一个原生回调，不覆盖任何监听', () => {
    const { host, overwrites } = createHost();

    bindHost(host, 'demo-app');

    /**
     * 桥自己注册调试标记 + 一个原生回调。只要原生回调只注册一次，
     * 就不会触发 qiankun 的"覆盖"分支。
     */
    expect(overwrites).toEqual([]);
  });

  it('多个订阅者能同时收到状态变化', () => {
    const { host, push } = createHost();
    bindHost(host, 'demo-app');

    const seenByA: string[] = [];
    const seenByB: string[] = [];
    onHostGlobalStateChange((state) => seenByA.push(String(state.userId)));
    onHostGlobalStateChange((state) => seenByB.push(String(state.userId)));

    push({ userId: 'u-42' });

    expect(seenByA).toEqual(['u-42']);
    expect(seenByB).toEqual(['u-42']);
    // 调试标记也要跟着变 —— 它只是"第三个订阅者"，不该被业务订阅顶掉
    expect(document.getElementById('micro-host-indicator')?.textContent).toContain('userId=u-42');
  });

  it('取消订阅之后不再收到回调', () => {
    const { host, push } = createHost();
    bindHost(host, 'demo-app');

    const received: string[] = [];
    const unsubscribe = onHostGlobalStateChange((state) => received.push(String(state.theme)));

    push({ theme: 'dark' });
    unsubscribe();
    push({ theme: 'light' });

    expect(received).toEqual(['dark']);
  });

  it('默认不立即回调，显式要求时才回灌当前值', () => {
    const { host } = createHost({ theme: 'dark', userId: 'u-9' });
    bindHost(host, 'demo-app');

    const immediate: string[] = [];
    onHostGlobalStateChange((state) => immediate.push(String(state.theme)));
    expect(immediate).toEqual([]);

    const seeded: string[] = [];
    onHostGlobalStateChange((state) => seeded.push(`${state.theme}/${state.userId}`), {
      fireImmediately: true,
    });
    expect(seeded).toEqual(['dark/u-9']);
  });

  it('基座不提供 getGlobalState 时，退回最近一次推送的状态', () => {
    const { host, push } = createHost();
    // 模拟老基座：没有 getGlobalState，只能靠推送
    bindHost({ ...host, getGlobalState: undefined }, 'demo-app');

    expect(getHostGlobalState()).toEqual({ theme: 'light', locale: 'zh-CN', userId: null });

    push({ theme: 'dark' });
    expect(getHostGlobalState()).toEqual({ theme: 'dark', locale: 'zh-CN', userId: null });
  });

  it('解绑会清空订阅者与原生回调，重复挂载不会重复回调', () => {
    const { host, push } = createHost();
    bindHost(host, 'demo-app');

    const received: string[] = [];
    onHostGlobalStateChange((state) => received.push(String(state.theme)));

    // 上一轮卸载
    unbindHost();

    // 再挂一次（子应用模块在多次挂载之间是复用的）
    bindHost(host, 'demo-app');
    push({ theme: 'dark' });

    // 只有新挂载那一次的回调，没有把上一轮的订阅者带过来
    expect(received).toEqual([]);
    expect(document.getElementById('micro-host-indicator')?.textContent).toContain('theme=dark');
  });
});
