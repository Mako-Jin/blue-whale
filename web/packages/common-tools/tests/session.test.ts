import { describe, expect, it, vi } from 'vitest';

import { createMemoryStorage, createSessionStore } from '../src/session';
import type { SessionStorage, SessionUser } from '../src/session';

const silent = { logger: null } as const;

const ADMIN: SessionUser = {
  userId: 1,
  username: 'admin',
  displayName: '管理员',
  tenantId: 1,
  tenantCode: 'default',
  roles: ['ADMIN'],
  permissions: ['auth:user:read', 'auth:user:create'],
};

describe('createSessionStore · 基本存取', () => {
  it('初始无会话', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });

    expect(session.isAuthenticated()).toBe(false);
    expect(session.getAccessToken()).toBeNull();
    expect(session.getRefreshToken()).toBeNull();
    expect(session.getSnapshot().user).toBeNull();
  });

  it('set 建立会话，快照里能同时看到身份与令牌', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });

    session.set({
      user: ADMIN,
      tokens: { accessToken: 'a', refreshToken: 'r', expiresAt: 123 },
    });

    const snapshot = session.getSnapshot();
    expect(snapshot.authenticated).toBe(true);
    expect(snapshot.user).toEqual(ADMIN);
    expect(snapshot.tokens).toEqual({ accessToken: 'a', refreshToken: 'r', expiresAt: 123 });
  });

  it('hasPermission 按权限集合判定，未知权限为 false', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    session.set({ user: ADMIN });

    expect(session.hasPermission('auth:user:create')).toBe(true);
    expect(session.hasPermission('auth:tenant:delete')).toBe(false);
  });

  it('updateUser 只换身份与权限，令牌不动', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });

    session.updateUser({ ...ADMIN, permissions: ['auth:user:read'] });

    expect(session.getAccessToken()).toBe('a');
    expect(session.getRefreshToken()).toBe('r');
    expect(session.hasPermission('auth:user:create')).toBe(false);
  });

  it('updateTokens 只换令牌，身份不动', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });

    session.updateTokens({ accessToken: 'a2' });

    expect(session.getAccessToken()).toBe('a2');
    expect(session.getSnapshot().user).toEqual(ADMIN);
  });
});

describe('createSessionStore · 持久化边界', () => {
  it('只持久化刷新令牌，访问令牌不落盘', () => {
    const storage = createMemoryStorage();
    const session = createSessionStore({ ...silent, storage });
    session.set({ user: ADMIN, tokens: { accessToken: 'secret-access', refreshToken: 'r' } });

    const raw = storage.getItem('bw.session');
    expect(raw).toContain('r');
    // 访问令牌留在内存：落盘只是扩大暴露面
    expect(raw).not.toContain('secret-access');
  });

  it('新建 store 能读回刷新令牌（模拟刷新页面）', () => {
    const storage = createMemoryStorage();
    const first = createSessionStore({ ...silent, storage });
    first.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });

    const second = createSessionStore({ ...silent, storage });

    expect(second.getRefreshToken()).toBe('r');
    // 身份不持久化，要靠 /me 或刷新令牌重新拉 —— 所以刷新后是"未恢复但可恢复"状态
    expect(second.getSnapshot().user).toBeNull();
    expect(second.isAuthenticated()).toBe(true);
  });

  it('clear 清空内存与持久化', () => {
    const storage = createMemoryStorage();
    const session = createSessionStore({ ...silent, storage });
    session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });

    session.clear();

    expect(session.getSnapshot().user).toBeNull();
    expect(session.getRefreshToken()).toBeNull();
    expect(storage.getItem('bw.session')).toBeNull();
    expect(createSessionStore({ ...silent, storage }).getRefreshToken()).toBeNull();
  });

  it('持久化载荷版本不认识时忽略，而不是抛错', () => {
    const storage = createMemoryStorage();
    storage.setItem('bw.session', JSON.stringify({ v: 99, refreshToken: 'r' }));

    const session = createSessionStore({ ...silent, storage });

    expect(session.getRefreshToken()).toBeNull();
  });

  it('持久化内容不是合法 JSON 时忽略', () => {
    const storage = createMemoryStorage();
    storage.setItem('bw.session', '{ 这不是 JSON');

    expect(createSessionStore({ ...silent, storage }).getRefreshToken()).toBeNull();
  });

  it('存储读失败时按"没有会话"处理，不抛错', () => {
    const broken: SessionStorage = {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {},
      removeItem: () => {},
    };

    const session = createSessionStore({ ...silent, storage: broken });

    expect(session.getRefreshToken()).toBeNull();
  });

  it('存储写失败不影响本次会话（内存里仍然可用）', () => {
    const broken: SessionStorage = {
      getItem: () => null,
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
      removeItem: () => {},
    };
    const session = createSessionStore({ ...silent, storage: broken });

    expect(() => {
      session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });
    }).not.toThrow();
    expect(session.isAuthenticated()).toBe(true);
  });
});

describe('createSessionStore · 订阅', () => {
  it('会话变化时通知监听器，并带上前后快照', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    const listener = vi.fn();
    session.subscribe(listener);

    session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });

    expect(listener).toHaveBeenCalledTimes(1);
    const [next, previous] = listener.mock.calls[0] as [
      { authenticated: boolean },
      { authenticated: boolean },
    ];
    expect(previous.authenticated).toBe(false);
    expect(next.authenticated).toBe(true);
  });

  it('任何一次 update 都会通知：比较的是引用而非字段值', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    session.set({ user: ADMIN, tokens: { accessToken: 'a', refreshToken: 'r' } });
    const listener = vi.fn();
    session.subscribe(listener);

    // 空 patch 也会换掉 tokens 对象引用 ⇒ 通知。
    // 这是有意的：字段级比较需要手写清单，漏项会静默丢更新，代价比一次多余重渲染大得多
    session.updateTokens({});

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('取消订阅后不再收到通知', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    const listener = vi.fn();
    const unsubscribe = session.subscribe(listener);

    unsubscribe();
    session.set({ user: ADMIN });

    expect(listener).not.toHaveBeenCalled();
  });

  it('某个监听器抛错不影响其他监听器', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    const broken = vi.fn(() => {
      throw new Error('监听器炸了');
    });
    const healthy = vi.fn();
    session.subscribe(broken);
    session.subscribe(healthy);

    expect(() => session.set({ user: ADMIN })).not.toThrow();
    expect(healthy).toHaveBeenCalledTimes(1);
  });

  it('getSnapshot 引用稳定：内容不变时反复取到同一个对象', () => {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    session.set({ user: ADMIN, tokens: { accessToken: 'a' } });

    // useSyncExternalStore 会在每次渲染时调用它，返回新对象会陷入无限重渲染
    expect(session.getSnapshot()).toBe(session.getSnapshot());

    const before = session.getSnapshot();
    session.updateTokens({ accessToken: 'a2' });
    expect(session.getSnapshot()).not.toBe(before);
  });
});
