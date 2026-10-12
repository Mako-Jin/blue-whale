import { describe, expect, it, vi } from 'vitest';

import type { HttpClient } from 'common-tools/http';

import { AccessPermissions, createAccessApi } from '../src/access';

/**
 * 记录调用的 http 替身。
 *
 * <p>只验证"接线"这一层：哪个方法对应哪条路径、请求体怎么传。
 * 真实请求的形状由 `auth.test.ts` 里的端到端用例覆盖（那边串了契约 + 会话 + 传输钩子）。
 */
function recordingHttp() {
  const calls: Array<{ method: string; path: string; body?: unknown; options?: unknown }> = [];
  const http = {
    baseUrl: '',
    request: vi.fn(),
    get: (path: string, options?: unknown) => {
      calls.push({ method: 'GET', path, options });
      return Promise.resolve(undefined);
    },
    post: (path: string, body?: unknown, options?: unknown) => {
      calls.push({ method: 'POST', path, body, options });
      return Promise.resolve(undefined);
    },
    put: (path: string, body?: unknown, options?: unknown) => {
      calls.push({ method: 'PUT', path, body, options });
      return Promise.resolve(undefined);
    },
    patch: vi.fn(),
    delete: (path: string, options?: unknown) => {
      calls.push({ method: 'DELETE', path, options });
      return Promise.resolve(undefined);
    },
  } as unknown as HttpClient;
  return { http, calls };
}

describe('createAccessApi · 契约接线', () => {
  it('用户六个操作分别打到契约里的路径', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.users.page({ keyword: 'adm', page: 1, size: 10 });
    await api.users.detail(7);
    await api.users.create({ username: 'someone', password: 'Someone@123' });
    await api.users.update(7, { username: 'someone', displayName: '改名' });
    await api.users.resetPassword(7, { password: 'NewPass@123' });
    await api.users.remove(7);

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/users',
      'GET /api/auth/users/7',
      'POST /api/auth/users',
      'PUT /api/auth/users/7',
      'POST /api/auth/users/7/password',
      'DELETE /api/auth/users/7',
    ]);

    // 查询条件整包透传：分页与过滤由服务端的 PageQuery / UserQuery 负责钳制，
    // 前端不再抄一遍默认值与上限（抄了就会与后端悄悄分叉）
    expect(calls[0].options).toEqual({
      query: { keyword: 'adm', page: 1, size: 10 },
    });
  });

  it('租户五个操作分别打到契约里的路径', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.tenants.page();
    await api.tenants.detail(3);
    await api.tenants.create({ tenantCode: 'acme', tenantName: 'Acme 集团' });
    await api.tenants.update(3, { tenantCode: 'acme', tenantName: 'Acme 2' });
    await api.tenants.remove(3);

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/tenants',
      'GET /api/auth/tenants/3',
      'POST /api/auth/tenants',
      'PUT /api/auth/tenants/3',
      'DELETE /api/auth/tenants/3',
    ]);
  });

  it('删除用户的方法名是 remove —— 调用点不该读起来像"删掉 users 这个集合"', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.users.remove(7);

    expect(calls[0].method).toBe('DELETE');
  });
});

describe('AccessPermissions · 权限标识', () => {
  /**
   * 逐个钉死字符串。
   *
   * <p>这不是同义反复：它是**唯一**能挡住"前端把权限码抄错"的护栏 ——
   * 抄成 `auth:user:ceate` 不会报错，只会让按钮永远不出现，
   * 而排查方向会先指向"权限没下发"（`docs/decisions/0018` 的后续约束）。
   * 后端 `AuthPermissions` 是这些字符串的来源，改后端时必须同时改这里。
   */
  it('与后端 AuthPermissions 逐字一致', () => {
    expect(AccessPermissions).toEqual({
      USER_READ: 'auth:user:read',
      USER_CREATE: 'auth:user:create',
      USER_UPDATE: 'auth:user:update',
      USER_DELETE: 'auth:user:delete',
      TENANT_READ: 'auth:tenant:read',
      TENANT_CREATE: 'auth:tenant:create',
      TENANT_UPDATE: 'auth:tenant:update',
      TENANT_DELETE: 'auth:tenant:delete',
    });
  });
});
