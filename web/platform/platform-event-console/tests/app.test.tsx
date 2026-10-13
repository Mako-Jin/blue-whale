import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  health: vi.fn(),
  page: vi.fn(),
  stats: vi.fn(),
  report: vi.fn(),
  /** 当前身份持有的权限集合 —— 用例改它就能改"哪一栏可见" */
  permissions: [] as string[],
}));

/*
 * `common-api` 只替换**网络与认证端口**，其余导出（`EventConsoleViews` 等）照旧来自真身 ——
 * 权限码若在测试里被换掉，"拼错一个码 ⇒ 那一栏永远不出现"就正好会被掩盖。
 */
vi.mock('common-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('common-api')>();
  return {
    ...actual,
    createApiClient: () => ({
      http: {},
      system: { health: mocks.health },
      event: {
        report: mocks.report,
        page: mocks.page,
        stats: mocks.stats,
      },
    }),
    // events.ts 在模块顶层就调用它，少一个导出会让整个文件加载失败
    // （表现为 0 个用例，而不是某个用例失败）
    createConsoleAuth: () => ({
      port: { getAccessToken: () => null, reauthenticate: async () => false },
      ensureSession: async () => {},
      readStandaloneIdentity: () => ({
        userId: '7',
        roles: [],
        permissions: mocks.permissions,
      }),
      onStandaloneIdentityChange: () => () => {},
    }),
  };
});

import { EventConsoleViews } from 'common-api';

import App from '../src/App';
import { connectPermissions } from '../src/auth/permissions';

let disconnect: (() => void) | null = null;

/**
 * 建立身份，并接上权限来源。
 *
 * <p>合成一个动作是刻意的：store 只在 `connect()` 时读一次身份，
 * 先接后改权限不会生效。测试里也必须显式 connect（真实应用在 `main.tsx` 的挂载生命周期里接），
 * 忘了接的表现正是两栏都不出现 —— 与"没权限"长得一模一样。
 */
function signIn(permissions: readonly string[]): void {
  mocks.permissions = [...permissions];
  disconnect?.();
  disconnect = connectPermissions();
}

function stubBackend(): void {
  mocks.health.mockResolvedValue({ status: 'UP', dependencies: { database: 'UP' } });
  mocks.stats.mockResolvedValue({ total: 0, events: [] });
  mocks.page.mockResolvedValue({ records: [], total: 0, page: 1, size: 20, pages: 0 });
  mocks.report.mockResolvedValue({ accepted: 0, rejected: 0 });
}

describe('行为事件控制台 · 两栏按菜单码显隐', () => {
  beforeEach(stubBackend);

  it('两栏都有权限时渲染出骨架：客户端视角 + 服务端视角', async () => {
    signIn([EventConsoleViews.TRACK, EventConsoleViews.QUERY]);

    render(<App />);

    expect(screen.getByText('BlueWhale 行为事件控制台')).toBeInTheDocument();
    expect(screen.getByTestId('event-track-panel')).toBeInTheDocument();
    expect(screen.getByTestId('event-server-panel')).toBeInTheDocument();

    // 后端连通性由健康检查驱动，说明探活链路真的接上了
    expect(await screen.findByText(/后端 UP/)).toBeInTheDocument();
  });

  it('只有埋点码：左栏在、右栏不在，且不发查询请求', async () => {
    signIn([EventConsoleViews.TRACK]);

    render(<App />);

    expect(await screen.findByTestId('event-track-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('event-server-panel')).not.toBeInTheDocument();
    expect(mocks.page).not.toHaveBeenCalled();
    expect(mocks.stats).not.toHaveBeenCalled();
  });

  it('只有查询码：右栏在、左栏不在', async () => {
    signIn([EventConsoleViews.QUERY]);

    render(<App />);

    expect(await screen.findByTestId('event-server-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('event-track-panel')).not.toBeInTheDocument();
    // 右栏可见 ⇒ 查询照常发出
    expect(mocks.page).toHaveBeenCalled();
  });

  it('一个菜单码都没有：两栏都不渲染，并说清缺的是哪一个码', () => {
    signIn([]);

    render(<App />);

    const empty = screen.getByTestId('event-empty');
    expect(empty.textContent).toContain('ui:event_track:view');
    expect(empty.textContent).toContain('ui:event_query:view');
    expect(screen.queryByTestId('event-track-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('event-server-panel')).not.toBeInTheDocument();
  });

  it('只剩一栏时收成单列 —— 否则内容会被按两栏比例挤在半边', async () => {
    signIn([EventConsoleViews.TRACK]);

    const { container } = render(<App />);
    await screen.findByTestId('event-track-panel');

    const layout = container.querySelector('.layout');
    expect(layout?.className).toContain('layout--single');
  });

  it('两栏都在时不加单列的修饰类（5:7 的原比例要留着）', async () => {
    signIn([EventConsoleViews.TRACK, EventConsoleViews.QUERY]);

    const { container } = render(<App />);
    await screen.findByTestId('event-server-panel');

    const layout = container.querySelector('.layout');
    expect(layout?.className).not.toContain('layout--single');
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
