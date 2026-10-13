import type { SessionStore } from 'common-tools/session';
import { beforeEach, describe, expect, it } from 'vitest';

import { consoleAuth } from '../src/auth';
import {
  connectPermissionSource,
  getPermissionState,
  hasPermission,
  subscribePermissions,
} from '../src/auth/permissions';

/**
 * 权限快照的行为。
 *
 * <p>测试环境里没有基座，因此走的是**独立运行分支**（来源是应用自己的会话）。
 * 分支选择本身是这条用例的一部分：`isMicroApp()` 为假时必须落到会话，
 * 而不是报错或返回空权限 —— 那正是子应用单跑时要能工作的那件事。
 */
function standaloneSession(): SessionStore {
  const session = consoleAuth.getStandaloneSession();
  if (!session) {
    throw new Error('测试环境不应存在宿主认证桥：独立运行分支必然成立');
  }
  return session;
}

function signIn(permissions: string[], roles: string[] = ['AUDITOR']): void {
  standaloneSession().set({
    user: { userId: 7, username: 'auditor', roles, permissions },
    tokens: { accessToken: 'access-1', refreshToken: 'refresh-1' },
  });
}

describe('权限快照', () => {
  beforeEach(() => {
    localStorage.clear();
    standaloneSession().clear();
  });

  it('未登录时 ready 为 false —— "还不知道有没有权限"不能等同于"没有权限"', () => {
    const stop = connectPermissionSource();

    expect(getPermissionState().ready).toBe(false);
    // 未就绪时判定一律为假：宁可少显示一个按钮，也不能默认放行
    expect(hasPermission('auth:user:create')).toBe(false);

    stop();
  });

  it('登录后 ready 变真，权限集合原样取自会话（不由本应用自行拼接）', () => {
    const stop = connectPermissionSource();

    signIn(['auth:user:read', 'auth:tenant:read']);

    const state = getPermissionState();
    expect(state.ready).toBe(true);
    expect(state.userId).toBe('7');
    expect(state.permissions).toEqual(['auth:user:read', 'auth:tenant:read']);
    expect(hasPermission('auth:user:read')).toBe(true);
    expect(hasPermission('auth:user:create')).toBe(false);

    stop();
  });

  it('会话变化会通知订阅者，登出后回到未就绪', () => {
    const stop = connectPermissionSource();
    let notifications = 0;
    const off = subscribePermissions(() => {
      notifications += 1;
    });

    signIn(['auth:user:read']);
    expect(notifications).toBe(1);

    standaloneSession().clear();
    expect(notifications).toBe(2);
    expect(getPermissionState().ready).toBe(false);

    off();
    stop();
  });

  it('断开后不再跟随会话变化，并回到空状态（防重复挂载留下重复订阅）', () => {
    const stop = connectPermissionSource();
    signIn(['auth:user:read']);

    stop();
    expect(getPermissionState().ready).toBe(false);

    signIn(['auth:user:create']);
    expect(getPermissionState().ready).toBe(false);
  });

  it('只有令牌变化时不通知（静默续期不该让整棵权限组件树重渲染）', () => {
    const stop = connectPermissionSource();
    signIn(['auth:user:read']);

    let notifications = 0;
    const off = subscribePermissions(() => {
      notifications += 1;
    });

    // 访问令牌换了，身份与权限没变 —— 快照引用没变，因此不发通知
    standaloneSession().updateTokens({ accessToken: 'access-2' });
    expect(notifications).toBe(0);

    // 权限真的变了就必须通知
    signIn(['auth:user:read', 'auth:user:create']);
    expect(notifications).toBe(1);

    off();
    stop();
  });
});
