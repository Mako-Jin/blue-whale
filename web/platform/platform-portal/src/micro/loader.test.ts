import { beforeEach, describe, expect, it } from 'vitest';

import { session } from '@/auth';
import { MICRO_APPS } from './apps';
import { createMicroAppProps } from './loader';

const APP = MICRO_APPS[0];

const USER = {
  userId: 7,
  username: 'admin',
  displayName: '系统管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['auth:user:create'],
};

describe('下发给子应用的 props', () => {
  beforeEach(() => {
    session.clear();
    localStorage.clear();
  });

  it('basename 取路由前缀 —— 子应用内路由的前缀只有这一个来源', () => {
    expect(createMicroAppProps(APP).basename).toBe(APP.routePath);
  });

  it('认证能力按需取令牌：令牌值不进全局状态（否则基座静默刷新后它就是一份作废的令牌）', () => {
    const props = createMicroAppProps(APP);

    // 未登录：取不到令牌，但函数本身始终存在（子应用据此判断"要不要带 Bearer"）
    expect(props.auth.getAccessToken()).toBeNull();

    session.set({ user: USER, tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' } });
    expect(props.auth.getAccessToken()).toBe('access-1');

    // 每次调用都问会话，因此基座刷新之后子应用下一次拿到的一定是新令牌
    session.updateTokens({ accessToken: 'access-2' });
    expect(props.auth.getAccessToken()).toBe('access-2');
  });

  it('下发的是能力而不是状态：只有"取令牌"与"要一次刷新"，没有"清空会话"', () => {
    const props = createMicroAppProps(APP);

    expect(Object.keys(props.auth)).toEqual(['getAccessToken', 'reauthenticate']);
    // 全局状态里不该出现令牌字段 —— 它会被广播给所有已挂载的子应用
    expect(Object.keys(props.getGlobalState())).not.toContain('accessToken');
    expect(Object.keys(props.getGlobalState())).not.toContain('refreshToken');
  });
});
