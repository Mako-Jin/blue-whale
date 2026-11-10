import React from 'react';
import { ErrorBoundary } from '@bluewhale/whale-ui';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App as AntdApp } from 'antd';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import App from './App';
import { bootstrapAuth } from './api/token';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

// 启动静默续期（V-03）：内存无 accessToken 时用 HttpOnly 刷新 Cookie 换新一次，
// Cookie 不在则按未登录进入（守卫重定向 /login）；完成后再渲染路由以稳定守卫判定
const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';
bootstrapAuth(API_BASE).finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <ThemeProvider>
        <AntdApp>
          <BrowserRouter>
            <ErrorBoundary>
              <App />
            </ErrorBoundary>
          </BrowserRouter>
        </AntdApp>
      </ThemeProvider>
    </React.StrictMode>,
  );
});
