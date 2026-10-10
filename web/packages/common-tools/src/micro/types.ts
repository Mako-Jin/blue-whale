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
  onGlobalStateChange?: (
    callback: (state: HostGlobalState, prevState: HostGlobalState) => void,
    fireImmediately?: boolean,
  ) => void;
  offGlobalStateChange?: () => void;
};
