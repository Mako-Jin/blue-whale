import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 资源 / 角色 / 用户组三个管理页的行为断言。
 *
 * <p>与 `app.test.tsx` 同一套替身策略：**接口客户端换掉、`AccessPermissions` 保留真身** ——
 * 页面里的权限标识来自它，换掉就等于把"标识拼错"这类问题一起藏起来。
 *
 * <p>这里刻意只断言**跨端约定**层面的东西（哪个按钮由哪个标识控制、
 * 哪个限制是"刻意不做"而不是"还没做"），不复述渲染细节：
 * 后者改一次布局就要修一次测试，而它并不能证明什么。
 */
const mocks = vi.hoisted(() => ({
  userPage: vi.fn(),
  userRoleSources: vi.fn(),
  tenantPage: vi.fn(),
  resourcePage: vi.fn(),
  resourceTree: vi.fn(),
  resourceCreate: vi.fn(),
  rolePage: vi.fn(),
  roleGrants: vi.fn(),
  roleGrant: vi.fn(),
  roleMembers: vi.fn(),
  roleBindMember: vi.fn(),
  groupPage: vi.fn(),
  groupMembers: vi.fn(),
  groupBindMember: vi.fn(),
}));

vi.mock('common-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('common-api')>();
  return {
    ...actual,
    createApiClient: () => ({
      http: {},
      auth: {},
      access: {
        users: {
          page: mocks.userPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          resetPassword: vi.fn(),
          remove: vi.fn(),
          roleSources: mocks.userRoleSources,
        },
        tenants: {
          page: mocks.tenantPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
        },
        resources: {
          page: mocks.resourcePage,
          tree: mocks.resourceTree,
          detail: vi.fn(),
          create: mocks.resourceCreate,
          update: vi.fn(),
          remove: vi.fn(),
        },
        roles: {
          page: mocks.rolePage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
          listGrants: mocks.roleGrants,
          grant: mocks.roleGrant,
          revoke: vi.fn(),
          pageMembers: mocks.roleMembers,
          bindMember: mocks.roleBindMember,
          revokeMember: vi.fn(),
        },
        userGroups: {
          page: mocks.groupPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
          pageMembers: mocks.groupMembers,
          bindMember: mocks.groupBindMember,
          revokeMember: vi.fn(),
        },
      },
    }),
  };
});

import App from '../src/App';
import { getStandaloneSession, initializeAuth } from '../src/auth';
import { connectPermissionSource } from '../src/auth/permissions';

const ADMIN = [
  'auth:user:read',
  'auth:resource:read',
  'auth:resource:create',
  'auth:resource:delete',
  'auth:role:read',
  'auth:role:update',
  'auth:user_group:read',
  'auth:user_group:update',
];

const USER_ROW = { id: 7, username: 'admin', displayName: '系统管理员', status: 'ENABLED' };
const ROLE_ROW = { id: 3, roleCode: 'SUPER_ADMIN', roleName: '超级管理员', status: 'ENABLED' };
const GROUP_ROW = { id: 5, groupCode: 'FIN_GROUP', groupName: '财务部', isEnabled: 1 };

const TREE = [
  {
    id: 1,
    resourceCode: 'auth',
    resourceName: '权限',
    resourceType: 'MODULE',
    requestScoped: false,
    isEnabled: 1,
    children: [
      {
        id: 2,
        resourceCode: 'auth:user:create',
        resourceName: '新建用户',
        resourceType: 'API',
        requestScoped: true,
        parentCode: 'auth',
        isEnabled: 1,
      },
    ],
  },
];

function signIn(permissions: string[]): void {
  getStandaloneSession().set({
    user: { userId: 7, username: 'tester', roles: ['SUPER_ADMIN'], permissions },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

let disconnect: () => void;

describe('权限管理后台 · 资源 / 角色 / 用户组', () => {
  beforeEach(() => {
    localStorage.clear();
    getStandaloneSession().clear();
    initializeAuth();
    disconnect = connectPermissionSource();

    for (const mock of Object.values(mocks)) mock.mockReset();

    mocks.userPage.mockResolvedValue({
      records: [USER_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.tenantPage.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.resourceTree.mockResolvedValue(TREE);
    mocks.resourcePage.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.rolePage.mockResolvedValue({
      records: [ROLE_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.roleGrants.mockResolvedValue([]);
    mocks.roleMembers.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
    mocks.groupPage.mockResolvedValue({
      records: [GROUP_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.groupMembers.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
  });

  it('资源页：树按 parentCode 的层级渲染，且不参与准入的节点被标出来', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('资源'));

    expect(await screen.findByTestId('resource-node-auth')).toBeInTheDocument();
    expect(screen.getByTestId('resource-node-auth:user:create')).toBeInTheDocument();
    // 派生字段来自后端（`requestScoped`），前端不重算 —— 重算就是第二份真相
    expect(screen.getAllByText('不参与准入').length).toBeGreaterThan(0);
  });

  it('资源页：只读账号看不到新建入口，但树照常可见', async () => {
    signIn(['auth:resource:read']);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('资源'));
    await screen.findByTestId('resource-node-auth');

    // 看得见 —— 否则就分不清是"没权限"还是"没数据"
    expect(screen.queryByText('新建资源')).not.toBeInTheDocument();
    expect(screen.queryByText('加子资源')).not.toBeInTheDocument();
  });

  it('资源页：标识形状不对时提交按钮直接不可用（少一次注定被拒的往返）', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('资源'));
    await screen.findByTestId('resource-node-auth');
    fireEvent.click(screen.getByText('新建资源'));

    const submit = screen.getByRole('button', { name: '保存' });
    fireEvent.change(screen.getByRole('textbox', { name: /资源标识/ }), {
      target: { value: 'Auth:User:Create' },
    });
    expect(submit).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: /资源标识/ }), {
      target: { value: 'auth:user:create' },
    });
    expect(submit).not.toBeDisabled();
  });

  it('角色页：资源授权对话框列出已授权，未选资源时不可提交', async () => {
    mocks.roleGrants.mockResolvedValue([
      {
        grantId: 11,
        resourceId: 2,
        resourceCode: 'auth:user:create',
        effect: 'ALLOW',
        requestScoped: true,
        effectiveNow: true,
      },
    ]);
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('资源授权'));

    expect(await screen.findByText('资源授权 · SUPER_ADMIN')).toBeInTheDocument();
    expect(await screen.findByText('auth:user:create')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '授予' })).toBeDisabled();
  });

  it('角色页：成员类型默认不选 —— 不给默认值，避免把"没选"变成"授给同号用户"', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('角色'));
    await screen.findByTestId('role-row-SUPER_ADMIN');
    fireEvent.click(screen.getByText('成员'));

    expect(await screen.findByText('成员 · SUPER_ADMIN')).toBeInTheDocument();

    const typeSelect = screen.getByRole('combobox', { name: /成员类型/ });
    expect((typeSelect as HTMLSelectElement).value).toBe('');
    expect(screen.getByRole('button', { name: '授予成员' })).toBeDisabled();

    // 选类型 → 成员下拉才有候选；选成员 → 提交才可用
    fireEvent.change(typeSelect, { target: { value: 'USER' } });
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /^成员$/ })).not.toBeDisabled();
    });
    fireEvent.change(screen.getByRole('combobox', { name: /^成员$/ }), {
      target: { value: '7' },
    });
    mocks.roleBindMember.mockResolvedValue({ grantId: 1, memberType: 'USER', memberId: 7 });
    fireEvent.click(screen.getByRole('button', { name: '授予成员' }));

    await waitFor(() => {
      expect(mocks.roleBindMember).toHaveBeenCalledWith(3, {
        memberType: 'USER',
        memberId: 7,
        effectiveFrom: undefined,
        effectiveTo: undefined,
      });
    });
  });

  it('用户组页：成员表单没有"类型"下拉 —— 用户组只接受 USER，不提供能选错的开关', async () => {
    signIn(ADMIN);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    fireEvent.click(screen.getByText('用户组'));
    await screen.findByTestId('user-group-row-FIN_GROUP');
    fireEvent.click(screen.getByText('成员'));

    expect(await screen.findByText('组成员 · FIN_GROUP')).toBeInTheDocument();
    // "成员类型"这个标签只在角色那边存在（那边 USER / GROUP 都合法）
    expect(screen.queryByText('成员类型')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: /成员$/ }), { target: { value: '7' } });
    mocks.groupBindMember.mockResolvedValue({ grantId: 1, memberType: 'USER', memberId: 7 });
    fireEvent.click(screen.getByRole('button', { name: '加入用户组' }));

    await waitFor(() => {
      expect(mocks.groupBindMember).toHaveBeenCalledWith(5, {
        memberType: 'USER',
        memberId: 7,
        effectiveFrom: undefined,
        effectiveTo: undefined,
      });
    });
  });

  it('反查：只读账号也能查角色来源，且停用的角色照原样返回', async () => {
    mocks.userRoleSources.mockResolvedValue([
      {
        grantId: 21,
        source: 'DIRECT',
        sourceLabel: '直接授予',
        roleId: 9,
        roleCode: 'TEMP_REVIEWER',
        roleName: '临时评审',
        roleStatus: 'DISABLED',
        effectiveNow: true,
      },
    ]);
    // 只有用户读权限 —— 反查归 `auth:user:read`，因此这个按钮必须出现
    signIn(['auth:user:read']);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    expect(screen.getByText('角色来源')).toBeInTheDocument();
    fireEvent.click(screen.getByText('角色来源'));

    expect(await screen.findByTestId('role-source-TEMP_REVIEWER')).toBeInTheDocument();
    // 「停用」正是"为什么没有权限"的答案，过滤掉它就等于把答案删了
    expect(screen.getByText('停用')).toBeInTheDocument();
  });
});

/**
 * 先 `cleanup()` 再断开订阅 —— `afterEach` 逆序执行，
 * 而 testing-library 的自动清理是 import 时注册的，会**后于**这个回调跑；
 * 那时组件还在树上，断开触发的发布会落在 act 之外，产出一堆指向错误位置的警告。
 */
afterEach(() => {
  cleanup();
  disconnect();
});
