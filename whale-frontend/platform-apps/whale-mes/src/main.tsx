import React from 'react';
import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import App from './App';
import { configureAuthToken } from './api/client';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
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
  // 基座经 qiankun props 注入登录态读取器；接线后所有 MES 请求携带 Bearer
  configureAuthToken(props.getToken ?? null);
  render(props);
}

export async function unmount() {
  configureAuthToken(null);
  root?.unmount();
  root = null;
}
