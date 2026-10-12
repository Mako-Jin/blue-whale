import { loadMicroApp, type MicroApp } from 'qiankun';
import { createLogger } from 'common-tools';
import type { HostAuthBridge } from 'common-tools/micro';
import { auth } from '@/auth';
import { resolveEntry } from './apps';
import { getGlobalState, offGlobalStateChange, onGlobalStateChange, setGlobalState } from './state';
import type { MicroAppMeta, MicroAppProps } from './types';

const logger = createLogger('micro:qiankun');

/**
 * 组装下发给子应用的**认证能力**。
 *
 * <p>子应用要发已鉴权请求（例如管理类接口），但它拿不到令牌 ——
 * 令牌只在基座的内存会话里。这里给出的是"按需取 + 按需刷新"两个动作，
 * 而不是令牌本身：**令牌值一旦下发就会陈旧**（基座静默刷新一次之后，
 * 子应用手里那份已经作废），而刷新必须是**全应用一次**
 * （刷新令牌轮换 + 重放检测，见 `docs/decisions/0018` 第 7 条）。
 *
 * <p>不传"清空会话"：会话失效是全局事实，清空只该有 `createAuthChannel`
 * 的 `onAuthLost` 一个入口，基座那边也因此**刻意不传** `onAuthLost` 给通道。
 */
function createHostAuthBridge(): HostAuthBridge {
  return {
    getAccessToken: () => auth.session.getAccessToken(),
    // 通道内部已做会话级合并：多个子应用同时要求刷新，最终只会打一次刷新接口
    reauthenticate: () => auth.reauthenticate(),
  };
}

/** 组装下发给子应用的 props。 */
export function createMicroAppProps(meta: MicroAppMeta): MicroAppProps {
  return {
    basename: meta.routePath,
    getGlobalState,
    setGlobalState,
    onGlobalStateChange,
    offGlobalStateChange,
    auth: createHostAuthBridge(),
  };
}

/**
 * 加载并挂载一个子应用。
 *
 * ### 为什么用 `loadMicroApp` 而不是 `registerMicroApps` + `start`
 *
 * 参考项目用的是后者：一个常驻的 `#sub-app-container`，靠 `activeRule` 匹配路由。
 * 那条路在这里有两个具体麻烦：
 * 一是容器必须**在 `start()` 之前就存在于 DOM**，否则激活时找不到挂载点；
 * 二是子应用的真实 DOM 落在全局容器里，而路由出口在另一个位置，
 * 位置被拆成两处，排查问题时很难说清"到底是谁在管这块区域"。
 *
 * `loadMicroApp` 把挂载点交给调用方，于是**路由出口本身就是容器**，
 * 加载、卸载与 React 组件的生命周期一一对应，位置也只有一处。
 * 代价是没有 qiankun 自带的 `prefetch`，需要时再显式调 `prefetchApps`。
 *
 * ### 为什么关掉沙箱
 *
 * `sandbox: false` 是 Vite 子应用的必要选择，而不是省事：
 * 子应用在开发期是一堆 ESM 模块，由浏览器原生 `import()` 加载，
 * qiankun 的 JS 沙箱靠 `with(window)` 包住脚本来劫持全局变量，
 * 而**它包不住模块作用域** —— 沙箱开着也拦不到子应用模块里的全局访问，
 * 只会把 `document` / `history` / 定时器那部分补丁打上，白白增加出错面。
 *
 * 于是子应用之间真正需要隔离的只剩样式，见 `MicroAppContainer` 的说明。
 */
export function mountMicroApp(meta: MicroAppMeta, container: HTMLElement): MicroApp {
  const entry = resolveEntry(meta);
  logger.info(`加载子应用：${meta.name}`, { entry });

  return loadMicroApp(
    {
      name: meta.name,
      entry,
      container,
      props: createMicroAppProps(meta),
    },
    {
      sandbox: false,
      /**
       * Vite 的 HMR 客户端脚本必须排除在资源劫持之外。
       * 不排除的话 qiankun 会去改写它的加载地址，结果就是子应用的热更新悄悄失效 ——
       * 现象是"改了代码页面不动"，很容易被误判成子应用自己的问题。
       */
      excludeAssetFilter: (url: string) =>
        url.includes('react-refresh') || url.includes('@react-refresh'),
    },
  );
}
