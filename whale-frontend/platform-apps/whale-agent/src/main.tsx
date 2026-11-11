import React from 'react';
import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import { qiankunWindow, renderWithQiankun } from 'vite-plugin-qiankun/dist/helper';
import App from './App';
import { setPlatformHostProps } from './api/client';
import { bootstrapAuth } from './api/token';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

/** 基座（qiankun）注入的 props：挂载容器 + 登录态读取器 */
interface HostProps {
  container?: HTMLElement;
  getToken?: () => string | null;
}

/** 基座注入的宿主属性（独立运行时为 null） */
let hostProps: HostProps | null = null;

/** 当前 React 根（unmount 时卸载，避免基座切换路由后残留） */
let root: ReactDOM.Root | null = null;

/** 是否运行在基座内（qiankun 注入 __POWERED_BY_QIANKUN__） */
function isInQiankun(): boolean {
  return Boolean(qiankunWindow.__POWERED_BY_QIANKUN__);
}

/** 解析挂载节点：基座内挂到注入容器的 #root，独立运行挂到文档 #root */
function resolveMountNode(container?: HTMLElement): HTMLElement {
  const host = container ?? document;
  const node = host.querySelector('#root') as HTMLElement | null;
  return node ?? (host as HTMLElement);
}

/** 渲染应用（主题 / 全局消息 / 错误边界；路由由 App 内部的 BrowserRouter 承担） */
function render(): void {
  root = ReactDOM.createRoot(resolveMountNode(hostProps?.container));
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

// ---------------------------------------------------------------------------
// qiankun 生命周期：基座按 apps.ts 的 name 调用；独立运行时走下方自举分支
// ---------------------------------------------------------------------------

export async function bootstrap(): Promise<void> {
  // 基座侧一次性初始化无额外动作（日志与审计已在模块加载时接入）
}

export async function mount(props: HostProps): Promise<void> {
  // 基座 accessToken 为内存态，经 props 注入读取器（独立运行时为 null，
  // 回落到本应用自己的存储，保证两种运行方式共用同一套请求层）
  hostProps = props ?? null;
  setPlatformHostProps(hostProps);
  render();
}

export async function unmount(): Promise<void> {
  root?.unmount();
  root = null;
  hostProps = null;
  setPlatformHostProps(null);
}

// 把上面三个生命周期注册进 vite-plugin-qiankun 的桥接表：
// 产物 index.html 由插件注入的脚本从 window.moudleQiankunAppLifeCycles['whale-agent']
// 取生命周期，缺这一步基座内 mount 的 Promise 永不 resolve —— 表现为白屏且无报错。
renderWithQiankun({
  bootstrap,
  mount,
  unmount,
  // 基座未调用 update；保留空实现以满足插件的生命周期类型
  update: () => Promise.resolve(),
});

// 独立运行（非基座内嵌）：先做一次静默续期，再渲染。
// 基座内嵌时不重复续期——登录态由基座维护，子应用只读取注入的 token。
if (!isInQiankun()) {
  const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';
  bootstrapAuth(API_BASE).finally(() => render());
}
