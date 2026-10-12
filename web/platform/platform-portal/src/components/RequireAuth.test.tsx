import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import RequireAuth from './RequireAuth';

/**
 * 用 `vi.hoisted` 造一份可切换的会话替身。
 *
 * 守卫要测的正是"三种输入 → 三种输出"，而真实的恢复阶段是模块级一次性状态，
 * 塞不进用例里。这里只替换**订阅层**（`@/auth/hooks`），守卫本身跑的是真实现。
 */
const authState = vi.hoisted(() => ({
  authenticated: false,
  phase: 'done' as 'idle' | 'restoring' | 'done',
}));

vi.mock('@/auth/hooks', () => ({
  useSession: () => ({
    user: authState.authenticated ? { userId: 1, username: 'admin' } : null,
    tokens: { accessToken: null, refreshToken: null, expiresAt: null },
    authenticated: authState.authenticated,
  }),
  useRestorePhase: () => authState.phase,
}));

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route
          path="/protected"
          element={
            <RequireAuth>
              <div>受保护内容</div>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<div>登录页占位</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    authState.authenticated = false;
    authState.phase = 'done';
  });

  it('未登录时重定向到登录页，且不渲染受保护内容', () => {
    renderGuard();

    expect(screen.getByText('登录页占位')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
  });

  it('已登录时放行', () => {
    authState.authenticated = true;

    renderGuard();

    expect(screen.getByText('受保护内容')).toBeInTheDocument();
    expect(screen.queryByText('登录页占位')).not.toBeInTheDocument();
  });

  it('恢复会话期间既不跳登录页也不放行（否则每次刷新都会掉一次登录）', () => {
    authState.phase = 'restoring';

    renderGuard();

    expect(screen.getByText('正在恢复会话…')).toBeInTheDocument();
    expect(screen.queryByText('登录页占位')).not.toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
  });
});
