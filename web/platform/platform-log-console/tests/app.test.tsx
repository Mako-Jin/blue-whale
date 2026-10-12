import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// 用 hoisted 保存替身：vi.mock 的工厂函数会被提升到 import 之前，
// 直接引用模块作用域的变量会踩到"初始化前访问"的 TDZ 报错。
const mocks = vi.hoisted(() => ({
  health: vi.fn(),
  page: vi.fn(),
  stats: vi.fn(),
  report: vi.fn(),
  listByTraceId: vi.fn(),
}));

vi.mock('common-api', () => ({
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
  // 少一个导出会让整个测试文件加载失败（0 个用例，不是某个用例失败）
  createConsoleAuth: () => ({
    port: { getAccessToken: () => null, reauthenticate: async () => false },
    ensureSession: async () => {},
  }),
}));

import App from '../src/App';

describe('日志控制台', () => {
  beforeEach(() => {
    mocks.health.mockResolvedValue({ status: 'UP', dependencies: { database: 'UP' } });
    mocks.stats.mockResolvedValue({ total: 0, levels: [] });
    mocks.page.mockResolvedValue({ records: [], total: 0, page: 1, size: 20, pages: 0 });
    mocks.report.mockResolvedValue({ accepted: 0, rejected: 0 });
    mocks.listByTraceId.mockResolvedValue([]);
  });

  it('渲染出两栏骨架：左边产生、右边查看', async () => {
    render(<App />);

    expect(screen.getByText('BlueWhale 日志控制台')).toBeInTheDocument();
    expect(screen.getByText('产生日志')).toBeInTheDocument();
    expect(screen.getByText('服务端日志')).toBeInTheDocument();

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
