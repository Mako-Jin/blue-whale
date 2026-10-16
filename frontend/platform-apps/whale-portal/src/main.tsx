import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { antdThemeConfig } from '@bluewhale/whale-common-theme';
import App from './App';
import './index.css';
import { applyPublicPath } from './public-path';

applyPublicPath();

let root: ReactDOM.Root | null = null;

function render(props?: { container?: HTMLElement }) {
  // 被 qiankun 加载时，挂载点在 props.container 中
  // 独立运行时，挂载点为 document.getElementById('root')
  const container = props?.container ?? document;
  const mountNode = container.querySelector('#root') as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <ConfigProvider theme={antdThemeConfig}>
        <App />
      </ConfigProvider>
    </React.StrictMode>,
  );
}

// 独立运行时直接挂载
if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

// qiankun 生命周期
export async function bootstrap() {
  console.log('[whale-portal] qiankun bootstrap');
}

export async function mount(props: { container?: HTMLElement }) {
  console.log('[whale-portal] qiankun mount', props);
  render(props);
}

export async function unmount() {
  console.log('[whale-portal] qiankun unmount');
  root?.unmount();
  root = null;
}
