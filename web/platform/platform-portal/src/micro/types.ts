/**
 * 微前端相关的类型。
 *
 * 这里的类型全部用 `type` 而不是 `interface` —— qiankun 的泛型约束是
 * `T extends Record<string, any>`，而 interface 不会获得隐式索引签名，
 * 会直接编译不过。这不是风格偏好，是硬约束。
 */

import type { HostAuthBridge } from 'common-tools/micro';

/** 子应用注册信息。 */
export type MicroAppMeta = {
  /**
   * 应用名。
   *
   * **必须与子应用 `vite-plugin-qiankun` 插件里声明的名字逐字一致。**
   * 插件在 `window` 上以这个名字挂了一个生命周期壳，名字写错不会有任何报错，
   * 只会一直卡在"加载中"——因为壳永远等不到子应用来认领它。
   */
  name: string;
  /** 路由前缀，同时作为 basename 下发给子应用 */
  routePath: string;
  /** 导航与加载态里显示的名字 */
  title: string;
  /** 开发期子应用 dev server 的端口 */
  devPort: number;
  /** 生产期子应用的静态资源目录（即以子应用路径为 base 构建的产物） */
  prodEntry: string;
  /** 一句话说明，显示在加载态里 */
  summary: string;
};

/**
 * 下发给子应用的全局状态。
 *
 * 刻意保持极小：**只放"基座知道、子应用也需要"的东西**。
 * 子应用自己的业务状态不该走这里，否则这个对象会长成上帝对象，
 * 且每次变更都会广播给所有已挂载的子应用。
 *
 * `roles` / `permissions` 属于这里，是因为**权限判定由基座单点计算**
 * （`docs/decisions/0018` 第 3 条）：判定结果是一份全应用共用的横切事实，
 * 与 `theme` / `locale` 同一性质；而"谁能看到什么"的逻辑不该在每个子应用里各写一遍。
 */
export type GlobalState = {
  theme: 'light' | 'dark';
  locale: string;
  userId: string | null;
  /** 角色编码集合 */
  roles: string[];
  /** 权限标识集合。**标识由后端定义，前端不得拼接自造** */
  permissions: string[];
};

/**
 * 下发给子应用的 props（qiankun 会把它展开进 `mount(props)`）。
 *
 * 前四个与 qiankun 的 `MicroAppStateActions` 对齐，但**多了一个 `getGlobalState`**：
 * qiankun 的 `initGlobalState` 只返回 onGlobalStateChange / setGlobalState /
 * offGlobalStateChange，**没有 getGlobalState**。基座想读当前值必须自己留一份副本。
 */
export type MicroAppProps = {
  /** 子应用内部路由的挂载前缀 */
  basename: string;
  getGlobalState: () => GlobalState;
  setGlobalState: (state: Partial<GlobalState>) => void;
  onGlobalStateChange: (
    callback: (state: GlobalState, prevState: GlobalState) => void,
    fireImmediately?: boolean,
  ) => void;
  offGlobalStateChange: () => void;
  /**
   * 认证能力（令牌按需取、刷新由基座合并）。
   *
   * 子应用要用它来发已鉴权请求。令牌**值**不下发，只下发"取令牌"与"要一次刷新"
   * 两个动作 —— 理由见 `loader.ts` 的 `createHostAuthBridge`。
   */
  auth: HostAuthBridge;
};
