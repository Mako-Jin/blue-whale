import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// 用 hoisted 保存替身：vi.mock 的工厂函数会被提升到 import 之前，
// 直接引用模块作用域的变量会踩到"初始化前访问"的 TDZ 报错。
const mocks = vi.hoisted(() => ({
  health: vi.fn(),
  page: vi.fn(),
  stats: vi.fn(),
  report: vi.fn(),
  listByTraceId: vi.fn(),
  /** 当前身份持有的权限集合 —— 用例改它就能改"哪一栏可见" */
  permissions: [] as string[],
}));

/*
 * `common-api` 只替换**网络与认证端口**，其余导出（`LogConsoleViews` 等）照旧来自真身。
 * 用 `importOriginal` 保留它们：权限码若在测试里被换掉，
 * "拼错一个码 ⇒ 那一栏永远不出现"这类问题就正好会被掩盖掉。
 */
vi.mock('common-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('common-api')>();
  return {
    ...actual,
    createApiClient: () => ({
      http: {},
      system: { health: mocks.health },
      log: {
        report: mocks.report,
        page: mocks.page,
        stats: mocks.stats,
        detail: vi.fn(),
        listByTraceId: mocks.listByTraceId,
      },
    }),
    // 认证端口要一起给：console.ts 在模块顶层就调用它，
    // 少一个导出会让整个测试文件加载失败（0 个用例，不是某个用例失败）。
    // 这里给的是完整的 StandaloneIdentitySource —— 权限 store 靠它读身份。
    createConsoleAuth: () => ({
      port: { getAccessToken: () => null, reauthenticate: async () => false },
      ensureSession: async () => {},
      readStandaloneIdentity: () => ({
        userId: '7',
        roles: [],
        permissions: mocks.permissions,
      }),
      // 本文件不模拟"运行中换身份"，订阅给一个空实现即可
      onStandaloneIdentityChange: () => () => {},
    }),
  };
});

import { LogConsoleViews } from 'common-api';

import App from '../src/App';
import { connectPermissions } from '../src/auth/permissions';

let disconnect: (() => void) | null = null;

/**
 * 建立身份，并接上权限来源。
 *
 * <p>两件事合成一个动作是刻意的：store 只在 `connect()` 时读一次身份，
 * 先接后改权限不会生效 —— 症状是"权限明明给了，界面却还是空的"。
 * 顺带一提，测试里必须显式 `connect`（真实应用是在 `main.tsx` 的挂载生命周期里接的），
 * 忘了接的表现正是两栏都不出现，与"没权限"长得一模一样。
 */
function signIn(permissions: readonly string[]): void {
  mocks.permissions = [...permissions];
  disconnect?.();
  disconnect = connectPermissions();
}

function stubBackend(): void {
  mocks.health.mockResolvedValue({ status: 'UP', dependencies: { database: 'UP' } });
  mocks.stats.mockResolvedValue({ total: 0, levels: [] });
  mocks.page.mockResolvedValue({ records: [], total: 0, page: 1, size: 20, pages: 0 });
  mocks.report.mockResolvedValue({ accepted: 0, rejected: 0 });
  mocks.listByTraceId.mockResolvedValue([]);
}

describe('日志控制台', () => {
  beforeEach(() => {
    signIn([LogConsoleViews.REPORTER, LogConsoleViews.QUERY]);
    stubBackend();
  });

  it('渲染出两栏骨架：左边产生、右边查看', async () => {
    render(<App />);

    expect(screen.getByText('BlueWhale 日志控制台')).toBeInTheDocument();
    expect(screen.getByTestId('log-generator-panel')).toBeInTheDocument();
    expect(screen.getByTestId('log-server-panel')).toBeInTheDocument();

    // 后端连通性由健康检查驱动，说明探活链路真的接上了
    expect(await screen.findByText('后端 UP')).toBeInTheDocument();
  });

  it('点级别按钮会经由 logger 产生日志，并出现在本地缓冲里', async () => {
    render(<App />);

    expect(screen.getByText('还没有日志，点上面的按钮产生几条')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'INFO' }));

    expect(await screen.findByText(/手动产生的一条 info 日志/)).toBeInTheDocument();
    // 待上报计数同步变化，说明记录确实进了上报缓冲
    const pending = screen.getByText('待上报').parentElement as HTMLElement;
    expect(within(pending).getByText('1')).toBeInTheDocument();
  });

  it('后端不可达时给出明确的启动指引，而不是只显示空白表格', async () => {
    mocks.health.mockRejectedValue(new Error('连接被拒绝'));
    mocks.stats.mockRejectedValue(new Error('连接被拒绝'));
    mocks.page.mockRejectedValue(new Error('连接被拒绝'));

    render(<App />);

    expect(await screen.findByText(/连不上后端/)).toBeInTheDocument();
    // 顶部横幅与右栏的查询失败提示都会带上启动命令，因此这里断言"至少出现过"
    expect(screen.getAllByText(/spring-boot:run/).length).toBeGreaterThan(0);
  });
});

/**
 * 两栏的显隐由**菜单码**决定，与"里面的请求能不能成功"是两层。
 *
 * <p>三条用例盯的是同一件事的三个切面：可见性跟着权限走，
 * 而且不可见时**连请求都不发** —— 一条看不见的管道仍在后台发请求，
 * 只是把"假开关"藏得更深。
 */
describe('日志控制台 · 两栏按菜单码显隐', () => {
  beforeEach(stubBackend);

  it('只有演示码：左栏在、右栏不在，且不发查询请求', async () => {
    signIn([LogConsoleViews.REPORTER]);

    render(<App />);

    expect(await screen.findByTestId('log-generator-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('log-server-panel')).not.toBeInTheDocument();
    expect(mocks.page).not.toHaveBeenCalled();
    expect(mocks.stats).not.toHaveBeenCalled();
  });

  it('只有查询码：右栏在、左栏不在', async () => {
    signIn([LogConsoleViews.QUERY]);

    render(<App />);

    expect(await screen.findByTestId('log-server-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('log-generator-panel')).not.toBeInTheDocument();
    // 右栏可见 ⇒ 查询照常发出
    expect(mocks.page).toHaveBeenCalled();
  });

  it('一个菜单码都没有：两栏都不渲染，并说清缺的是哪一个码', () => {
    signIn([]);

    render(<App />);

    const empty = screen.getByTestId('console-empty');
    expect(empty.textContent).toContain('ui:log_reporter:view');
    expect(empty.textContent).toContain('ui:log_query:view');
    expect(screen.queryByTestId('log-generator-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('log-server-panel')).not.toBeInTheDocument();
  });
});

/**
 * 断开权限订阅，避免用例之间互相污染。
 *
 * <p>**必须先 `cleanup()` 再断开**：`afterEach` 按注册顺序逆序执行，
 * 而 testing-library 的自动清理是在 import 时就注册的 —— 这个回调会**先于**自动清理跑。
 * 那时组件还挂在树上，断开订阅触发的发布就会落在 act 之外，产生一群 act 警告。
 */
afterEach(() => {
  cleanup();
  disconnect?.();
  disconnect = null;
});
