import { beforeEach, describe, expect, it } from 'vitest';

import { session } from '@/auth';
import { startSessionSync } from './session-sync';
import { getGlobalState } from './state';

const USER = {
  userId: 7,
  username: 'admin',
  displayName: '系统管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['auth:user:create', 'auth:role:read'],
};

describe('会话 → 全局状态的同步', () => {
  beforeEach(() => {
    session.clear();
    localStorage.clear();
  });

  it('会话建立后把身份、角色与权限一起下发（子应用据此判定，不再自己去问 /me）', () => {
    const stop = startSessionSync();

    // 起点：未登录
    expect(getGlobalState().userId).toBeNull();
    expect(getGlobalState().roles).toEqual([]);
    expect(getGlobalState().permissions).toEqual([]);

    session.set({ user: USER, tokens: { accessToken: 'a', refreshToken: 'r' } });

    const state = getGlobalState();
    expect(state.userId).toBe('7');
    expect(state.roles).toEqual(['SUPER_ADMIN']);
    expect(state.permissions).toEqual(['auth:user:create', 'auth:role:read']);

    stop();
  });

  it('登出后把身份与权限一并清空（否则子应用会继续持有上一个身份的权限集合）', () => {
    const stop = startSessionSync();

    session.set({ user: USER, tokens: { accessToken: 'a', refreshToken: 'r' } });
    session.clear();

    const state = getGlobalState();
    expect(state.userId).toBeNull();
    expect(state.roles).toEqual([]);
    expect(state.permissions).toEqual([]);

    stop();
  });

  it('取消订阅后不再跟随会话变化（避免热更新后留下重复订阅）', () => {
    const stop = startSessionSync();
    stop();

    session.set({ user: USER, tokens: { accessToken: 'a', refreshToken: 'r' } });

    expect(getGlobalState().userId).toBeNull();
  });
});
