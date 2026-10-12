import { session } from '@/auth';
import { setGlobalState } from './state';

/**
 * 把会话结果同步到下发给子应用的全局状态。
 *
 * <h3>为什么是模块级订阅，而不是写在布局组件的 `useEffect` 里</h3>
 *
 * 全局状态必须在**任何路由下都正确**，而登录页 `/login` 并不渲染 `BasicLayout` ——
 * 写在布局里的话，恰恰在最需要把"已登出"广播出去的时刻（401 掉线、主动登出后跳登录页）
 * 没有人在同步，子应用会继续持有上一个身份的权限集合。
 *
 * <h3>为什么下发的是"算好的集合"而不是原始令牌</h3>
 *
 * 依据 `docs/decisions/0018` 第 3 条：**权限判定由基座单点计算**，子应用只消费结果。
 * 下发令牌则等于让每个子应用各自去调 `/api/auth/me`，判定结果就会分裂成若干份。
 *
 * @returns 取消订阅的函数（应用运行期不需要调用，留着便于测试与热更新清理）
 */
export function startSessionSync(): () => void {
  const push = (): void => {
    const user = session.getSnapshot().user;
    setGlobalState({
      userId: user ? String(user.userId) : null,
      roles: user?.roles ?? [],
      permissions: user?.permissions ?? [],
    });
  };

  // 先推一次当前值：子应用可能在会话建立之前就被挂载，它需要立刻收到一份正确的初值
  push();
  return session.subscribe(push);
}
