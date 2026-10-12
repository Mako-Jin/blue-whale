import { describe, expect, it, vi } from 'vitest';

import { createMemoryStorage, createSessionStore } from 'common-tools/session';
import type { HttpClient } from 'common-tools/http';

import { createAuthApi, toExpiresAt, toSessionTokens, toSessionUser } from '../src/auth';
import { createAuthChannel } from '../src/channel';

/* ------------------------------------------------------------------ 替身 */

/** 记录调用的 http 替身：只用来验证"契约接线"这层，不碰真实请求 */
function recordingHttp(results: Record<string, unknown> = {}) {
  const calls: Array<{ path: string; body?: unknown; options?: unknown }> = [];
  const http = {
    baseUrl: '',
    request: vi.fn(),
    get: (path: string, options?: unknown) => {
      calls.push({ path, options });
      return Promise.resolve(results[path]);
    },
    post: (path: string, body?: unknown, options?: unknown) => {
      calls.push({ path, body, options });
      return Promise.resolve(results[path]);
    },
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  } as unknown as HttpClient;
  return { http, calls };
}

/** 按 URL 尾段路由的 fetch 替身，用于端到端串起"契约 + 会话 + 传输钩子" */
function routedFetch(routes: Record<string, { status?: number; body?: string }>) {
  const calls: Array<{ path: string; method: string; authorization?: string; body?: string }> = [];
  const impl = vi.fn(async (url: string, init?: RequestInit) => {
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const authKey = Object.keys(headers).find((name) => name.toLowerCase() === 'authorization');
    calls.push({
      path: url,
      method: (init?.method as string) ?? 'GET',
      authorization: authKey ? headers[authKey] : undefined,
      body: typeof init?.body === 'string' ? init.body : undefined,
    });

    const route = Object.entries(routes).find(([suffix]) => url.endsWith(suffix));
    if (!route) throw new Error(`未配置的请求：${url}`);
    const { status = 200, body } = route[1];
    return {
      status,
      statusText: '',
      ok: status >= 200 && status < 300,
      headers: { get: () => null },
      text: async () => body ?? '',
    } as unknown as Response;
  }) as unknown as typeof fetch;
  return { impl, calls };
}

function envelope(data: unknown, code = 0): string {
  return JSON.stringify({ code, message: 'ok', data, timestamp: 1 });
}

/** 一份登录/刷新返回体（字段与后端 LoginVO 对齐） */
const LOGIN_RESULT = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 1800,
  userId: 1,
  username: 'admin',
  displayName: '管理员',
  tenantId: 1,
  tenantCode: 'default',
  roles: ['ADMIN'],
  permissions: ['auth:user:read', 'auth:user:create'],
};

const silent = { logger: null } as const;

/* ------------------------------------------------------------------ 契约接线 */

describe('createAuthApi · 契约接线', () => {
  it('四条认证接口分别打到契约里的路径，登出与刷新都豁免自动恢复', async () => {
    const { http, calls } = recordingHttp({ '/api/auth/me': { userId: 1 } });
    const api = createAuthApi(http);

    await api.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });
    await api.refresh({ refreshToken: 'r' });
    await api.logout({ refreshToken: 'r' });
    await api.me();

    expect(calls.map((call) => call.path)).toEqual([
      '/api/auth/login',
      '/api/auth/refresh',
      '/api/auth/logout',
      '/api/auth/me',
    ]);
    // 刷新失败会 401 —— 必须豁免自动恢复，否则递归刷新
    expect(calls[1].options).toMatchObject({ skipAuth: true, skipAuthRetry: true });
    // 登录请求同样不带令牌（登录时本来也没有）
    expect(calls[0].options).toMatchObject({ skipAuth: true, skipAuthRetry: true });
    // 登出带上令牌（便于服务端审计"谁登出了"），但不参与自动恢复
    expect(calls[2].options).toMatchObject({ skipAuthRetry: true });
  });
});

describe('身份映射', () => {
  it('从返回体收敛出会话身份，权限集合原样搬过来', () => {
    expect(toSessionUser(LOGIN_RESULT)).toEqual({
      userId: 1,
      username: 'admin',
      displayName: '管理员',
      tenantId: 1,
      tenantCode: 'default',
      roles: ['ADMIN'],
      permissions: ['auth:user:read', 'auth:user:create'],
    });
  });

  it('字段缺席时补空值，不让一次登录因为缺字段整体失败', () => {
    expect(toSessionUser({})).toEqual({
      userId: '',
      username: '',
      displayName: undefined,
      tenantId: undefined,
      tenantCode: undefined,
      roles: [],
      permissions: [],
    });
  });

  it('令牌对去掉可能缺席的字段，并把有效期换算成到期时刻', () => {
    const tokens = toSessionTokens(LOGIN_RESULT);
    expect(tokens.accessToken).toBe('access-1');
    expect(tokens.refreshToken).toBe('refresh-1');
    expect(tokens.expiresAt).toBeGreaterThan(Date.now());

    // 算不出来时给 null，而不是编一个时刻
    expect(toExpiresAt({})).toBeNull();
  });
});

/* ------------------------------------------------------------------ 会话通道 */

function createChannel(routes: Record<string, { status?: number; body?: string }>) {
  const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
  const { impl, calls } = routedFetch(routes);
  const onAuthLost = vi.fn();
  const channel = createAuthChannel({ ...silent, session, fetchImpl: impl, onAuthLost });
  return { channel, session, calls, onAuthLost };
}

describe('createAuthChannel · 登录与登出', () => {
  it('登录成功后建立会话（令牌 + 身份 + 到期时刻）', async () => {
    const { channel, session } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
    });

    const user = await channel.login({
      tenantCode: 'default',
      username: 'admin',
      password: 'pwd',
    });

    expect(user.username).toBe('admin');
    expect(session.isAuthenticated()).toBe(true);
    expect(session.getAccessToken()).toBe('access-1');
    expect(session.hasPermission('auth:user:create')).toBe(true);
  });

  it('登出先撤销服务端令牌，再清空本地会话', async () => {
    const { channel, session, calls } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      '/api/auth/logout': { status: 204 },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await channel.logout();

    expect(calls.some((call) => call.path.endsWith('/api/auth/logout'))).toBe(true);
    expect(session.isAuthenticated()).toBe(false);
  });

  it('撤销失败也要清空本地会话（否则界面还在登录态、令牌却已不可用）', async () => {
    const { channel, session } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      '/api/auth/logout': { status: 500, body: 'boom' },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await expect(channel.logout()).resolves.toBeUndefined();

    expect(session.isAuthenticated()).toBe(false);
  });

  it('没有刷新令牌时不发登出请求，但本地照样清空', async () => {
    const { channel, session, calls } = createChannel({});
    session.set({ user: toSessionUser(LOGIN_RESULT), tokens: { accessToken: 'a' } });

    await channel.logout();

    expect(calls.filter((call) => call.path.endsWith('/api/auth/logout'))).toHaveLength(0);
    expect(session.isAuthenticated()).toBe(false);
  });
});

describe('createAuthChannel · 会话恢复', () => {
  it('没有持久化刷新令牌时直接返回 null，不发请求', async () => {
    const { channel, calls } = createChannel({});

    await expect(channel.restore()).resolves.toBeNull();
    expect(calls).toHaveLength(0);
  });

  it('用持久化的刷新令牌换新令牌对并恢复身份（一次请求，不必再调 /me）', async () => {
    const { channel, session, calls } = createChannel({
      '/api/auth/refresh': { body: envelope({ ...LOGIN_RESULT, accessToken: 'access-2' }) },
    });
    // 模拟"刷新页面"：只剩持久化的刷新令牌
    session.updateTokens({ refreshToken: 'refresh-1' });

    const user = await channel.restore();

    expect(user?.username).toBe('admin');
    expect(session.getAccessToken()).toBe('access-2');
    expect(session.getRefreshToken()).toBe('refresh-1');
    expect(calls).toHaveLength(1);
  });

  it('恢复失败时清空会话并返回 null（过期令牌不该留在存储里反复重试）', async () => {
    const { channel, session } = createChannel({
      '/api/auth/refresh': { status: 401, body: envelope(null, 40101) },
    });
    session.updateTokens({ refreshToken: 'stale' });

    await expect(channel.restore()).resolves.toBeNull();
    expect(session.getRefreshToken()).toBeNull();
  });

  it('reload 只重新拉取身份与权限，不动令牌', async () => {
    const { channel, session } = createChannel({
      '/api/auth/me': {
        body: envelope({ ...LOGIN_RESULT, permissions: ['auth:user:read'] }),
      },
    });
    session.set({
      user: toSessionUser(LOGIN_RESULT),
      tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
    });

    await channel.reload();

    expect(session.getAccessToken()).toBe('access-1');
    expect(session.hasPermission('auth:user:create')).toBe(false);
  });
});

describe('createAuthChannel · 401 恢复串联（契约 + 会话 + 传输钩子）', () => {
  /**
   * 脚本化 fetch：每条规则按顺序生效、可限定次数。
   *
   * <p>比"按 URL 固定响应"更能表达真实时序 —— 这里要验证的正是
   * **同一个接口先 401、刷新之后重试才成功**。
   */
  function scriptedFetch(
    rules: Array<{ match: string; times?: number; status?: number; body?: string }>,
  ) {
    const used = new Map<number, number>();
    const calls: Array<{ path: string; authorization?: string }> = [];
    const impl = vi.fn(async (url: string, init?: RequestInit) => {
      const headers = (init?.headers ?? {}) as Record<string, string>;
      const authKey = Object.keys(headers).find((name) => name.toLowerCase() === 'authorization');
      calls.push({ path: url, authorization: authKey ? headers[authKey] : undefined });

      const index = rules.findIndex((rule, position) => {
        if (!url.endsWith(rule.match)) return false;
        return (used.get(position) ?? 0) < (rule.times ?? Number.POSITIVE_INFINITY);
      });
      if (index < 0) throw new Error(`未配置的请求（或规则已用尽）：${url}`);
      used.set(index, (used.get(index) ?? 0) + 1);
      const { status = 200, body } = rules[index];
      return {
        status,
        statusText: '',
        ok: status >= 200 && status < 300,
        headers: { get: () => null },
        text: async () => body ?? '',
      } as unknown as Response;
    }) as unknown as typeof fetch;
    return { impl, calls };
  }

  function createLiveChannel(
    rules: Array<{ match: string; times?: number; status?: number; body?: string }>,
  ) {
    const session = createSessionStore({ ...silent, storage: createMemoryStorage() });
    const { impl, calls } = scriptedFetch(rules);
    const onAuthLost = vi.fn();
    const channel = createAuthChannel({ ...silent, session, fetchImpl: impl, onAuthLost });
    session.set({
      user: toSessionUser(LOGIN_RESULT),
      tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
    });
    return { channel, session, calls, onAuthLost };
  }

  it('业务请求 401 时自动刷新并重试，调用方只看到成功结果', async () => {
    const { channel, session, calls } = createLiveChannel([
      { match: '/api/logs/client', times: 1, status: 401, body: envelope(null, 40101) },
      { match: '/api/auth/refresh', body: envelope({ ...LOGIN_RESULT, accessToken: 'access-2' }) },
      { match: '/api/logs/client', body: envelope({ records: [] }) },
    ]);

    await expect(channel.api.log.page()).resolves.toEqual({ records: [] });

    expect(calls.map((call) => call.path.replace(/^.*\/api/, '/api'))).toEqual([
      '/api/logs/client',
      '/api/auth/refresh',
      '/api/logs/client',
    ]);
    expect(session.getAccessToken()).toBe('access-2');
  });

  it('刷新失败时会话被清空，并由 onAuthLost 通知界面跳登录页', async () => {
    const { channel, session, onAuthLost } = createLiveChannel([
      { match: '/api/logs/client', status: 401, body: envelope(null, 40101) },
      { match: '/api/auth/refresh', status: 401, body: envelope(null, 40101) },
    ]);

    await expect(channel.api.log.page()).rejects.toMatchObject({ status: 401 });

    expect(onAuthLost).toHaveBeenCalledTimes(1);
    expect(session.isAuthenticated()).toBe(false);
  });

  it('刷新请求自身不带令牌，且只发生一次', async () => {
    const { channel, calls } = createLiveChannel([
      { match: '/api/logs/client', times: 1, status: 401, body: envelope(null, 40101) },
      { match: '/api/auth/refresh', body: envelope(LOGIN_RESULT) },
      { match: '/api/logs/client', body: envelope({ records: [] }) },
    ]);

    await channel.api.log.page();

    const refreshCalls = calls.filter((call) => call.path.endsWith('/api/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
    expect(refreshCalls[0].authorization).toBeUndefined();
  });

  it('并发两个请求同时 401 时只刷新一次（轮换 + 重放检测下这是正确性要求）', async () => {
    const { channel, session, calls } = createLiveChannel([
      { match: '/api/logs/client', times: 1, status: 401, body: envelope(null, 40101) },
      { match: '/api/health', times: 1, status: 401, body: envelope(null, 40101) },
      { match: '/api/auth/refresh', body: envelope({ ...LOGIN_RESULT, accessToken: 'access-2' }) },
      { match: '/api/logs/client', body: envelope({ records: [] }) },
      { match: '/api/health', body: envelope({ status: 'UP' }) },
    ]);

    await Promise.all([channel.api.log.page(), channel.api.system.health()]);

    expect(calls.filter((call) => call.path.endsWith('/api/auth/refresh'))).toHaveLength(1);
    expect(session.getAccessToken()).toBe('access-2');
  });
});
