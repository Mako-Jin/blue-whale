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
import { consoleAuth } from './auth';
import { connectPermissionSource } from './auth/permissions';
import './styles/index.less';

const APP_NAME = 'platform-admin';
const logger = createLogger('boot:admin');

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
 * 与 log-console 的区别是这里多两步 —— **先把权限来源接上，再备会话，最后才渲染**：
 * 本应用的每个页面都要发已鉴权请求，没有令牌时渲染出来只会是一屏错误。
 *
 * <p>顺序不能颠倒（权限来源先接、会话后备）：`connectPermissionSource()` 会订阅会话，
 * 而独立运行时"登录完成"正是通过那次订阅推出来的 —— 反了就会"登录好了但界面不知道"，
 * 症状是六个标签页一个都不出现、刷新一下才正常。
 */
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  logger.info('以独立应用模式启动（未被基座加载）');

  void (async () => {
    disconnectPermissions = connectPermissionSource();
    try {
      await consoleAuth.ensureSession();
    } catch (cause) {
      // 失败也渲染：没有会话只影响需要鉴权的面板，让它白屏是更差的处置
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
 * <p>顺序有讲究：`bindHost` 必须在 `connectPermissionSource()` 之前 ——
 * 权限 store 在 `connect` 时**读一次**身份，而"用宿主下发的身份还是自己的会话"
 * 这个判据是"**有没有宿主桥**"。排反了就会把"被基座加载"读成"独立运行"，
 * 于是自建起第二份会话（两份会话并存，刷新各自失效）。
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
