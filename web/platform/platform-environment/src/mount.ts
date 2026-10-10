/**
 * 挂载点解析。
 *
 * 一个子应用有**两种活法**，挂载点来源不同：
 *
 * | 运行方式          | 容器来源                        |
 * | ----------------- | ------------------------------- |
 * | 被基座加载        | 基座通过 `props.container` 传入 |
 * | 独立启动（pnpm dev） | 本应用自建                      |
 *
 * 两种都要能用，且**共用同一份渲染代码** —— 分开写两套入口，迟早漂移。
 */

/** 本应用在 `index.html` 里声明的根节点 id。 */
export const ROOT_ID = 'root';

/** 独立运行时若 `index.html` 里没有根节点，用这个 id 现场建一个。 */
export const SELF_BUILT_ID = 'environment-root';

export type MountMode = 'host' | 'standalone';

export type MountTarget = {
  /** 真正挂 React 根实例的元素 */
  element: HTMLElement;
  /** 挂载点从哪来，用于在界面上显示当前运行方式 */
  mode: MountMode;
};

/**
 * 在 `parent` 内找一个带 `id` 的子元素，找不到就建一个。
 *
 * 为什么不直接渲染进 `parent` 本身：被基座加载时，qiankun 卸载会**清空容器的
 * innerHTML**。把自己的根节点直接当容器用，就等于和基座的清理动作抢同一个节点，
 * 卸载后残留什么全看时序。多套一层自己的 div，清理边界就清楚了。
 */
function ensureChild(parent: HTMLElement, id: string): HTMLElement {
  const existing = parent.querySelector<HTMLElement>(`#${id}`);
  if (existing) {
    return existing;
  }

  const created = parent.ownerDocument.createElement('div');
  created.id = id;
  parent.appendChild(created);
  return created;
}

/**
 * 解析挂载点。
 *
 * @param container 基座传入的容器；独立运行时为 `undefined`
 */
export function resolveMountPoint(container?: HTMLElement | null): MountTarget {
  // 被 qiankun 加载：用基座给的容器，在里面找/建自己的根节点。
  if (container) {
    return { element: ensureChild(container, ROOT_ID), mode: 'host' };
  }

  // 独立启动：用自建容器 —— 优先复用 index.html 里的根节点，
  // 万一那份 HTML 被改掉了也不会白屏，现场建一个顶上。
  const declared = document.getElementById(ROOT_ID);
  if (declared) {
    return { element: declared, mode: 'standalone' };
  }

  const selfBuilt = document.createElement('div');
  selfBuilt.id = SELF_BUILT_ID;
  document.body.appendChild(selfBuilt);
  return { element: selfBuilt, mode: 'standalone' };
}
