import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AdminViews } from 'common-api';
import { HttpStatusError } from 'common-tools/http';
import type { SessionStore } from 'common-tools/session';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 接口客户端用替身。
 *
 * <p>`AccessPermissions` 必须保留真身 —— 页面里的权限标识就来自它，
 * 若把它也换掉，"标识拼错"这类问题会在测试里被一起掩盖。
 */
const mocks = vi.hoisted(() => ({
  userPage: vi.fn(),
  userCreate: vi.fn(),
  tenantPage: vi.fn(),
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
          create: mocks.userCreate,
          update: vi.fn(),
          resetPassword: vi.fn(),
          remove: vi.fn(),
        },
        tenants: {
          page: mocks.tenantPage,
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          remove: vi.fn(),
        },
      },
    }),
  };
});

import App from '../src/App';
import { consoleAuth } from '../src/auth';
import { connectPermissionSource } from '../src/auth/permissions';

/**
 * 独立运行时的会话 —— 测试环境里没有宿主桥，这条分支必然成立。
 *
 * <p>`createConsoleAuth` 的 `getStandaloneSession()` 返回 `SessionStore | null`
 * （被基座加载时是 `null`：那时会话在基座手里，本应用压根不该建）。
 * 测试里显式把它断言出来，而不是到处写 `!` —— 顺带把
 * "测试必须在'无宿主桥'的前提下跑"这条约束写在明面上。
 */
function standaloneSession(): SessionStore {
  const session = consoleAuth.getStandaloneSession();
  if (!session) {
    throw new Error('测试环境不应存在宿主认证桥：独立运行分支必然成立');
  }
  return session;
}

/**
 * 管理员：八项读写权限 **加上**六个标签页菜单码。
 *
 * <p>两类码缺一不可，而且缺了的症状完全不同 —— 少了读写权限，标签页在、按钮不在；
 * 少了菜单码，标签页本身就不出现。这个文件里两类用例都有，正是为了让这个区别留在测试里。
 */
const ALL_PERMISSIONS = [
  'auth:user:read',
  'auth:user:create',
  'auth:user:update',
  'auth:user:delete',
  'auth:tenant:read',
  'auth:tenant:create',
  'auth:tenant:update',
  'auth:tenant:delete',
  ...Object.values(AdminViews),
];

const USER_ROW = {
  id: 1,
  tenantId: 1,
  username: 'admin',
  displayName: '系统管理员',
  status: 'ENABLED',
  lastLoginAt: 1791770000000,
};

/** 建立会话（权限集合决定按钮显隐） */
function signIn(permissions: string[]): void {
  standaloneSession().set({
    user: { userId: 7, username: 'tester', roles: ['SUPER_ADMIN'], permissions },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

let disconnect: () => void;

describe('权限管理后台 · 按钮级权限', () => {
  beforeEach(() => {
    localStorage.clear();
    standaloneSession().clear();
    disconnect = connectPermissionSource();

    mocks.userPage.mockReset();
    mocks.userCreate.mockReset();
    mocks.tenantPage.mockReset();
    mocks.userPage.mockResolvedValue({
      records: [USER_ROW],
      total: 1,
      page: 1,
      size: 10,
      pages: 1,
    });
    mocks.tenantPage.mockResolvedValue({ records: [], total: 0, page: 1, size: 10, pages: 0 });
  });

  it('管理员（读写权限 + 全部菜单码）：六个标签页与全部操作入口都在', async () => {
    signIn(ALL_PERMISSIONS);
    render(<App />);

    expect(await screen.findByTestId('user-row-admin')).toBeInTheDocument();

    // 菜单码决定**标签页**出不出来
    for (const key of ['users', 'tenants', 'resources', 'roles', 'userGroups', 'organizations']) {
      expect(screen.getByTestId(`admin-tab-${key}`)).toBeInTheDocument();
    }

    // 读写权限决定**点进去之后**能看到哪些按钮
    expect(screen.getByText('新建用户')).toBeInTheDocument();
    expect(screen.getByText('编辑')).toBeInTheDocument();
    expect(screen.getByText('重置口令')).toBeInTheDocument();
    expect(screen.getByText('删除')).toBeInTheDocument();

    // 每个操作入口都带着它自己的判定依据，便于"按钮为什么没出现"的排查
    expect(screen.getByText('新建用户')).toHaveAttribute('data-permission', 'auth:user:create');
  });

  it('每个标签页都带着自己的菜单码，便于"这个入口为什么没出现"的排查', () => {
    signIn(ALL_PERMISSIONS);
    render(<App />);

    expect(screen.getByTestId('admin-tab-users')).toHaveAttribute(
      'data-permission',
      AdminViews.USER,
    );
    expect(screen.getByTestId('admin-tab-organizations')).toHaveAttribute(
      'data-permission',
      AdminViews.ORGANIZATION,
    );
  });

  it('没被授菜单码的标签页不出现 —— 界面里不留进不去的入口', () => {
    signIn(['auth:user:read', AdminViews.USER]);
    render(<App />);

    expect(screen.getByTestId('admin-tab-users')).toBeInTheDocument();
    expect(screen.queryByTestId('admin-tab-tenants')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-tab-roles')).not.toBeInTheDocument();
  });

  it('一个菜单码都没有：一个标签页都不渲染，并说清缺的是哪一类码', () => {
    // 有读权限、但没有菜单码 —— 这时界面里不该出现任何模块入口
    signIn(['auth:user:read']);
    render(<App />);

    const empty = screen.getByTestId('admin-empty');
    expect(empty.textContent).toContain('ui:admin_*:view');
    expect(screen.queryByTestId('admin-tab-users')).not.toBeInTheDocument();
  });

  it('审计员（读权限 + 两个标签页菜单码）：列表照常显示，写操作入口一个都不渲染', async () => {
    signIn(['auth:user:read', 'auth:tenant:read', AdminViews.USER, AdminViews.TENANT]);
    render(<App />);

    // 读得到 —— 否则这一屏会变成"什么都没有"，就分不清是没权限还是没数据
    expect(await screen.findByTestId('user-row-admin')).toBeInTheDocument();

    expect(screen.queryByText('新建用户')).not.toBeInTheDocument();
    expect(screen.queryByText('编辑')).not.toBeInTheDocument();
    expect(screen.queryByText('重置口令')).not.toBeInTheDocument();
    expect(screen.queryByText('删除')).not.toBeInTheDocument();
  });

  it('租户页与用户页各自独立判定（有租户写权限不影响用户页的显隐）', async () => {
    signIn([
      'auth:user:read',
      'auth:tenant:read',
      'auth:tenant:create',
      AdminViews.USER,
      AdminViews.TENANT,
    ]);
    render(<App />);
    await screen.findByTestId('user-row-admin');

    // 用户页：无写权限
    expect(screen.queryByText('新建用户')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('admin-tab-tenants'));
    expect(await screen.findByText('新建租户')).toBeInTheDocument();
  });

  it('没有读权限时服务端返回 403：显示"无权限"而不是当成登录失效', async () => {
    mocks.userPage.mockRejectedValue(
      new HttpStatusError({
        message: '无权限',
        url: '/api/auth/users',
        method: 'GET',
        status: 403,
      }),
    );
    // 菜单码决定"进得去"，读权限决定"读得到" —— 这里是进得去但读不到
    signIn([AdminViews.USER]);
    render(<App />);

    const banner = await screen.findByTestId('users-error');
    expect(banner.textContent).toContain('403');
    expect(banner.textContent).toContain('没有该操作的权限');
  });

  it('权限未就绪时不发请求（首次加载等权限，而不是先打一次注定 401 的请求）', () => {
    // 刻意不建会话 —— 权限来源未就绪
    render(<App />);

    expect(screen.getByTestId('admin-pending')).toBeInTheDocument();
    expect(mocks.userPage).not.toHaveBeenCalled();
  });

  it('身份条上给出 userId 与权限数量（子应用消费权限的可观测痕迹）', async () => {
    signIn(ALL_PERMISSIONS);
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('admin-identity').textContent).toContain('userId=7');
    });
    expect(screen.getByTestId('admin-permissions').children).toHaveLength(ALL_PERMISSIONS.length);
  });
});

/**
 * 断开权限订阅，避免用例之间互相污染。
 *
 * <p>**必须先 `cleanup()` 再断开**：`afterEach` 按注册顺序逆序执行，
 * 而 testing-library 的自动清理是在 import 时就注册的 —— 也就是说这个回调会**先于**自动清理跑。
 * 那时组件还挂在树上，断开订阅触发的发布就会落在 act 之外，产生一群 act 警告，
 * 而警告指向的位置（组件里的 setState）并不是真正的原因。
 */
afterEach(() => {
  cleanup();
  disconnect();
});
