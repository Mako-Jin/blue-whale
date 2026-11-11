import React from 'react';
import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import { renderWithQiankun } from 'vite-plugin-qiankun/dist/helper';
import App from './App';
import { setPlatformHostProps } from './api/client';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计
initAuditLogging();

let root: ReactDOM.Root | null = null;

function render(props?: { container?: HTMLElement }) {
  const container = props?.container ?? document;
  const mountNode = container.querySelector('#root') as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <ThemeProvider>
        <ErrorBoundary>
          <App />
        </ErrorBoundary>
      </ThemeProvider>
    </React.StrictMode>,
  );
}

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {}

export async function mount(props: { container?: HTMLElement; getToken?: () => string | null }) {
  // 基座 accessToken 为内存态，经 qiankun props 注入读取器（独立运行时为 null）
  setPlatformHostProps(props);
  render(props);
}

export async function unmount() {
  root?.unmount();
  root = null;
  setPlatformHostProps(null);
}

// 把生命周期注册进 vite-plugin-qiankun 的桥接表：产物 index.html 由插件注入的脚本
// 从 window.moudleQiankunAppLifeCycles['whale-mall'] 取生命周期。缺这一步时基座内
// mount 的 Promise 永不 resolve —— 表现为白屏且无报错。
renderWithQiankun({
  bootstrap,
  mount,
  unmount,
  // 基座不调用 update；保留空实现以满足插件的生命周期类型
  update: () => Promise.resolve(),
});
