import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import BasicLayout from './BasicLayout';
import { auth, session } from '@/auth';
import { MICRO_APPS } from '@/micro';

/**
 * 导航栏按菜单码过滤。
 *
 * <p>这是"看不见"的那一半；另一半是 `RequireMenu` 的"进不去"。
 * 两者读同一个字段，因此这里也顺带钉住了它们不会各说各话 ——
 * 只过滤导航栏的话，地址栏就成了后门。
 */
function signIn(permissions: readonly string[], mustChangePassword = false): void {
  session.set({
    user: {
      userId: 7,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['SUPER_ADMIN'],
      permissions: [...permissions],
      mustChangePassword,
    },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

function renderLayout(pathname = '/') {
  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <BasicLayout />
    </MemoryRouter>,
  );
}

const ALL_MENUS = MICRO_APPS.map((app) => app.menuPermission);

describe('BasicLayout · 导航过滤', () => {
  beforeEach(() => {
    localStorage.clear();
    session.clear();
  });

  it('持有全部菜单码时五个应用入口都在', () => {
    signIn(ALL_MENUS);
    renderLayout();

    for (const app of MICRO_APPS) {
      expect(screen.getByTestId(`nav-${app.name}`)).toBeInTheDocument();
    }
  });

  it('只有部分菜单码时其余入口不渲染', () => {
    const [kept, dropped] = MICRO_APPS;
    signIn([kept.menuPermission]);
    renderLayout();

    expect(screen.getByTestId(`nav-${kept.name}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`nav-${dropped.name}`)).not.toBeInTheDocument();
  });

  it('概览入口恒在 —— 它是"被弹回来"时的落点，不参与过滤', () => {
    signIn([]);
    renderLayout();

    expect(screen.getByText('概览')).toBeInTheDocument();
    // 一个应用都没授权时，导航栏只剩概览
    for (const app of MICRO_APPS) {
      expect(screen.queryByTestId(`nav-${app.name}`)).not.toBeInTheDocument();
    }
  });

  it('子应用页面铺满内容区（不套一层留白）—— 判据用的是完整注册表', () => {
    const app = MICRO_APPS[0];
    signIn(ALL_MENUS);
    const { container } = renderLayout(app.routePath);

    expect(container.querySelector('.basic-layout__main--flush')).not.toBeNull();
  });
});

/**
 * 修改口令入口的落点。
 *
 * <p>它必须在**不依赖任何菜单码**的地方：新账号可能一个菜单码都没有（ADR-0031），
 * 那时它进不去任何子应用 —— 把入口放进子应用，等于让"被自己都进不去的口令锁在门外"
 * 从登录态换到门户态重演一遍。下面第一条用例就是这条判据本身。
 */
describe('BasicLayout · 修改口令入口', () => {
  beforeEach(() => {
    localStorage.clear();
    session.clear();
  });

  it('一个菜单码都没有时入口仍在（它不依赖任何菜单码）', () => {
    signIn([]);
    renderLayout();

    expect(screen.getByTestId('open-change-password')).toBeInTheDocument();
  });

  it('待改口令时自动弹一次，并留下角标', () => {
    signIn(ALL_MENUS, true);
    renderLayout();

    expect(screen.getByRole('dialog', { name: '修改口令' })).toBeInTheDocument();
    expect(screen.getByTestId('must-change-password-badge')).toBeInTheDocument();
  });

  it('不需要改口令时不弹窗、也没有角标 —— 不能对着没这回事的人弹', () => {
    signIn(ALL_MENUS);
    renderLayout();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('must-change-password-badge')).not.toBeInTheDocument();
  });

  it('关掉弹窗后不再自动弹，但角标还在（"还没改"这件事没有变）', () => {
    signIn(ALL_MENUS, true);
    renderLayout();

    fireEvent.click(screen.getByRole('button', { name: '关闭' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('must-change-password-badge')).toBeInTheDocument();
  });

  it('点入口能自己打开（关掉之后还能再进来）', () => {
    signIn(ALL_MENUS);
    renderLayout();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('open-change-password'));

    expect(screen.getByRole('dialog', { name: '修改口令' })).toBeInTheDocument();
  });
});

/**
 * 「退出所有设备」入口的落点与步数。
 *
 * <p>落点与「修改口令」相同（会话级动作、不依赖任何菜单码、必须在基座），
 * 但**步数不同**：改密点开就是表单，它点开必须先问一句。差别来自后果落在谁身上 ——
 * 改密踢的是自己，用户当场就能感知；退出所有设备踢的是**别的设备上那些会话**，
 * 那些用户看不见，而且可能正在填一张表单。
 *
 * <p>第二条用例因此是本组的重点：它钉的是**"点一下不会直接发出去"**。
 * 只断言"弹窗出现了"是不够的 —— 一个"先发请求、再弹确认"的实现同样会弹窗。
 */
describe('BasicLayout · 退出所有设备入口', () => {
  beforeEach(() => {
    localStorage.clear();
    session.clear();
    vi.restoreAllMocks();
  });

  it('一个菜单码都没有时入口仍在（它不依赖任何菜单码）', () => {
    signIn([]);
    renderLayout();

    expect(screen.getByTestId('open-logout-all')).toBeInTheDocument();
  });

  it('点它只打开确认，一个请求都不发 —— 这个动作影响的是别的设备', () => {
    const logoutAll = vi.spyOn(auth, 'logoutAll').mockResolvedValue();
    signIn(ALL_MENUS);
    renderLayout();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('open-logout-all'));

    expect(screen.getByRole('dialog', { name: '退出所有设备' })).toBeInTheDocument();
    expect(logoutAll).not.toHaveBeenCalled();
  });

  it('确认之后才真的调用，且只调一次', async () => {
    const logoutAll = vi.spyOn(auth, 'logoutAll').mockResolvedValue();
    signIn(ALL_MENUS);
    renderLayout();

    fireEvent.click(screen.getByTestId('open-logout-all'));
    fireEvent.click(screen.getByTestId('confirm-logout-all'));

    await waitFor(() => expect(logoutAll).toHaveBeenCalledTimes(1));
  });
});

function jsonResponse(body: unknown): Response {
  return {
    status: 200,
    ok: true,
    statusText: '',
    headers: { get: () => null },
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

/**
 * `/api/auth/me` 的成功响应。
 *
 * <p>照契约的真实形态造：信封是 `{ code, message, data }`，`code` 为 `0`，
 * 权限在 `data.permissions` 里。**替身造错形状，测出来的就是替身的属性**
 * （ADR-0044 花一小时换来的一条）。
 */
function meResponse(permissions: readonly string[]): Response {
  return jsonResponse({
    code: 0,
    message: 'ok',
    data: {
      userId: 7,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['SUPER_ADMIN'],
      permissions: [...permissions],
      mustChangePassword: 0,
    },
  });
}

/**
 * 「重新检查权限」—— 被授权之后不必重新登录的那条路。
 *
 * <p>本组刻意**不替换 `auth.reload`**，而是 stub 掉 `fetch` 让真实实现跑：
 * 要验的正是"一次 `/me` → 写回会话 → 导航栏多出入口"这条链路。把通道换成替身，
 * 被测的东西就变成了替身。
 *
 * <p>重点是那两条容易被做错的分支：**"没有变化"也要说出来**（不说的话用户会
 * 以为点漏了、反复点，而真实原因常是授错了账号），以及**失败之后不能把人踢出去**。
 */
describe('BasicLayout · 重新检查权限', () => {
  beforeEach(() => {
    localStorage.clear();
    session.clear();
  });

  afterEach(() => {
    // 与改密对话框测试同一顺序：先卸载再清会话，免得 clear() 触发的通知落在 act 之外
    cleanup();
    vi.unstubAllGlobals();
    session.clear();
  });

  it('一个菜单码都没有时入口仍在 —— 最需要它的正是这个账号', () => {
    signIn([]);
    renderLayout();

    expect(screen.getByTestId('refresh-permissions')).toBeInTheDocument();
  });

  it('点它打一次 /me：导航栏当场多出入口，并说明新增了几项', async () => {
    const app = MICRO_APPS[0];
    signIn([]);
    const fetchMock = vi.fn(async () => meResponse([app.menuPermission]));
    vi.stubGlobal('fetch', fetchMock);
    renderLayout();

    expect(screen.queryByTestId(`nav-${app.name}`)).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('refresh-permissions'));

    // 这是整件事的目的：不必重新登录，入口就在眼前出现了
    expect(await screen.findByTestId(`nav-${app.name}`)).toBeInTheDocument();
    expect(await screen.findByTestId('refresh-permissions-result')).toHaveTextContent(
      '权限已更新：新增 1 项',
    );

    const [url] = fetchMock.mock.calls[0] as unknown as [string];
    expect(String(url)).toContain('/api/auth/me');
  });

  it('权限没变化时如实说"没有变化"，并指出该去核对什么', async () => {
    signIn([]);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => meResponse([])),
    );
    renderLayout();

    fireEvent.click(screen.getByTestId('refresh-permissions'));

    const result = await screen.findByTestId('refresh-permissions-result');
    // 界面毫无动静时，"没说变化"与"点了没生效"在用户眼里长得一样
    expect(result).toHaveTextContent('权限没有变化');
    expect(result).toHaveTextContent('授的是当前这个账号');
  });

  it('授权被收回时如实说移除了几项', async () => {
    const app = MICRO_APPS[0];
    signIn([app.menuPermission]);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => meResponse([])),
    );
    renderLayout();

    fireEvent.click(screen.getByTestId('refresh-permissions'));

    expect(await screen.findByTestId('refresh-permissions-result')).toHaveTextContent(
      '权限已更新：移除 1 项',
    );
    // 入口同时消失：可见入口与权限集合读的是同一个字段
    await waitFor(() => expect(screen.queryByTestId(`nav-${app.name}`)).not.toBeInTheDocument());
  });

  it('失败时给出原因、按钮恢复可点，且不把用户踢出去', async () => {
    const app = MICRO_APPS[0];
    signIn([app.menuPermission]);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ code: 50000, message: '服务器内部错误', data: null })),
    );
    renderLayout();

    fireEvent.click(screen.getByTestId('refresh-permissions'));

    const result = await screen.findByTestId('refresh-permissions-result');
    expect(result).toHaveTextContent('服务器内部错误');
    expect(result).toHaveAttribute('role', 'alert');
    // 一次失败只是"这次没查到"，不该顺手把用户登出，也不该卡住按钮让他只能刷新页面
    expect(session.isAuthenticated()).toBe(true);
    expect(screen.getByTestId('refresh-permissions')).toBeEnabled();
    expect(screen.getByTestId(`nav-${app.name}`)).toBeInTheDocument();
  });

  it('请求进行中按钮不可点 —— 防连点，且不能看起来像个坏掉的按钮', async () => {
    signIn([]);
    let release = (): void => {};
    vi.stubGlobal(
      'fetch',
      vi.fn(
        () =>
          new Promise<Response>((resolve) => {
            release = () => resolve(meResponse([]));
          }),
      ),
    );
    renderLayout();

    fireEvent.click(screen.getByTestId('refresh-permissions'));

    const button = screen.getByTestId('refresh-permissions');
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent('正在获取权限…');

    release();
    await waitFor(() => expect(screen.getByTestId('refresh-permissions')).toBeEnabled());
  });
});
