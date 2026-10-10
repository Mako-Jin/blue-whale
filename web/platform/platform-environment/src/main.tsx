import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import {
  qiankunWindow,
  renderWithQiankun,
  type QiankunProps,
} from 'vite-plugin-qiankun/dist/helper';
import { bindHost, createLogger, unbindHost } from 'common-tools';
import type { HostProps } from 'common-tools/micro';
import App from './App';
import { resolveMountPoint } from './mount';
import './styles/index.less';

/** 子应用名。**必须与基座 `src/micro/apps.ts` 里注册的名字逐字一致。** */
const APP_NAME = 'platform-environment';
const logger = createLogger('boot:environment');

/** React 根实例。基座会反复 mount / unmount，必须拿住它才能干净地销毁。 */
let root: Root | null = null;

/**
 * 挂载点解析交给 `resolveMountPoint`，本应用**只有这一条渲染路径**：
 *
 * - 被基座加载（qiankun）：`container` 由基座传入，用它；
 * - 独立启动（`pnpm dev`）：`container` 为空，用自建容器。
 *
 * 两条路共用同一个 `App`，因此"能在基座里跑"与"能单独跑"不会各自漂移。
 */
function render(container?: HTMLElement | null) {
  const { element, mode } = resolveMountPoint(container);
  logger.info('挂载渲染根', { mode, mountPoint: `#${element.id}` });

  root = createRoot(element);
  root.render(
    <StrictMode>
      <App mode={mode} />
    </StrictMode>,
  );
}

function destroy() {
  // 先卸载 React 再让 main.tsx 的其他清理工作接手：unmount 会同步跑完所有 effect 清理，
  // 舞台（WebGL 上下文、动画帧、ResizeObserver）在这一步就被释放掉了。
  root?.unmount();
  root = null;
}

/**
 * 独立运行分支。
 *
 * 不是"兜底代码"：子应用能单独 `pnpm dev` 打开，改一行不用等基座起来。
 * 对三维应用尤其重要 —— 渲染问题在基座里排查要同时面对三层日志。
 */
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  logger.info('以独立运行模式启动（未被基座加载）');
  render();
}

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
