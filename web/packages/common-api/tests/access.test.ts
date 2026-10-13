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

  it('用户角色反查打到 /users/{id}/roles', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.users.roleSources(7);

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/users/7/roles',
    ]);
  });

  it('资源目录六个操作分别打到契约里的路径（平铺与树是两条路径）', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.resources.page({ resourceType: 'API' });
    await api.resources.tree();
    await api.resources.detail(2);
    await api.resources.create({
      resourceCode: 'auth:user:create',
      resourceName: '新建用户',
      resourceType: 'API',
    });
    await api.resources.update(2, {
      resourceCode: 'auth:user:create',
      resourceName: '改名',
      resourceType: 'API',
    });
    await api.resources.remove(2);

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/resources',
      'GET /api/auth/resources/tree',
      'GET /api/auth/resources/2',
      'POST /api/auth/resources',
      'PUT /api/auth/resources/2',
      'DELETE /api/auth/resources/2',
    ]);
  });

  it('角色十一个操作分别打到契约里的路径，且撤销成员**带 memberType**', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.roles.page({ keyword: 'adm' });
    await api.roles.detail(9);
    await api.roles.create({ roleCode: 'DEPT_MANAGER', roleName: '部门经理' });
    await api.roles.update(9, { roleCode: 'DEPT_MANAGER', roleName: '部门经理 2' });
    await api.roles.remove(9);
    await api.roles.listGrants(9);
    await api.roles.grant(9, { resourceId: 12 });
    await api.roles.revoke(9, { resourceId: 12 });
    await api.roles.pageMembers(9, { page: 1, size: 10 });
    await api.roles.bindMember(9, { memberType: 'USER', memberId: 7 });
    await api.roles.revokeMember(9, { memberType: 'USER', memberId: 7 });

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/roles',
      'GET /api/auth/roles/9',
      'POST /api/auth/roles',
      'PUT /api/auth/roles/9',
      'DELETE /api/auth/roles/9',
      'GET /api/auth/roles/9/resources',
      'POST /api/auth/roles/9/resources',
      // 撤销收的是**资源主键**（契约如此），不是授权关系主键
      'DELETE /api/auth/roles/9/resources/12',
      'GET /api/auth/roles/9/members',
      'POST /api/auth/roles/9/members',
      /*
       * `memberType` 必须在路径里。
       *
       * 用户与用户组是两个独立的自增序列、**会撞号** —— 少了这一段，
       * `memberId = 3` 撤掉的可能是另一个对象，而症状是"某人的权限凭空消失"，
       * 离真正的原因非常远。这条断言钉的就是"不许把它省掉"。
       */
      'DELETE /api/auth/roles/9/members/USER/7',
    ]);
  });

  it('用户组八个操作分别打到契约里的路径（路径连字符、成员关系挂组下）', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.userGroups.page({ enabled: 1 });
    await api.userGroups.detail(5);
    await api.userGroups.create({ groupCode: 'FIN_GROUP', groupName: '财务部' });
    await api.userGroups.update(5, { groupCode: 'FIN_GROUP', groupName: '财务部 2' });
    await api.userGroups.remove(5);
    await api.userGroups.pageMembers(5, { page: 1, size: 10 });
    await api.userGroups.bindMember(5, { memberType: 'USER', memberId: 7 });
    await api.userGroups.revokeMember(5, { memberType: 'USER', memberId: 7 });

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      // 路径是连字符 `user-groups`，权限码却是下划线 `auth:user_group:*` —— 全仓唯一一处两处不同形
      'GET /api/auth/user-groups',
      'GET /api/auth/user-groups/5',
      'POST /api/auth/user-groups',
      'PUT /api/auth/user-groups/5',
      'DELETE /api/auth/user-groups/5',
      'GET /api/auth/user-groups/5/members',
      'POST /api/auth/user-groups/5/members',
      'DELETE /api/auth/user-groups/5/members/USER/7',
    ]);
  });

  it('组织十一个操作分别打到契约里的路径（移动是独立路径，不在 update 里）', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.organizations.page({ keyword: '研发', parentId: 2 });
    await api.organizations.tree();
    await api.organizations.detail(4);
    await api.organizations.create({ orgCode: 'RD_FE', orgName: '前端组', parentId: 2 });
    await api.organizations.update(4, { orgCode: 'RD_FE', orgName: '前端组·改' });
    await api.organizations.move(4, { parentId: 1 });
    await api.organizations.remove(4);
    await api.organizations.pageMembers(4, { primaryOnly: true });
    await api.organizations.bindMember(4, { memberId: 7 });
    await api.organizations.setPrimaryPosition(4, { userId: 7 });
    await api.organizations.revokeMember(4, { userId: 7 });

    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/orgs',
      'GET /api/auth/orgs/tree',
      'GET /api/auth/orgs/4',
      'POST /api/auth/orgs',
      /*
       * 移动走 `/parent` 这条独立路径。
       *
       * 不把它折进 `PUT /orgs/4` 的请求体：那一次会重写整棵子树的派生列、
       * 并同时维护两个父级的 `hasChild` —— 与"改资料"不是一件事。
       * 折进去之后，"这次改的是什么"只能靠对比前后值猜，审计也只剩一条 `auth.org.update`。
       */
      'PUT /api/auth/orgs/4',
      'PUT /api/auth/orgs/4/parent',
      'DELETE /api/auth/orgs/4',
      'GET /api/auth/orgs/4/members',
      'POST /api/auth/orgs/4/members',
      // 设主岗没有请求体：目标就是路径上的这两个 id
      'PUT /api/auth/orgs/4/members/7/primary',
      // 撤销路径里**没有** memberType —— 组织只有用户这一种成员，不存在"同号不同表"
      'DELETE /api/auth/orgs/4/members/7',
    ]);
  });

  it('组织树不传 rootId 时不拼查询串（空值由内核丢掉，语义是"整片森林"）', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.organizations.tree();
    await api.organizations.tree(3);

    /*
     * 两次都走同一条路径，区别只在 query 上。
     *
     * 这里刻意断言 `options.query.rootId` 是 `undefined` 而**不是不传 query** ——
     * 内核会把空值丢掉（`buildQuery` 里 `undefined` / `null` / `''` 都跳过），
     * 因此"不传 rootId"与"传 undefined"在线上是同一个请求。
     * 断言得这么细，是为了钉住"前端不在这里补默认值"：补一个默认根，
     * 就会把"看不到别的树"伪装成"只有这一棵树"。
     */
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/orgs/tree',
      'GET /api/auth/orgs/tree',
    ]);
    expect(calls[0].options).toEqual({ query: { rootId: undefined } });
    expect(calls[1].options).toEqual({ query: { rootId: 3 } });
  });

  it('用户组织归属与数据范围反查分别打到两条子路径', async () => {
    const { http, calls } = recordingHttp();
    const api = createAccessApi(http);

    await api.users.organizations(7);
    await api.users.dataScope(7);

    /*
     * 两条路径都在 `/users/{id}` 下，但**性质完全不同**：
     * 前者是配置（逐条、带状态、不过滤），后者是一次求值的结果（唯一、必然自洽）。
     * 界面上它们的入口也因此分开：一个回答"为什么"，一个回答"是什么"。
     */
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      'GET /api/auth/users/7/organizations',
      'GET /api/auth/users/7/data-scope',
    ]);
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
      RESOURCE_READ: 'auth:resource:read',
      RESOURCE_CREATE: 'auth:resource:create',
      RESOURCE_UPDATE: 'auth:resource:update',
      RESOURCE_DELETE: 'auth:resource:delete',
      ROLE_READ: 'auth:role:read',
      ROLE_CREATE: 'auth:role:create',
      ROLE_UPDATE: 'auth:role:update',
      ROLE_DELETE: 'auth:role:delete',
      // 注意是**下划线** —— 后端标识的字符集被 AuthTaxonomyTest 钉成 [a-z][a-z0-9_]*
      USER_GROUP_READ: 'auth:user_group:read',
      USER_GROUP_CREATE: 'auth:user_group:create',
      USER_GROUP_UPDATE: 'auth:user_group:update',
      USER_GROUP_DELETE: 'auth:user_group:delete',
      // 资源段是缩写 `org`（路径写 `orgs`），同样是"两处不同形"的一处
      ORG_READ: 'auth:org:read',
      ORG_CREATE: 'auth:org:create',
      ORG_UPDATE: 'auth:org:update',
      ORG_DELETE: 'auth:org:delete',
    });
  });

  /**
   * **移动组织**与**设主岗**都没有单设权限码，复用 `auth:org:update`。
   *
   * <p>与角色成员关系同一条理由：能移动组织的人本来就能改组织，能设主岗的人
   * 本来就能挂成员。真拆出 `auth:org:move`，已经配好的角色会在升级后
   * **静默失去**移动能力 —— 而补救方向会指向"新权限没下发"。
   *
   * <p>但**审计动作是分开的**（`auth.org.move` / `auth.org.set_primary_position`），
   * 所以"谁把部门搬走了"在审计里查得到，不因为共用一个权限码而糊掉。
   * 界面同理：权限码不拆，入口必须是两个。
   */
  it('移动组织与设主岗复用 *:update，不单设权限码', () => {
    const codes: string[] = Object.values(AccessPermissions);
    expect(codes.filter((code) => code.includes('move'))).toEqual([]);
    expect(codes.filter((code) => code.includes('primary'))).toEqual([]);
  });

  /**
   * 成员关系的授予 / 撤销**刻意没有独立的权限码**，复用 `*:update`。
   *
   * <p>这条断言是"防止有人好心补上"：给它们加 `auth:role:grant_member` 之类的码，
   * 会让已经配好的角色在升级后**静默丢失**授予成员的能力 ——
   * 而补救方向会指向"新权限没下发"。真要拆，必须是一次带数据迁移的决定。
   */
  it('成员关系的授予 / 撤销复用 *:update，不单设权限码', () => {
    const codes: string[] = Object.values(AccessPermissions);
    expect(codes.filter((code) => code.includes('grant'))).toEqual([]);
    expect(codes.filter((code) => code.includes('member'))).toEqual([]);
  });
});
