import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '@/App';
import type { MountMode } from '@/mount';

/**
 * 本文件的运行环境（jsdom）**没有 WebGL**，所以它天然覆盖的就是降级分支。
 * 真正带动画的渲染由 `scripts/verify-micro-frontend.mjs` 在真实浏览器里验证 ——
 * 三维相关的 bug（上下文、绘制、释放、切场景后的资源回收）在无 GPU 环境里
 * 一条都复现不出来。
 */

/**
 * 渲染并等清单加载的 promise 落地。
 *
 * App 挂载后会异步取一次场景清单。不等它的话，那次 `setState` 会落在 `act` 之外，
 * React 会打出一串 "not wrapped in act" 警告 —— 而且测试可能在更新前就断言了，
 * 断言的是"旧数据碰巧也对"的结果。
 */
async function renderApp(mode: MountMode) {
  const view = render(<App mode={mode} />);
  await act(async () => {});
  return view;
}

describe('App', () => {
  it('无 WebGL 时降级成提示面板，而不是白屏', async () => {
    await renderApp('standalone');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/无法创建 WebGL 上下文/)).toBeInTheDocument();
  });

  it('按运行方式显示挂载点来源：独立启动用自建容器', async () => {
    await renderApp('standalone');

    expect(screen.getByText('独立运行 · 自建容器')).toBeInTheDocument();
  });

  it('按运行方式显示挂载点来源：被基座加载用基座容器', async () => {
    await renderApp('host');

    expect(screen.getByText('被基座加载 · 基座容器')).toBeInTheDocument();
  });

  it('未被基座加载时不显示桥接状态为已连接', async () => {
    await renderApp('standalone');

    expect(screen.getByText('桥接未连接')).toBeInTheDocument();
  });

  it('渲染控制面板与统计占位', async () => {
    await renderApp('standalone');

    expect(screen.getByRole('region', { name: '渲染控制' })).toBeInTheDocument();
    // 默认场景是古建筑（可操控）：镜头绕人转会让人晕，所以自动环绕默认关
    expect(screen.getByLabelText('自动环绕')).not.toBeChecked();
    expect(screen.getByLabelText('线框模式')).not.toBeChecked();
    expect(screen.getByText('帧率')).toBeInTheDocument();
  });

  it('渲染场景选择器，首屏即有可选的场景卡片', async () => {
    await renderApp('standalone');

    // 同步就有 —— 清单不允许等异步接口才出来（ADR-0003 客户端底线第 1 条）
    expect(screen.getByRole('region', { name: '场景选择' })).toBeInTheDocument();
    expect(screen.getByText('古建筑')).toBeInTheDocument();
    expect(screen.getByText('原始丛林')).toBeInTheDocument();
    expect(screen.getByText('现代写字楼')).toBeInTheDocument();
    expect(screen.getByText('太空飞船')).toBeInTheDocument();
  });

  it('切换场景时把渲染选项重置为该场景声明的默认值', async () => {
    await renderApp('standalone');

    // 古建筑：网格是广场铺装的分缝，默认开；可操控场景的自动环绕默认关
    expect(screen.getByLabelText('地面网格')).toBeChecked();
    expect(screen.getByLabelText('自动环绕')).not.toBeChecked();

    // 原始丛林：网格只会干扰（没有铺装可参照），自动环绕开 —— 两项都被重置
    fireEvent.click(screen.getByRole('button', { name: /原始丛林/ }));
    expect(screen.getByLabelText('地面网格')).not.toBeChecked();
    expect(screen.getByLabelText('自动环绕')).toBeChecked();

    // 太空又关掉网格：真空里没有地面，网格只是干扰
    fireEvent.click(screen.getByRole('button', { name: /太空飞船/ }));
    expect(screen.getByLabelText('地面网格')).not.toBeChecked();
  });
});
