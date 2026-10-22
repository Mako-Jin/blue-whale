import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import Root from './Root';
import './index.css';

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AntdApp>
        <Root />
      </AntdApp>
    </ThemeProvider>
  </React.StrictMode>,
);
