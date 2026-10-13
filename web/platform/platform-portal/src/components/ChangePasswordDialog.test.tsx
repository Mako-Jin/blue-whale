import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

import { session } from '@/auth';
import { ChangePasswordDialog } from './ChangePasswordDialog';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    status,
    ok: status >= 200 && status < 300,
    statusText: '',
    headers: { get: () => null },
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

/** 建立一个已登录的会话 —— 改密要求已认证，没有会话就测不到"会不会被登出" */
function signIn(): void {
  session.set({
    user: {
      userId: 1,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['SUPER_ADMIN'],
      permissions: ['auth:user:create'],
      mustChangePassword: true,
    },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

function fill(oldPassword: string, newPassword: string, confirmPassword: string): void {
  fireEvent.change(screen.getByLabelText('当前口令'), { target: { value: oldPassword } });
  fireEvent.change(screen.getByLabelText('新口令'), { target: { value: newPassword } });
  fireEvent.change(screen.getByLabelText('确认新口令'), { target: { value: confirmPassword } });
}

function submit(): void {
  fireEvent.click(screen.getByRole('button', { name: '确认修改' }));
}

describe('修改口令对话框', () => {
  const onClose = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    session.clear();
    signIn();
  });

  afterEach(() => {
    // 与登录页测试同一顺序：先卸载再清会话，免得 clear() 触发的通知落在 act 之外
    cleanup();
    vi.unstubAllGlobals();
    session.clear();
  });

  it('两次新口令不一致：当场报错且不发请求', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ code: 0, message: 'ok', data: null }));
    vi.stubGlobal('fetch', fetchMock);

    render(<ChangePasswordDialog onClose={onClose} />);
    fill('Seed@1234', 'Fresh@1234', 'Fresh@1235');
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('两次输入的新口令不一致');
    // 服务端只收到一个新口令，它无从知道用户有没有把新口令打错一遍 ——
    // 这一条只有客户端能判，所以它必须在这里判，而不是等一次往返之后
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('旧口令不对：显示服务端给的原因，且**不**结束会话', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        // 照真实形态造：业务失败返回 **HTTP 200** + 业务码（本仓 GlobalExceptionHandler
        // 的约定）。`status: 401` 是替身的属性而不是系统的属性 ——
        // 用它"测出"的缺陷在真实链路上并不存在（见 ADR-0044）。
        jsonResponse({ code: 40101, message: '当前口令不正确', data: null }),
      ),
    );

    render(<ChangePasswordDialog onClose={onClose} />);
    fill('Wrong@1234', 'Fresh@1234', 'Fresh@1234');
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('当前口令不正确');
    // 一次输错不该变成一次登出 —— 界面上应该还停在对话框里等他重填。
    // 保护它的是"业务失败返 HTTP 200"这条约定（否则内核会把 401 当成令牌失效，
    // 刷新、重试、清会话跳登录页，而用户会把它读成"改密成功，请重新登录"）。
    expect(session.isAuthenticated()).toBe(true);
    // 失败后按钮要恢复可用，否则用户只能关掉重开
    expect(screen.getByRole('button', { name: '确认修改' })).toBeEnabled();
  });

  it('成功：两个口令按位置传对，且本地会话随之结束', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ code: 0, message: 'ok', data: null }));
    vi.stubGlobal('fetch', fetchMock);

    render(<ChangePasswordDialog onClose={onClose} />);
    fill('Seed@1234', 'Fresh@1234', 'Fresh@1234');
    submit();

    await waitFor(() => expect(session.isAuthenticated()).toBe(false));

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(String(url)).toContain('/api/auth/password');
    expect(init.method).toBe('PUT');
    // 两个参数调换了的话，症状是"改密成功但口令没变"，而且没有任何一处会报错
    expect(JSON.parse(String(init.body))).toEqual({
      oldPassword: 'Seed@1234',
      newPassword: 'Fresh@1234',
    });
  });

  it('在点下去之前就把后果说清楚：会结束该账号在所有设备上的登录（含当前这个）', () => {
    render(<ChangePasswordDialog onClose={onClose} />);

    // 成功后会话立即结束、页面被送去登录页，界面没有机会事后解释 ——
    // 所以这句话只能前置。它也不是装饰：撤销范围真的是"全部刷新令牌"。
    expect(screen.getByText(/结束该账号在所有设备上的登录（含当前这个）/)).toBeInTheDocument();
  });
});
