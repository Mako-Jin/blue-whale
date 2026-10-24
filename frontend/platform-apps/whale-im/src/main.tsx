import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import App from './App';
import { setHostProps, type HostProps } from './platform/host';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

let root: ReactDOM.Root | null = null;

function render(props?: HostProps) {
  setHostProps(props);
  const container = props?.container ?? document;
  const mountNode = (container.querySelector('#root') ?? container) as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <ThemeProvider>
        <AntdApp>
          <App />
        </AntdApp>
      </ThemeProvider>
    </React.StrictMode>,
  );
}

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {}

export async function mount(props: HostProps) {
  render(props);
}

export async function unmount() {
  root?.unmount();
  root = null;
  setHostProps(null);
}
