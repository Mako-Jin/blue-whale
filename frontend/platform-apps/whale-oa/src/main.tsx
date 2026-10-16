import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { antdThemeConfig } from '@bluewhale/whale-common-theme';
import App from './App';
import './index.css';

let root: ReactDOM.Root | null = null;

function render(props?: { container?: HTMLElement }) {
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

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {
  console.log('[whale-oa] qiankun bootstrap');
}

export async function mount(props: { container?: HTMLElement }) {
  console.log('[whale-oa] qiankun mount', props);
  render(props);
}

export async function unmount() {
  console.log('[whale-oa] qiankun unmount');
  root?.unmount();
  root = null;
}
