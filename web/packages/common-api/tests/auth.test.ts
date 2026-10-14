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
    put: (path: string, body?: unknown, options?: unknown) => {
      calls.push({ path, body, options });
      return Promise.resolve(results[path]);
    },
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

function envelope(data: unknown, code = 0, message = 'ok'): string {
  return JSON.stringify({ code, message, data, timestamp: 1 });
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
  it('六条认证接口分别打到契约里的路径，登出与刷新都豁免自动恢复', async () => {
    const { http, calls } = recordingHttp({ '/api/auth/me': { userId: 1 } });
    const api = createAuthApi(http);

    await api.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });
    await api.refresh({ refreshToken: 'r' });
    await api.logout({ refreshToken: 'r' });
    await api.logoutAll();
    await api.me();
    await api.changePassword({ oldPassword: 'pwd', newPassword: 'Next@1234' });

    expect(calls.map((call) => call.path)).toEqual([
      '/api/auth/login',
      '/api/auth/refresh',
      '/api/auth/logout',
      '/api/auth/logout-all',
      '/api/auth/me',
      '/api/auth/password',
    ]);
    // 刷新失败会 401 —— 必须豁免自动恢复，否则递归刷新
    expect(calls[1].options).toMatchObject({ skipAuth: true, skipAuthRetry: true });
    // 登录请求同样不带令牌（登录时本来也没有）
    expect(calls[0].options).toMatchObject({ skipAuth: true, skipAuthRetry: true });
    // 登出带上令牌（便于服务端审计"谁登出了"），但不参与自动恢复
    expect(calls[2].options).toMatchObject({ skipAuthRetry: true });
    // 登出的令牌是**必填**的：它是"撤哪一个会话"的主语。
    // 曾经它是可选的，而"不带"表示另一件事（撤销全部）—— 一次把新客户端
    // 写成"忘了传令牌"的失误，代价是该账号所有设备被踢下线（见 docs/decisions/0047）。
    expect(calls[2].body).toEqual({ refreshToken: 'r' });
    /*
     * 退出所有设备走**默认**选项，与登出刻意不同，两处都不是随手写的：
     *   1. 它撤的是"当前用户的全部"，服务端得先知道你是谁 ⇒ 必须带令牌（不能 skipAuth）；
     *   2. 它最常见的调用时机恰恰是"访问令牌刚过期、我怀疑账号被人登着"
     *      ⇒ 不能 skipAuthRetry，否则这个动作在最需要它的时候必然失败。
     */
    expect(calls[3].options).toBeUndefined();
    // 改密走默认选项：带令牌、401 时刷新重试 ——
    // "旧口令不对"在传输层是 200（后端业务失败一律返回 HTTP 200），内核不会误判
    expect(calls[5].options).toBeUndefined();
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
      mustChangePassword: false,
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
      // 缺席按"不需要改密"处理：反过来（`!== 0`）会让所有没这个字段的响应
      // 都弹一次无事可做的引导，而用户根本没有可改的东西
      mustChangePassword: false,
    });
  });

  it('待改密标记只有后端给 1 才算真（0 与缺席都是否）', () => {
    expect(toSessionUser({ ...LOGIN_RESULT, mustChangePassword: 1 }).mustChangePassword).toBe(true);
    expect(toSessionUser({ ...LOGIN_RESULT, mustChangePassword: 0 }).mustChangePassword).toBe(
      false,
    );
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

  /*
   * 「退出所有设备」与登出的失败处理**刻意相反**，下面两条是那条判据的可执行版。
   *
   * 判据是"本地清理能不能达成这次动作的意图"：
   *   - 登出的意图是"**我这一台**下线" —— 清掉本地就达成了，服务端那次撤销失败
   *     只意味着"那个令牌还留在库里"，用户感知不到差别；
   *   - 退出所有设备的意图是"**别处也要**下线" —— 清本地恰恰达不成它。
   *     吞掉失败再清本地，用户会带着"我已经把别人踢了"的错觉回到登录页，
   *     而别的设备还好端端地开着。那是一次静默的安全失败。
   */
  it('退出所有设备：打到契约路径，成功后清空本地会话', async () => {
    const { channel, session, calls } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      '/api/auth/logout-all': { status: 204 },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await channel.logoutAll();

    expect(calls.some((call) => call.path.endsWith('/api/auth/logout-all'))).toBe(true);
    expect(session.isAuthenticated()).toBe(false);
  });

  it('退出所有设备失败时抛给调用方，且**不清**本地会话 —— 这次失败用户感知得到', async () => {
    const { channel, session } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      '/api/auth/logout-all': { status: 500, body: 'boom' },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await expect(channel.logoutAll()).rejects.toThrow();

    // 停在原处：用户能直接再点一次，而不是被送到登录页、还以为什么都踢干净了
    expect(session.isAuthenticated()).toBe(true);
  });
});

describe('createAuthChannel · 自助改密', () => {
  it('打到契约路径，并在成功后结束本地会话（服务端已撤销全部刷新令牌）', async () => {
    const { channel, session, calls } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      '/api/auth/password': { body: envelope(null) },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await channel.changePassword('pwd', 'Next@1234');

    const change = calls.find((call) => call.path.endsWith('/api/auth/password'));
    expect(change?.method).toBe('PUT');
    // 顺带钉住两个位置参数没被调换：把旧口令填进新口令那一格，
    // 症状是"改密成功但口令没变"，而没有任何一处会报错
    expect(JSON.parse(change?.body ?? '{}')).toEqual({
      oldPassword: 'pwd',
      newPassword: 'Next@1234',
    });
    // 留着这个会话的后果很具体：服务端已经撤销了它的刷新令牌，
    // 访问令牌一到期就是一次"突然掉登录"—— 那时用户早已忘了自己刚改过口令
    expect(session.isAuthenticated()).toBe(false);
  });

  it('旧口令不对（40101）时**不**结束会话 —— 一次输错不该变成一次登出', async () => {
    const { channel, session } = createChannel({
      '/api/auth/login': { body: envelope(LOGIN_RESULT) },
      /*
       * 响应形态必须照**真实**的造：业务失败一律返回 HTTP 200 + 业务码
       * （本仓 `GlobalExceptionHandler` 的约定，`ChangePasswordApiTest`
       * 里那句 `andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40101))`
       * 就是它）。
       *
       * 这里曾经写成 `status: 401` —— 那是替身的属性而不是系统的属性。
       * 用它会"测出"一个不存在的缺陷（详见 ADR-0044 背景第二节）。
       */
      '/api/auth/password': { status: 200, body: envelope(null, 40101) },
    });
    await channel.login({ tenantCode: 'default', username: 'admin', password: 'pwd' });

    await expect(channel.changePassword('wrong', 'Next@1234')).rejects.toMatchObject({
      code: 40101,
    });

    // 兜住它的正是那条"业务失败返 HTTP 200"的约定：`40101` 不会被内核
    // 当成令牌失效，于是不会有"刷新 → 重试 → 清会话跳登录页"那一串。
    // 若哪天后端改成按业务码回真实状态码，这条断言会红 —— 那正是它要报的警。
    expect(session.isAuthenticated()).toBe(true);
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

  describe('会话被结束的原因', () => {
    /*
     * 这一小组盯的是"用户被请出去时，界面说不说得清为什么"。
     *
     * 会话失效有两种来路，用户感受到的完全不同：自己点登出（他知道发生了什么），
     * 或被服务端拒绝续期 —— 他正在做事，页面忽然回到登录页，而这条路径上
     * **没有任何用户动作**，所以原因只能来自服务端那句响应。
     */

    it('刷新被业务码拒绝时，把服务端那句话原样记下来；取一次即清空', async () => {
      const { channel, onAuthLost } = createLiveChannel([
        { match: '/api/logs/client', status: 401, body: envelope(null, 40101) },
        {
          match: '/api/auth/refresh',
          body: envelope(null, 40111, '口令已过期，请联系管理员重置口令'),
        },
      ]);

      await expect(channel.api.log.page()).rejects.toMatchObject({ status: 401 });
      expect(onAuthLost).toHaveBeenCalledTimes(1);

      // 文案由服务端给，前端不另写一套 —— 另写的那套会在后端改口径之后变成
      // 第二份过期的说法，而且它还是用户唯一看得到的那份。
      expect(channel.consumeAuthNotice()).toBe('口令已过期，请联系管理员重置口令');
      // 取一次即清空：它是"刚刚发生了什么"，不是一个会一直挂着的状态。
      // 留着的话，用户明天打开应用会迎面看到一句昨天的原因。
      expect(channel.consumeAuthNotice()).toBeNull();
    });

    it('服务端没给出结论时（5xx / 断网）不记原因 —— 那不是"会话失效"的证据', async () => {
      const { channel } = createLiveChannel([
        { match: '/api/logs/client', status: 401, body: envelope(null, 40101) },
        { match: '/api/auth/refresh', status: 500, body: 'boom' },
      ]);

      await expect(channel.api.log.page()).rejects.toMatchObject({ status: 401 });

      // 把传输层失败也当成原因，用户会以为"必须重新登录"，而实际上可能只是
      // 后端此刻不可用 —— 那是两件不同的事，兜底文案也不该由这次失败来定。
      expect(channel.consumeAuthNotice()).toBeNull();
    });

    it('启动时那次恢复失败不留原因 —— 用户还没进来过，没有"被中断"这回事', async () => {
      const { channel } = createLiveChannel([
        { match: '/api/auth/refresh', body: envelope(null, 40106, '会话已失效，请重新登录') },
      ]);

      await expect(channel.restore()).resolves.toBeNull();

      // 同一个失败走内核那条路会留下原因（见上面第一条），走启动恢复就不该留：
      // 不清掉的话，隔一周再打开应用会迎面看到一句"会话已失效"——
      // 话是对的，但它解释的是一件用户没有经历过的事。
      expect(channel.consumeAuthNotice()).toBeNull();
    });
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

  describe('会话级刷新合并（跨客户端）', () => {
    it('多个调用方（基座 + 若干子应用）同时要求刷新时只打一次刷新接口', async () => {
      const { channel, calls } = createLiveChannel([
        {
          match: '/api/auth/refresh',
          body: envelope({ ...LOGIN_RESULT, accessToken: 'access-2' }),
        },
      ]);

      // 内核对同一个客户端上的并发 401 已做合并，但看不见"不同客户端"的并发：
      // 基座自己的请求与经 HostAuthBridge 委托过来的子应用请求分属不同客户端，
      // 合并点因此必须在会话层（channel.reauthenticate）才成立。
      const results = await Promise.all([
        channel.reauthenticate(),
        channel.reauthenticate(),
        channel.reauthenticate(),
      ]);

      expect(results).toEqual([true, true, true]);
      expect(calls.filter((call) => call.path.endsWith('/api/auth/refresh'))).toHaveLength(1);
    });

    it('一次失败不留下已结算的 Promise，下一次仍会真的去刷新', async () => {
      const { channel, calls } = createLiveChannel([
        { match: '/api/auth/refresh', times: 1, status: 401, body: envelope(null, 40101) },
        {
          match: '/api/auth/refresh',
          body: envelope({ ...LOGIN_RESULT, accessToken: 'access-3' }),
        },
      ]);

      await expect(channel.reauthenticate()).resolves.toBe(false);
      // 若清位写在 then 里而不是 finally 里，这里会命中那个已经 settled 的 false ——
      // 表现为"刷新失败过一次之后，会话再也恢复不了，直到刷新页面"
      await expect(channel.reauthenticate()).resolves.toBe(true);

      expect(calls.filter((call) => call.path.endsWith('/api/auth/refresh'))).toHaveLength(2);
    });
  });
});
