/**
 * 基座下发的全局状态。
 *
 * 刻意用**可选的宽松类型**而不是基座的 `GlobalState`：
 * 子应用不能反向依赖基座（那是应用之间的引用），所以只按结构描述"我关心哪些字段"，
 * 基座将来加字段不需要子应用跟着改。
 */
export type HostGlobalState = {
  theme?: string;
  locale?: string;
  userId?: string | null;
  /** 角色编码集合，如 `SUPER_ADMIN` */
  roles?: string[];
  /**
   * 权限标识集合，如 `auth:user:create`。
   *
   * <p>**由基座算好下发，子应用只消费**（`docs/decisions/0018` 第 3 条）——
   * 子应用不要自己去调 `/api/auth/me`：那样每个子应用都会有一份自己的判定，
   * 迟早出现"某处只判了登录、忘了判权限"。
   *
   * <p>标识**由后端定义**，前端不得拼接自造。
   */
  permissions?: string[];
};

/**
 * 基座在 mount 时交给子应用的 props。
 *
 * 同样是结构化描述，**不 import qiankun 的类型** —— 本包不依赖任何运行时第三方库，
 * 这既是包本身的约束，也让这份桥接不被绑死在 qiankun 上。
 */
export type HostProps = {
  /** 应用名 */
  name?: string;
  /** 基座为子应用准备的容器元素 */
  container?: HTMLElement;
  /** 子应用内部路由的挂载前缀 */
  basename?: string;
  getGlobalState?: () => HostGlobalState;
  setGlobalState?: (state: Partial<HostGlobalState>) => void;
  /**
   * 基座原生的全局状态监听。
   *
   * **不要直接用它**，除非你确定整个子应用只有这一处消费者：qiankun 每个子应用
   * 只保留一个回调，第二次注册会覆盖第一个，先注册的那个静默失效。
   * 一律改用 `onHostGlobalStateChange()`（同一模块导出），它做了多路复用。
   */
  onGlobalStateChange?: (
    callback: (state: HostGlobalState, prevState: HostGlobalState) => void,
    fireImmediately?: boolean,
  ) => void;
  offGlobalStateChange?: () => void;
};
