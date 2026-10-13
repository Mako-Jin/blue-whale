import { getHostGlobalState, isMicroApp, onHostGlobalStateChange } from './bridge';

/**
 * 子应用的**权限快照** —— "谁在登录、能做什么"的唯一来源。
 *
 * <h3>为什么要有它（而不是每个子应用各写一份）</h3>
 *
 * `platform-admin` 与 `platform-audit-console` 各持有一份几乎相同的实现，
 * 当时是**有意留着的重复**。本轮菜单与页面级过滤要接五个子应用 ——
 * 再抄三份就是五份，且每一份都要覆盖"基座下发 / 独立运行"两条分支、
 * 引用比对去抖、`ready` 三态这些细节。触发下沉的条件（出现第三个消费方）
 * 已经到了，因此收拢到这里。
 *
 * <h3>为什么是一个模块级 store，而不是 React context</h3>
 *
 * 权限不是某个组件的局部状态，而是**全应用共用**的事实（视图过滤、按钮显隐、
 * 将来的路由都要用）。做成 context 也能用，但那样每个消费点都必须挂在
 * Provider 之下 —— 而"挂在 Provider 之下"一旦漏了，症状是权限恒为假、
 * 入口永远不出现，排查方向会先指向"权限没下发"。模块级 store 没有这个前提条件。
 *
 * <h3>数据从哪来</h3>
 *
 * - 被基座加载：`HostGlobalState.permissions` / `roles`（**基座算好下发**，
 *   子应用不得自行调用 `/api/auth/me`，见 `docs/decisions/0018` 第 3 条）
 * - 独立运行：子应用自己的会话（由调用方通过 {@link StandaloneIdentitySource} 注入）
 *
 * <h3>本模块是 React-free 的</h3>
 *
 * 它不引 React：`common-tools` 是一个与框架无关的内核，而"把它接到
 * `useSyncExternalStore`"是三行适配代码，放在各子应用里比把 React 拉进内核更合适。
 *
 * <h3>为什么区分 `ready`</h3>
 *
 * "没有权限"和"还不知道有没有权限"是两件事。前者该隐藏，后者该等一等
 * （默认放行会漏权限，默认隐藏会闪烁）。`userId` 非空即表示权限集合可信 ——
 * 一个真正没有任何权限的已登录用户，`ready` 也是 `true`。
 */

/** 身份快照。`userId` 为 null 表示"还没有身份"（未登录或尚未收到下发） */
export type IdentitySnapshot = {
  userId: string | null;
  roles: readonly string[];
  permissions: readonly string[];
};

/** 权限快照 = 身份 + 它是否可信 */
export type PermissionState = IdentitySnapshot & {
  /** 权限集合是否可信（未登录时为 false，此时"没有权限"不代表真的没有） */
  ready: boolean;
};

/**
 * 独立运行（未被基座加载）时的身份来源。
 *
 * <p>两个方法都要**可被延迟调用**：会话通常在 `connect()` 之后才建立
 * （独立运行分支是先接权限来源、再 `ensureSession`），因此实现里不能
 * 在构造时就把身份读死。
 */
export type StandaloneIdentitySource = {
  /** 读当前身份；尚未建立会话时返回 null */
  readStandaloneIdentity(): IdentitySnapshot | null;
  /** 订阅身份的变更（登录完成、刷新续期、登出），返回取消订阅函数 */
  onStandaloneIdentityChange(listener: () => void): () => void;
};

export type PermissionStore = {
  getState(): PermissionState;
  /** 订阅快照变化。适配 `useSyncExternalStore` 时直接传本函数即可 */
  subscribe(listener: () => void): () => void;
  /**
   * 接上权限来源，返回断开函数。
   *
   * <p>**必须在 `mount` 里调用、在 `unmount` 里断开**，不能写在模块顶层：
   * 子应用的模块在多次挂载之间是复用的，而基座桥在 `unbindHost` 时会清空全部订阅者 ——
   * 顶层订阅在第二次挂载后就不会再收到任何推送（症状是"第二次进入页面按钮全没了"）。
   */
  connect(): () => void;
  /** 是否拥有某个权限标识。**未就绪时返回 false** */
  has(code: string): boolean;
};

/** 共用的空数组/空状态：引用稳定，`useSyncExternalStore` 依赖它避免无谓重渲染 */
const EMPTY: readonly string[] = [];
const EMPTY_IDENTITY: IdentitySnapshot = { userId: null, roles: EMPTY, permissions: EMPTY };
export const EMPTY_PERMISSION_STATE: PermissionState = { ready: false, ...EMPTY_IDENTITY };

export function createPermissionStore(source: StandaloneIdentitySource): PermissionStore {
  let state: PermissionState = EMPTY_PERMISSION_STATE;

  const listeners = new Set<() => void>();

  /**
   * 只在**内容真的变了**时通知。
   *
   * <p>比对的是引用而不是逐项内容：数组来自会话快照或基座下发的那一次推送，
   * 在两次变更之间是同一个引用，所以"引用相同 ⇒ 内容相同"成立，而且比逐项比对便宜。
   * 若改成每次发布都通知，基座每推一次全局状态（例如切主题）就会让所有消费组件重渲染一轮。
   */
  const publish = (next: PermissionState): void => {
    if (
      next.ready === state.ready &&
      next.userId === state.userId &&
      next.roles === state.roles &&
      next.permissions === state.permissions
    ) {
      return;
    }
    state = next;
    for (const listener of Array.from(listeners)) listener();
  };

  const readFromHost = (): PermissionState => {
    const global = getHostGlobalState();
    const userId = global.userId ?? null;
    return {
      ready: userId !== null,
      userId,
      roles: global.roles ?? EMPTY,
      permissions: global.permissions ?? EMPTY,
    };
  };

  const readFromStandalone = (): PermissionState => {
    const identity = source.readStandaloneIdentity();
    if (!identity) {
      return EMPTY_PERMISSION_STATE;
    }
    return { ready: identity.userId !== null, ...identity };
  };

  const reset = (): void => {
    publish(EMPTY_PERMISSION_STATE);
  };

  return {
    getState: () => state,

    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    connect: () => {
      if (isMicroApp()) {
        // fireImmediately：立刻推一次当前值，避免首帧停在"未就绪"而让入口闪一下
        const stop = onHostGlobalStateChange(() => publish(readFromHost()), {
          fireImmediately: true,
        });
        return () => {
          stop();
          reset();
        };
      }

      publish(readFromStandalone());
      const stop = source.onStandaloneIdentityChange(() => publish(readFromStandalone()));
      return () => {
        // 两个分支都要复位：留下上一轮的权限，会让下一次挂载在"尚未收到推送"的瞬间
        // 拿着已经失效的权限集合去渲染入口
        stop();
        reset();
      };
    },

    has: (code) => state.ready && state.permissions.includes(code),
  };
}

/**
 * 按权限筛出可见的视图。
 *
 * <p>**五个子应用共用这一条规则**，因此把它写成函数而不是各写一次
 * `filter(state => state.permissions.includes(x))`：
 * 各写一次的话，"未就绪时怎么办"这个判断会在五处各有一个答案，
 * 而它恰恰是最容易写歪的一处（默认放行 = 漏权限；默认隐藏 = 首帧闪烁）。
 *
 * <p>规则只有两条：
 * <ol>
 *   <li>未就绪 ⇒ **空列表**。不确定就不显示 —— 收到推送后立刻会补上，
 *       而"先渲染再收回"会让人以为入口消失了；</li>
 *   <li>就绪 ⇒ 只留拥有权限的那些。</li>
 * </ol>
 *
 * <p>返回 `readonly` 数组：调用方不该往筛选结果里塞东西（那会让"可见集合"
 * 与"权限集合"开始各说各话）。
 */
export function visibleViews<T>(
  items: readonly T[],
  state: PermissionState,
  permissionOf: (item: T) => string,
): readonly T[] {
  if (!state.ready) {
    return [];
  }
  return items.filter((item) => state.permissions.includes(permissionOf(item)));
}
