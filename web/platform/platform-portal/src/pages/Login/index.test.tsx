import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { session } from '@/auth';
import Login from './index';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    statusText: '',
    headers: { get: () => null },
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const LOGIN_OK = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresIn: 7200,
  userId: 1,
  username: 'admin',
  displayName: '系统管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['auth:user:create'],
};

function renderLogin(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<div>概览占位</div>} />
        <Route path="/environment" element={<div>三维环境占位</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

/**
 * 填表并提交。
 *
 * 保持**同步**：提交处理本身是异步的，若把它再包成 `async` 并 `await`，那个 `await`
 * 会先让出一拍微任务，`findBy*` 才姗姗启动 —— 请求失败的更新就可能赶在 act 作用域
 * 张开之前落地。同步调用能让"点击 → 等待 → 断言"落在同一个 act 区间里。
 */
function submit(password: string): void {
  fireEvent.change(screen.getByLabelText('口令'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: '登录' }));
}

describe('登录页', () => {
  beforeEach(() => {
    session.clear();
    localStorage.clear();
  });

  afterEach(() => {
    /*
     * **顺序有讲究：先卸载，再清会话。**
     * 会话存储每次 `clear()` 都会重建 tokens 对象，`commit()` 因而必然通知订阅者；
     * 失败路径下本组件仍然是挂载着的（没有跳转），于是这次由 `clear()` 触发的外部
     * store 更新会落在 act 作用域之外。先卸载就没有订阅者了。
     */
    cleanup();
    vi.unstubAllGlobals();
    session.clear();
  });

  it('登录成功后建立会话，并回到原本想去的页面', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ code: 0, message: 'ok', data: LOGIN_OK })),
    );

    renderLogin({ from: '/environment' });
    submit('Admin@12345');

    expect(await screen.findByText('三维环境占位')).toBeInTheDocument();
    expect(session.isAuthenticated()).toBe(true);
    expect(session.hasPermission('auth:user:create')).toBe(true);
  });

  it('口令错误时展示服务端给出的原因，且不建立会话', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ code: 40101, message: '用户名或密码错误', data: null })),
    );

    renderLogin();
    submit('wrong-password');

    expect(await screen.findByRole('alert')).toHaveTextContent('用户名或密码错误');
    expect(session.isAuthenticated()).toBe(false);
    // 失败后按钮要恢复可用，否则用户改完口令也点不动
    expect(screen.getByRole('button', { name: '登录' })).toBeEnabled();
  });

  it('连不上服务端时给出可操作的提示，而不是把原始错误抛给用户', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );

    renderLogin();
    submit('Admin@12345');

    expect(await screen.findByRole('alert')).toHaveTextContent('连不上服务端');
  });

  it('登录后重定向目标只认站内路径（挡掉被塞进来的外站地址）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ code: 0, message: 'ok', data: LOGIN_OK })),
    );

    renderLogin({ from: '//evil.example.com' });
    submit('Admin@12345');

    expect(await screen.findByText('概览占位')).toBeInTheDocument();
  });
});
