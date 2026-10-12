import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  auth,
  ensureSessionRestored,
  getRestorePhase,
  resetAuthForTest,
  session,
  subscribeRestorePhase,
} from './index';

/** 造一个只够 http 内核使用的响应替身（与 common-api 测试里的做法一致） */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    statusText: '',
    headers: { get: () => null },
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const ENVELOPE = (code: number, message: string, data: unknown = null) => ({ code, message, data });

describe('会话恢复阶段', () => {
  beforeEach(() => {
    resetAuthForTest();
    session.clear();
    localStorage.clear();
  });

  it('没有刷新令牌时直接结束恢复，不停在中间态', async () => {
    await ensureSessionRestored();

    expect(getRestorePhase()).toBe('done');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('阶段推进时通知订阅者（界面据此决定显示"恢复中"还是"跳登录页"）', async () => {
    const phases: string[] = [];
    subscribeRestorePhase(() => phases.push(getRestorePhase()));

    await ensureSessionRestored();

    expect(phases).toEqual(['restoring', 'done']);
  });

  it('重复调用共用同一次恢复', async () => {
    const [first, second] = await Promise.all([ensureSessionRestored(), ensureSessionRestored()]);

    expect(first).toBe(second);
    expect(getRestorePhase()).toBe('done');
  });

  it('恢复失败也会推进到 done 并清空会话（否则界面永远停在"正在恢复会话"）', async () => {
    session.updateTokens({ refreshToken: 'stale-token' });
    // 刷新接口返回业务失败：该刷新令牌已不可用
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(ENVELOPE(40101, '会话已失效，请重新登录'))),
    );

    await ensureSessionRestored();

    expect(getRestorePhase()).toBe('done');
    expect(session.isAuthenticated()).toBe(false);
    expect(session.getRefreshToken()).toBeNull();
    vi.unstubAllGlobals();
  });

  it('恢复成功后会话里就有身份与权限（权限不在访问令牌里，必须靠这次请求拿回来）', async () => {
    session.updateTokens({ refreshToken: 'good-token' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          ENVELOPE(0, 'ok', {
            accessToken: 'access-2',
            refreshToken: 'refresh-2',
            expiresIn: 7200,
            userId: 1,
            username: 'admin',
            displayName: '系统管理员',
            roles: ['SUPER_ADMIN'],
            permissions: ['auth:user:create'],
          }),
        ),
      ),
    );

    await ensureSessionRestored();

    expect(getRestorePhase()).toBe('done');
    expect(session.getSnapshot().user?.roles).toEqual(['SUPER_ADMIN']);
    expect(session.hasPermission('auth:user:create')).toBe(true);
    // 令牌被轮换：持久化的是新的刷新令牌，旧的已作废
    expect(session.getRefreshToken()).toBe('refresh-2');
    vi.unstubAllGlobals();
  });

  it('恢复走的是刷新接口，而不是再调一次 /me', async () => {
    session.updateTokens({ refreshToken: 'good-token' });
    const fetchMock = vi.fn(async () =>
      jsonResponse(
        ENVELOPE(0, 'ok', {
          accessToken: 'a',
          refreshToken: 'b',
          expiresIn: 7200,
          userId: 1,
          username: 'admin',
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await ensureSessionRestored();

    const calls = fetchMock.mock.calls as unknown as [string][];
    expect(calls).toHaveLength(1);
    expect(calls[0][0]).toContain('/api/auth/refresh');
    vi.unstubAllGlobals();
  });

  it('恢复阶段与登录共用同一份会话与通道（基座只装配，不重写登录逻辑）', () => {
    expect(auth.session).toBe(session);
  });
});
