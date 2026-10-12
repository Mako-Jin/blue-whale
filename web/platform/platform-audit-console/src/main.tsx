import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import {
  renderWithQiankun,
  qiankunWindow,
  type QiankunProps,
} from 'vite-plugin-qiankun/dist/helper';
import { bindHost, createLogger, unbindHost } from 'common-tools';
import type { HostProps } from 'common-tools/micro';
import App from './App';
import { ensureStandaloneSession, initializeAuth } from './auth';
import { connectPermissionSource } from './auth/permissions';
import './styles/index.less';

const APP_NAME = 'platform-audit-console';
const logger = createLogger('boot:audit');

/** React 根实例。基座会反复 mount / unmount，必须拿住它才能干净地销毁。 */
let root: Root | null = null;

/** 权限来源的断开函数。与根实例同生命周期 —— 漏断开会在下一次挂载时收到重复推送 */
let disconnectPermissions: (() => void) | null = null;

function render(container?: HTMLElement) {
  // 被基座加载时挂进基座给的容器；独立运行时退回自己的 #root
  const mountPoint = container
    ? container.querySelector<HTMLElement>('#root')
    : document.getElementById('root');

  if (!mountPoint) {
    logger.error('未找到 #root 挂载点，请检查 index.html');
    return;
  }

  root = createRoot(mountPoint);
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

function destroy() {
  root?.unmount();
  root = null;
  disconnectPermissions?.();
  disconnectPermissions = null;
}

/**
 * 独立运行分支。
 *
 * <p>保留它不是"兜底代码"，而是必须保留的开发方式：改一行不用等基座起来。
 * 与 log-console 的区别是这里多两步 —— **先接好认证与权限，再渲染**：
 * 本应用的每个请求都是已鉴权请求，没有令牌时渲染出来只会是一屏错误。
 */
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  logger.info('以独立应用模式启动（未被基座加载）');

  void (async () => {
    initializeAuth();
    disconnectPermissions = connectPermissionSource();
    try {
      await ensureStandaloneSession();
    } catch (cause) {
      logger.error('独立运行的会话准备失败（后续请求会以未认证失败）', {
        message: cause instanceof Error ? cause.message : String(cause),
      });
    }
    render();
  })();
}

/**
 * 注册 qiankun 生命周期。
 *
 * <p>顺序有讲究：`bindHost` 必须在 `initializeAuth` 之前 ——
 * 认证端口要靠基座下发的 `auth` 判断自己是"被基座加载"还是"独立运行"，
 * 反了就会误判成独立运行，然后自己建一份会话（两份会话并存，刷新各自失效）。
 */
renderWithQiankun({
  bootstrap() {
    logger.info('bootstrap');
  },

  mount(props: QiankunProps) {
    logger.info('mount', {
      appName: props.name,
      basename: props.basename,
      hasContainer: Boolean(props.container),
      hasAuthBridge: Boolean((props as HostProps).auth),
    });
    bindHost(props as HostProps, APP_NAME);
    initializeAuth();
    disconnectPermissions = connectPermissionSource();
    render(props.container);
  },

  unmount() {
    logger.info('unmount');
    destroy();
    unbindHost();
  },

  update(props: QiankunProps) {
    logger.info('update', props);
  },
});
