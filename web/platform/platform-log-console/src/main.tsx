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
import { connectPermissions } from './auth/permissions';
import { consoleAuth } from './console';
import './styles/index.less';

const APP_NAME = 'platform-log-console';
const logger = createLogger('boot:log-console');

/** React 根实例。基座会反复 mount / unmount，必须拿住它才能干净地销毁。 */
let root: Root | null = null;

/** 权限来源的断开函数。与根实例同生命周期 —— 漏断开会在下一次挂载时收到重复推送 */
let disconnectPermissions: (() => void) | null = null;

function render(container?: HTMLElement) {
  // 被基座加载时挂进基座给的容器；独立运行时退回自己的 #root。
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
 * 这不是"兜底代码"，是**必须保留**的开发方式：子应用能单独 `pnpm dev` 打开，
 * 改一行不用等基座起来，调试成本差很多。删掉它只会让日常开发变慢。
 *
 * <p>先把会话准备好再渲染：服务端日志面板的查询接口要鉴权（`log:client:read`），
 * 没有会话时它会立刻拿到 40100。`ensureSession` 在基座内是空操作，
 * 因此这里不需要分情况 —— 独立运行时它会用持久化刷新令牌续期，续不上才用开发账号登录。
 *
 * <p>权限来源**先接上再等会话**：`connectPermissions` 会订阅这条通道，
 * 而独立运行时的那次身份变更（登录/续期完成）正是通过它推出来的。
 * 反过来的顺序会让"登录完成了但界面不知道"，症状是两栏都不出现、刷新一下才正常。
 */
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  logger.info('以独立应用模式启动（未被基座加载）');
  disconnectPermissions = connectPermissions();
  void consoleAuth.ensureSession().finally(() => render());
}

/**
 * 注册 qiankun 生命周期。
 *
 * `props` 由基座下发，类型上带 `any` 索引签名，这里按 `HostProps` 取用；
 * 具体的字段契约见 `common-tools/micro`。
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
    });
    bindHost(props as HostProps, APP_NAME);
    // 顺序与 admin 一致：先绑定宿主，再确定权限来源 ——
    // `connectPermissions` 靠 `isMicroApp()` 判断走哪条分支，提前调用会误判成独立运行
    disconnectPermissions = connectPermissions();
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
