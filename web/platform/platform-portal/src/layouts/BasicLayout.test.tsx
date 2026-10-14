import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
