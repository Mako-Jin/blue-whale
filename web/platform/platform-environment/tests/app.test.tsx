import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '@/App';

/**
 * 本文件的运行环境（jsdom）**没有 WebGL**，所以它天然覆盖的就是降级分支。
 * 真正带动画的渲染由 `scripts/verify-micro-frontend.mjs` 在真实浏览器里验证 ——
 * 三维相关的 bug（上下文、绘制、释放）在无 GPU 环境里一条都复现不出来。
 */
describe('App', () => {
  it('无 WebGL 时降级成提示面板，而不是白屏', async () => {
    render(<App mode="standalone" />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/无法创建 WebGL 上下文/)).toBeInTheDocument();
  });

  it('按运行方式显示挂载点来源：独立启动用自建容器', () => {
    render(<App mode="standalone" />);

    expect(screen.getByText('独立运行 · 自建容器')).toBeInTheDocument();
  });

  it('按运行方式显示挂载点来源：被基座加载用基座容器', () => {
    render(<App mode="host" />);

    expect(screen.getByText('被基座加载 · 基座容器')).toBeInTheDocument();
  });

  it('未被基座加载时不显示桥接状态为已连接', () => {
    render(<App mode="standalone" />);

    expect(screen.getByText('桥接未连接')).toBeInTheDocument();
  });

  it('渲染控制面板与统计占位', () => {
    render(<App mode="standalone" />);

    expect(screen.getByRole('region', { name: '渲染控制' })).toBeInTheDocument();
    expect(screen.getByLabelText('自动环绕')).toBeChecked();
    expect(screen.getByLabelText('线框模式')).not.toBeChecked();
    expect(screen.getByText('帧率')).toBeInTheDocument();
  });
});
