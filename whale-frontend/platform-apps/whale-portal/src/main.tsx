import React from 'react';
import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import { renderWithQiankun } from 'vite-plugin-qiankun/dist/helper';
import App from './App';
import { setPlatformHostProps } from './api/client';
import './index.css';
import { applyPublicPath } from './public-path';

applyPublicPath();

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

let root: ReactDOM.Root | null = null;

function render(props?: { container?: HTMLElement }) {
  // 被 qiankun 加载时，挂载点在 props.container 中
  // 独立运行时，挂载点为 document.getElementById('root')
  const container = props?.container ?? document;
  const mountNode = container.querySelector('#root') as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <ThemeProvider>
        <AntdApp>
          <ErrorBoundary>
            <App />
          </ErrorBoundary>
        </AntdApp>
      </ThemeProvider>
    </React.StrictMode>,
  );
}

// 独立运行时直接挂载
if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

// qiankun 生命周期
export async function bootstrap() {}

export async function mount(props: { container?: HTMLElement; getToken?: () => string | null }) {
  // V-03：基座 accessToken 为内存态，经 props 注入读取器（独立运行时为 null）
  setPlatformHostProps(props);
  render(props);
}

export async function unmount() {
  root?.unmount();
  root = null;
  setPlatformHostProps(null);
}

// 把生命周期注册进 vite-plugin-qiankun 的桥接表：产物 index.html 由插件注入的脚本
// 从 window.moudleQiankunAppLifeCycles['whale-portal'] 取生命周期。缺这一步时基座内
// mount 的 Promise 永不 resolve —— 表现为白屏且无报错。
renderWithQiankun({
  bootstrap,
  mount,
  unmount,
  // 基座不调用 update；保留空实现以满足插件的生命周期类型
  update: () => Promise.resolve(),
});
