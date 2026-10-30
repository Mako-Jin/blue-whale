import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createAuthStorage } from './createAuthStorage';

/** 测试用 localStorage 内存实现（纯库单测不引入 jsdom） */
const storage = new Map<string, string>();

function installLocalStorage(): void {
  const stub = {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value);
    },
    removeItem: (key: string) => {
      storage.delete(key);
    },
    clear: () => {
      storage.clear();
    },
    key: (index: number) => Array.from(storage.keys())[index] ?? null,
    get length() {
      return storage.size;
    },
  };
  (globalThis as unknown as { localStorage: typeof stub }).localStorage = stub;
}

describe('createAuthStorage', () => {
  beforeEach(() => {
    storage.clear();
    installLocalStorage();
  });

  it('accessToken 只存内存，不落 localStorage', () => {
    const auth = createAuthStorage({ accessTokenKey: 'k1_token', userKey: 'k1_user' });

    auth.setToken('access-token');

    expect(auth.getToken()).toBe('access-token');
    expect(storage.has('k1_token')).toBe(false);
    expect([...storage.keys()]).toEqual([]);
  });

  it('不同应用 key 的内存令牌互相隔离', () => {
    const a = createAuthStorage({ accessTokenKey: 'app_a_token', userKey: 'app_a_user' });
    const b = createAuthStorage({ accessTokenKey: 'app_b_token', userKey: 'app_b_user' });

    a.setToken('token-a');

    expect(a.getToken()).toBe('token-a');
    expect(b.getToken()).toBeNull();
  });

  it('用户信息落 localStorage 并可读回', () => {
    const auth = createAuthStorage({ accessTokenKey: 'k2_token', userKey: 'k2_user' });

    auth.saveCurrentUser({ username: 'admin', nickname: '管理员', roles: ['ADMIN'] });

    expect(auth.getCurrentUser()).toEqual({ username: 'admin', nickname: '管理员', roles: ['ADMIN'] });
    expect(storage.get('k2_user')).toContain('admin');
  });

  it('损坏的用户缓存视为未登录', () => {
    const auth = createAuthStorage({ accessTokenKey: 'k3_token', userKey: 'k3_user' });
    storage.set('k3_user', '{not-json');

    expect(auth.getCurrentUser()).toBeNull();
  });

  it('saveCurrentUser 与 clearAuth 都会通知订阅者，退订后不再通知', () => {
    const auth = createAuthStorage({ accessTokenKey: 'k4_token', userKey: 'k4_user' });
    const listener = vi.fn();
    const unsubscribe = auth.subscribeCurrentUser(listener);

    auth.saveCurrentUser({ username: 'u1' });
    auth.clearAuth();
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    auth.saveCurrentUser({ username: 'u2' });
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('clearAuth 同时清除内存令牌与用户缓存', () => {
    const auth = createAuthStorage({ accessTokenKey: 'k5_token', userKey: 'k5_user' });
    auth.setToken('tok');
    auth.saveCurrentUser({ username: 'admin' });

    auth.clearAuth();

    expect(auth.getToken()).toBeNull();
    expect(auth.getCurrentUser()).toBeNull();
    expect(storage.has('k5_user')).toBe(false);
  });
});
