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
 * 基座提供给子应用的**认证能力**。
 *
 * <p><b>为什么下发的是"能力"而不是令牌本身。</b>把令牌值放进全局状态下发看起来更省事，
 * 但它会陈旧：访问令牌是短效的，基座在后台静默刷新一次之后，
 * 子应用手里那份就变成了已作废的令牌，而它自己并不知道 —— 症状是
 * "刚才还好好的，突然所有请求都 401"。函数则是**按需取**，取到的一定是最新的一份。
 *
 * <p><b>为什么刷新这个动作也由基座持有。</b>刷新令牌是轮换的，且重复使用会被服务端
 * 判为重放攻击并撤销该用户全部会话（`docs/decisions/0018` 第 7 条）。因此"刷新"
 * 在全应用里只能发生在一个地方；子应用若各自实现一份，就把"并发刷新"这个坑
 * 从基座搬到了每个子应用里。子应用只表达"我需要一次刷新"，合并与次数由基座负责。
 *
 * <p>注意它**不提供"清空会话"**：会话失效是全局事实，清空只该有基座一个入口
 * （`createAuthChannel` 的 `onAuthLost`），子应用清不掉、也不必清。
 */
export type HostAuthBridge = {
  /** 取当前访问令牌，未登录或已失效时返回 `null` */
  getAccessToken(): string | null;
  /**
   * 请基座刷新一次会话。
   *
   * <p>并发调用会被基座合并成**一次**刷新，返回值表示"这次请求之后会话是否可用"。
   * 刷新失败时基座会清空会话并走它自己的跳转，子应用只需把当前请求当失败处理。
   */
  reauthenticate(): Promise<boolean>;
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
   * 认证能力（令牌按需取、刷新由基座合并）。
   *
   * <p>**可选**：独立运行时没有基座。需要发已鉴权请求的子应用应当把它当作
   * "一种会话来源"，与"自建会话"走同一段装配代码（见各子应用的 `auth/`）。
   */
  auth?: HostAuthBridge;
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
