import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import { renderWithQiankun } from 'vite-plugin-qiankun/dist/helper';
import App from './App';
import './index.css';
import './world-ui.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

let root: ReactDOM.Root | null = null;

/** qiankun 注入的 props（基座只用到 container 与 getToken） */
interface QiankunProps {
  container?: HTMLElement;
  getToken?: () => string | null;
}

function render(props?: QiankunProps) {
  const container = props?.container ?? document;
  const mountNode = container.querySelector('#root') as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <ThemeProvider>
      <AntdApp>
        <ErrorBoundary>
          <App getToken={props?.getToken} />
        </ErrorBoundary>
      </AntdApp>
    </ThemeProvider>,
  );
}

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {}

export async function mount(props: QiankunProps) {
  render(props);
}

export async function unmount() {
  root?.unmount();
  root = null;
}

// 把生命周期注册进 vite-plugin-qiankun 的桥接表：产物 index.html 由插件注入的脚本
// 从 window.moudleQiankunAppLifeCycles['whale-world'] 取生命周期。缺这一步时基座内
// mount 的 Promise 永不 resolve —— 表现为白屏且无报错。
renderWithQiankun({
  bootstrap,
  mount,
  unmount,
  // 基座不调用 update；保留空实现以满足插件的生命周期类型
  update: () => Promise.resolve(),
});
