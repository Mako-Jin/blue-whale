import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { SessionStore } from 'common-tools/session';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * 接口客户端用替身。
 *
 * <p>`AuditPermissions` / `AuditActions` / `AuditResults` 必须保留真身 ——
 * 页面里的契约标识就来自它们，若把它们也换掉，"标识拼错"这类问题会在测试里被一起掩盖。
 */
const mocks = vi.hoisted(() => ({
  auditPage: vi.fn(),
  auditStats: vi.fn(),
  auditDetail: vi.fn(),
  tenantCreate: vi.fn(),
  tenantRemove: vi.fn(),
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
          page: vi.fn(),
          detail: vi.fn(),
          create: vi.fn(),
          update: vi.fn(),
          resetPassword: vi.fn(),
          remove: vi.fn(),
        },
        tenants: {
          page: vi.fn(),
          detail: vi.fn(),
          create: mocks.tenantCreate,
          update: vi.fn(),
          remove: mocks.tenantRemove,
        },
      },
      audit: {
        page: mocks.auditPage,
        detail: mocks.auditDetail,
        stats: mocks.auditStats,
      },
    }),
  };
});

import { AuditConsoleViews } from 'common-api';

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

const AUDIT_READ = 'auth:audit:read';
const TENANT_CREATE = 'auth:tenant:create';
const TENANT_DELETE = 'auth:tenant:delete';

/**
 * 三块区域各自的菜单码。
 *
 * <p>与 `auth:audit:read` 是**两层**：菜单码决定区块出不出现，读权限决定里面的请求
 * 能不能成功。这两个码都缺的人看到的是"没有读权限"，只有缺菜单码的人看到的是
 * "没有可见的区块" —— 提示语不同，因为要用户做的事不同。
 */
const { FLOW, STATS, DRILL } = AuditConsoleViews;

const SUCCESS_ROW = {
  id: 101,
  action: 'auth.tenant.create',
  resource: 'auth:tenant',
  targetId: '9',
  operatorId: 7,
  operatorName: 'tester',
  result: 'SUCCESS',
  httpMethod: 'POST',
  requestUri: '/api/auth/tenants',
  ip: '127.0.0.1',
  durationMs: 12,
  serverTime: 1791770000000,
};

const FAILURE_ROW = {
  id: 102,
  action: 'auth.tenant.create',
  resource: 'auth:tenant',
  operatorId: 8,
  operatorName: 'auditor',
  result: 'FAILURE',
  errorCode: 40301,
  errorMessage: '缺少权限：auth:tenant:create',
  httpMethod: 'POST',
  requestUri: '/api/auth/tenants',
  durationMs: 3,
  serverTime: 1791770001000,
};

/** 建立会话（权限集合决定能否读到流水） */
function signIn(permissions: string[]): void {
  standaloneSession().set({
    user: { userId: 7, username: 'tester', roles: ['SUPER_ADMIN'], permissions },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

let disconnect: () => void;

beforeEach(() => {
  localStorage.clear();
  standaloneSession().clear();
  disconnect = connectPermissionSource();

  for (const mock of Object.values(mocks)) mock.mockReset();

  mocks.auditPage.mockResolvedValue({
    records: [SUCCESS_ROW, FAILURE_ROW],
    total: 2,
    page: 1,
    size: 20,
    pages: 1,
  });
  mocks.auditStats.mockResolvedValue({
    total: 2,
    failureTotal: 1,
    startTime: 1791769000000,
    endTime: 1791770000000,
    actions: [
      { action: 'auth.tenant.create', actionCount: 2, failureCount: 1 },
      { action: 'auth.user.create', actionCount: 1, failureCount: 0 },
    ],
  });
});

describe('审计日志台 · 只读管道', () => {
  it('有读权限时渲染流水、聚合与演练区', async () => {
    signIn([AUDIT_READ, TENANT_CREATE, TENANT_DELETE, FLOW, STATS, DRILL]);
    render(<App />);

    expect(await screen.findByTestId('audit-row-101')).toBeInTheDocument();
    expect(screen.getByTestId('audit-row-102')).toBeInTheDocument();
    // 失败行必须带出业务码 —— 审计表里"为什么被拒"是最关键的一列
    expect(screen.getByTestId('audit-result-102').textContent).toContain('40301');

    expect(screen.getByTestId('audit-stat-auth.tenant.create').textContent).toContain('失败 1');
    expect(screen.getByTestId('drill-panel')).toBeInTheDocument();
    expect(screen.getByTestId('audit-total').textContent).toContain('共 2 条');
  });

  it('页面上没有任何"新增一条审计"的入口（接口不存在，入口也不该存在）', async () => {
    signIn([AUDIT_READ, FLOW]);
    render(<App />);
    await screen.findByTestId('audit-row-101');

    expect(screen.getByTestId('audit-callout').textContent).toContain('40500');
    for (const label of ['新增审计', '补一条', '写入审计', '上报审计']) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it('审计员（只有读权限）：演练区按钮照旧渲染 —— 越权尝试本身就该被记录', async () => {
    signIn([AUDIT_READ, FLOW, DRILL]);
    render(<App />);
    await screen.findByTestId('audit-row-101');

    // 与权限管理台刻意相反：那边没权限就不渲染按钮，这里永远渲染
    const run = screen.getByTestId('drill-panel').querySelector('.drill__run');
    expect(run).not.toBeNull();
    expect(run).toHaveTextContent('跑一次演练');
  });

  it('演练：写一次租户，随后在审计表里等到对应记录', async () => {
    signIn([AUDIT_READ, TENANT_CREATE, TENANT_DELETE, FLOW, DRILL]);
    mocks.tenantCreate.mockResolvedValue({
      id: 9,
      tenantCode: 'drillabc',
      tenantName: '审计演练租户',
    });
    mocks.tenantRemove.mockResolvedValue(undefined);
    // 演练里的等待查询按 action 过滤：替身要**回一个与请求的动作同名**的记录，
    // 否则两次等待看起来都像同一条（真实服务端也是这么返回的）
    mocks.auditPage.mockImplementation((query?: { action?: string }) =>
      Promise.resolve(
        query?.action
          ? {
              records: [{ ...SUCCESS_ROW, action: query.action }],
              total: 1,
              page: 1,
              size: 1,
              pages: 1,
            }
          : { records: [SUCCESS_ROW, FAILURE_ROW], total: 2, page: 1, size: 20, pages: 1 },
      ),
    );

    render(<App />);
    await screen.findByTestId('audit-row-101');

    fireEvent.click(screen.getByText('跑一次演练'));

    await waitFor(() => {
      expect(mocks.tenantCreate).toHaveBeenCalledTimes(1);
    });
    const steps = await screen.findByTestId('drill-steps');
    await waitFor(() => {
      expect(steps.textContent).toContain('auth.tenant.delete');
    });
    expect(steps.textContent).toContain('auth.tenant.create');
    expect(mocks.tenantRemove).toHaveBeenCalledWith(9);
  });

  it('演练被拒绝时说明"已留痕"，而不是笼统说失败', async () => {
    signIn([AUDIT_READ, FLOW, DRILL]);
    mocks.tenantCreate.mockRejectedValue(new Error('缺少权限：auth:tenant:create'));

    render(<App />);
    await screen.findByTestId('audit-row-101');

    fireEvent.click(screen.getByText('跑一次演练'));

    const steps = await screen.findByTestId('drill-steps');
    await waitFor(() => {
      expect(steps.textContent).toContain('被拒绝');
    });
    expect(steps.textContent).toContain('留痕');
    expect(mocks.tenantRemove).not.toHaveBeenCalled();
  });

  it('没有审计读权限：明确说缺哪个标识，并且不发请求', async () => {
    signIn([TENANT_CREATE]);
    render(<App />);

    const banner = await screen.findByTestId('audit-no-permission');
    expect(banner.textContent).toContain(AUDIT_READ);
    expect(mocks.auditPage).not.toHaveBeenCalled();
    expect(screen.queryByTestId('audit-table')).not.toBeInTheDocument();
  });

  it('有菜单码但没有读权限：提示的仍是"缺读权限" —— 菜单码不替代准入门槛', async () => {
    // 三块区域全授，唯独没有 auth:audit:read
    signIn([FLOW, STATS, DRILL]);
    render(<App />);

    const banner = await screen.findByTestId('audit-no-permission');
    expect(banner.textContent).toContain(AUDIT_READ);
    // 两道门是独立的：菜单码只让区块"可以出现"，读权限才决定能不能看到内容
    expect(screen.queryByTestId('audit-list-panel')).not.toBeInTheDocument();
    expect(mocks.auditPage).not.toHaveBeenCalled();
  });

  it('一个菜单码都没有：三块区域都不渲染，并说清缺的是哪一类码', async () => {
    // 有读权限、但没有菜单码 —— 查回来的东西没有落点，因此干脆不查
    signIn([AUDIT_READ]);
    render(<App />);

    const empty = screen.getByTestId('audit-empty');
    expect(empty.textContent).toContain('ui:audit_*:view');
    expect(screen.queryByTestId('audit-list-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('audit-stats-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drill-panel')).not.toBeInTheDocument();
    expect(mocks.auditPage).not.toHaveBeenCalled();
  });

  it('权限未就绪时不发请求（先等权限，而不是先打一次注定被拒的请求）', () => {
    // 刻意不建会话 —— 权限来源未就绪
    render(<App />);

    expect(screen.getByTestId('audit-pending')).toBeInTheDocument();
    expect(mocks.auditPage).not.toHaveBeenCalled();
  });

  it('点聚合里的动作即筛选流水（聚合给出"哪类最多"，下一步必然是"长什么样"）', async () => {
    signIn([AUDIT_READ, FLOW, STATS]);
    render(<App />);
    await screen.findByTestId('audit-row-101');

    fireEvent.click(screen.getByTestId('audit-stat-auth.tenant.create').querySelector('button')!);

    await waitFor(() => {
      const calls = mocks.auditPage.mock.calls as Array<[{ action?: string }]>;
      expect(calls.some((call) => call[0]?.action === 'auth.tenant.create')).toBe(true);
    });
  });

  it('详情里体现"审计只有服务端时间"这一条边界', async () => {
    signIn([AUDIT_READ, FLOW]);
    render(<App />);
    await screen.findByTestId('audit-row-101');

    fireEvent.click(screen.getAllByText('详情')[1]!);

    const detail = await screen.findByTestId('audit-detail');
    expect(detail.textContent).toContain('记录时间');
    expect(detail.textContent).toContain('40301');
    expect(detail.textContent).toContain('缺少权限：auth:tenant:create');
    // 没有客户端时间戳可显示 —— 审计不由客户端产生
    expect(detail.textContent).not.toContain('客户端时间');
  });
});

/**
 * 断开权限订阅，避免用例之间互相污染。
 *
 * <p>**必须先 `cleanup()` 再断开**：`afterEach` 按注册顺序逆序执行，
 * 而 testing-library 的自动清理是在 import 时就注册的 —— 这个回调会**先于**自动清理跑。
 * 那时组件还挂在树上，断开订阅触发的发布就会落在 act 之外，产生一群 act 警告，
 * 而警告指向的位置并不是真正的原因。
 */
afterEach(() => {
  cleanup();
  disconnect();
});
