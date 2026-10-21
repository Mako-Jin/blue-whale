import React from 'react';
import ReactDOM from 'react-dom/client';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import App from './App';
import './index.css';

let root: ReactDOM.Root | null = null;

function render(props?: { container?: HTMLElement }) {
  const container = props?.container ?? document;
  const mountNode = container.querySelector('#root') as HTMLElement;

  root = ReactDOM.createRoot(mountNode);
  root.render(
    <React.StrictMode>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </React.StrictMode>,
  );
}

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  render();
}

export async function bootstrap() {
  console.log('[whale-erp] qiankun bootstrap');
}

export async function mount(props: { container?: HTMLElement }) {
  console.log('[whale-erp] qiankun mount', props);
  render(props);
}

export async function unmount() {
  console.log('[whale-erp] qiankun unmount');
  root?.unmount();
  root = null;
}
