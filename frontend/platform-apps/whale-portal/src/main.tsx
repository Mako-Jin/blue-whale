import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import App from './App';
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
          <App />
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

export async function mount(props: { container?: HTMLElement }) {
  render(props);
}

export async function unmount() {
  root?.unmount();
  root = null;
}
