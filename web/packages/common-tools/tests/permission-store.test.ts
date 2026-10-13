import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { setLoggerSink } from '../src/logger';
import { bindHost, unbindHost } from '../src/micro';
import { createPermissionStore, visibleViews } from '../src/micro/permission-store';
import type { HostGlobalState, HostProps, IdentitySnapshot } from '../src/micro';

/**
 * 权限 store 的行为。
 *
 * <p>它有两个分支（被基座加载 / 独立运行），而**两个分支的差别恰恰是最容易写歪的地方**：
 * 判据用错（把"还没登录"当成"独立运行"）会自建第二套会话，
 * 或者在被基座加载时去读一份永远为空的本地会话 —— 症状都是"入口一个都不出现"，
 * 而排查方向会先指向"权限没配"。因此两条分支各有一条用例。
 */

/** 基座替身：够用即可，本文件不测桥本身（那在 micro.test.ts） */
function createHost(initial: HostGlobalState) {
  const container = document.createElement('div');
  document.body.appendChild(container);

  let state: HostGlobalState = { ...initial };
  let listener: ((next: HostGlobalState, prev: HostGlobalState) => void) | null = null;

  const host: HostProps = {
    name: 'demo-app',
    container,
    basename: '/demo',
    getGlobalState: () => ({ ...state }),
    setGlobalState: (next) => {
      state = { ...state, ...next };
    },
    onGlobalStateChange: (callback, fireImmediately) => {
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
    push(next: HostGlobalState) {
      const prev = state;
      state = { ...state, ...next };
      listener?.(state, prev);
    },
  };
}

/** 独立运行的会话替身：可读、可主动通知 */
function createStandalone(initial: IdentitySnapshot | null = null) {
  let identity = initial;
  const listeners = new Set<() => void>();

  return {
    source: {
      readStandaloneIdentity: () => identity,
      onStandaloneIdentityChange: (listener: () => void) => {
        listeners.add(listener);
        return () => {
          listeners.delete(listener);
        };
      },
    },
    set(next: IdentitySnapshot | null) {
      identity = next;
      for (const listener of Array.from(listeners)) listener();
    },
    /** 订阅者数量 —— 用来钉住"断开之后不留残余订阅" */
    listenerCount: () => listeners.size,
  };
}

beforeEach(() => {
  setLoggerSink(() => {});
});

afterEach(() => {
  unbindHost();
  document.body.innerHTML = '';
});

describe('权限 store · 基座分支', () => {
  it('未绑定时走独立运行分支：没有身份 ⇒ ready 为假，判定一律为假', () => {
    const standalone = createStandalone(null);
    const store = createPermissionStore(standalone.source);

    const stop = store.connect();

    expect(store.getState().ready).toBe(false);
    // 宁可少显示一个入口，也不能默认放行
    expect(store.has('ui:admin:view')).toBe(false);

    stop();
  });

  it('被基座加载时只认基座下发的集合，不去读本地会话', () => {
    // 本地会话里有身份，但基座模式下**不该**被读到 —— 那是两套会话并存的入口
    const standalone = createStandalone({
      userId: 'local-user',
      roles: ['SUPER_ADMIN'],
      permissions: ['ui:admin:view'],
    });
    const store = createPermissionStore(standalone.source);

    const { host } = createHost({
      theme: 'light',
      locale: 'zh-CN',
      userId: 'base-user',
      roles: ['AUDITOR'],
      permissions: ['ui:audit_console:view'],
    });
    bindHost(host, 'demo-app');

    const stop = store.connect();

    expect(store.getState().userId).toBe('base-user');
    expect(store.getState().permissions).toEqual(['ui:audit_console:view']);
    expect(store.has('ui:audit_console:view')).toBe(true);
    expect(store.has('ui:admin:view')).toBe(false);

    stop();
  });

  it('基座推送新集合时通知订阅者；集合引用没变则不通知', () => {
    const standalone = createStandalone(null);
    const store = createPermissionStore(standalone.source);

    const permissions = ['ui:admin:view'];
    const { host, push } = createHost({
      theme: 'light',
      locale: 'zh-CN',
      userId: 'u-1',
      roles: [],
      permissions,
    });
    bindHost(host, 'demo-app');

    const stop = store.connect();
    let notifications = 0;
    const off = store.subscribe(() => {
      notifications += 1;
    });

    // 同一个数组引用再来一次（基座每推一次主题就会走这条路径）——
    // 不比对引用的话，切主题会让所有消费组件重渲染一轮
    push({ theme: 'dark', permissions });
    expect(notifications).toBe(0);

    push({ permissions: ['ui:admin:view', 'ui:audit_console:view'] });
    expect(notifications).toBe(1);
    expect(store.has('ui:audit_console:view')).toBe(true);

    off();
    stop();
  });

  it('断开后复位为未就绪，且不再跟随基座推送（防重复挂载留下重复订阅）', () => {
    const standalone = createStandalone(null);
    const store = createPermissionStore(standalone.source);
    const { host, push } = createHost({
      theme: 'light',
      locale: 'zh-CN',
      userId: 'u-1',
      roles: [],
      permissions: ['ui:admin:view'],
    });
    bindHost(host, 'demo-app');

    const stop = store.connect();
    expect(store.getState().ready).toBe(true);

    stop();
    // 留下上一轮的权限，会让下一次挂载在"尚未收到推送"的瞬间拿着失效的集合渲染入口
    expect(store.getState().ready).toBe(false);

    push({ permissions: [] });
    expect(store.getState().ready).toBe(false);
  });
});

describe('权限 store · 独立运行分支', () => {
  it('从会话读到身份后就绪，身份变化时通知', () => {
    const standalone = createStandalone(null);
    const store = createPermissionStore(standalone.source);

    const stop = store.connect();
    expect(store.getState().ready).toBe(false);

    standalone.set({
      userId: '7',
      roles: ['AUDITOR'],
      permissions: ['ui:audit_console:view'],
    });

    expect(store.getState().ready).toBe(true);
    expect(store.getState().userId).toBe('7');
    expect(store.has('ui:audit_console:view')).toBe(true);

    stop();
  });

  it('断开时把独立运行那一路的订阅也摘干净', () => {
    const standalone = createStandalone(null);
    const store = createPermissionStore(standalone.source);

    const stop = store.connect();
    expect(standalone.listenerCount()).toBe(1);

    stop();
    expect(standalone.listenerCount()).toBe(0);
    expect(store.getState().ready).toBe(false);
  });
});

describe('visibleViews', () => {
  const views = [
    { name: 'a', permission: 'ui:admin_user:view' },
    { name: 'b', permission: 'ui:admin_org:view' },
    { name: 'c', permission: 'ui:admin_role:view' },
  ];

  it('未就绪时一律返回空 —— "不确定"不等同于"都可以看"', () => {
    expect(
      visibleViews(
        views,
        { ready: false, userId: null, roles: [], permissions: [] },
        (v) => v.permission,
      ),
    ).toEqual([]);
  });

  it('就绪后只留拥有权限的那些，顺序不变', () => {
    const visible = visibleViews(
      views,
      {
        ready: true,
        userId: '1',
        roles: [],
        permissions: ['ui:admin_role:view', 'ui:admin_user:view'],
      },
      (v) => v.permission,
    );

    expect(visible.map((v) => v.name)).toEqual(['a', 'c']);
  });

  it('已登录但一个都没有时返回空列表（调用方据此显示"没有可用视图"，而不是白屏）', () => {
    const visible = visibleViews(
      views,
      { ready: true, userId: '1', roles: [], permissions: [] },
      (v) => v.permission,
    );

    expect(visible).toEqual([]);
  });
});
