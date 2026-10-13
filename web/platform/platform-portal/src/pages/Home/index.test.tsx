import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import Home from './index';
import { session } from '@/auth';
import { MICRO_APPS } from '@/micro';

/**
 * 概览页的入口列表**按菜单码过滤**。
 *
 * <p>这里刻意用真实会话（而不是替换 `@/auth/hooks`）：本页要验的正是
 * "权限集合 → 可见入口"这条链路，把消费层换掉就等于把被测的东西换掉。
 * 会话是模块级单例，因此每个用例前都清一次。
 */
function signIn(permissions: readonly string[]): void {
  session.set({
    user: {
      userId: 7,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['SUPER_ADMIN'],
      permissions: [...permissions],
      mustChangePassword: false,
    },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

/** 被 `RequireMenu` 弹回时带的那份 state */
type Denied = { denied: { path: string; permission: string } };

function renderHome(state?: Denied) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/', state }]}>
      <Home />
    </MemoryRouter>,
  );
}

/** 全部应用的菜单码 —— 等价于"管理员看得到所有入口" */
const ALL_MENUS = MICRO_APPS.map((app) => app.menuPermission);

describe('Home', () => {
  beforeEach(() => {
    localStorage.clear();
    session.clear();
  });

  it('渲染就绪提示', () => {
    signIn(ALL_MENUS);
    renderHome();

    expect(screen.getByText('平台门户已就绪')).toBeInTheDocument();
  });

  it('持有全部菜单码时列出注册表里的每一个入口', () => {
    signIn(ALL_MENUS);
    renderHome();

    for (const app of MICRO_APPS) {
      expect(screen.getByText(app.title)).toBeInTheDocument();
      expect(screen.getByText(app.name)).toBeInTheDocument();
    }
    expect(screen.queryByTestId('home-empty')).not.toBeInTheDocument();
  });

  it('没有菜单码的入口不出现 —— 否则这里会成为"点了就被弹回"的假开关集合', () => {
    const [visible, hidden] = MICRO_APPS;
    signIn([visible.menuPermission]);
    renderHome();

    expect(screen.getByText(visible.title)).toBeInTheDocument();
    expect(screen.queryByText(hidden.title)).not.toBeInTheDocument();
    // 还有可见入口，就不该出现"一个都没有"的提示
    expect(screen.queryByTestId('home-empty')).not.toBeInTheDocument();
  });

  it('一个入口都没有时给出空态，并说清该找谁要什么', () => {
    signIn([]);
    renderHome();

    const empty = screen.getByTestId('home-empty');
    expect(empty.textContent).toContain('ui:*:view');
  });

  it('被路由守卫弹回时解释原因，并指名缺的是哪一个码', () => {
    signIn([MICRO_APPS[0].menuPermission]);
    renderHome({ denied: { path: '/admin', permission: 'ui:admin:view' } });

    const banner = screen.getByTestId('home-denied');
    // 没有这段解释，"敲了地址什么都没发生"会被当成应用坏了
    expect(banner.textContent).toContain('/admin');
    expect(banner.textContent).toContain('ui:admin:view');
  });

  it('正常进入概览时不出现"被拒绝"的提示', () => {
    signIn(ALL_MENUS);
    renderHome();

    expect(screen.queryByTestId('home-denied')).not.toBeInTheDocument();
  });
});
