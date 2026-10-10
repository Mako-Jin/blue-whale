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
import './styles/index.less';

const APP_NAME = 'platform-event-console';
const logger = createLogger('boot:event-console');

/** React 根实例。基座会反复 mount / unmount，必须拿住它才能干净地销毁。 */
let root: Root | null = null;

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
}

/**
 * 独立运行分支。
 *
 * 这不是"兜底代码"，是**必须保留**的开发方式：子应用能单独 `pnpm dev` 打开，
 * 改一行不用等基座起来，调试成本差很多。删掉它只会让日常开发变慢。
 */
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  logger.info('以独立应用模式启动（未被基座加载）');
  render();
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
