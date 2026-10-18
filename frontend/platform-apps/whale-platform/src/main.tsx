import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import { initLogSdk } from '@bluewhale/whale-log-sdk';
import ThemeProvider from './theme/ThemeProvider';
import App from './App';
import './index.css';

// 前端日志 SDK：采集 console 开发者日志与用户行为（REQ-SEC-006 通道③④）
initLogSdk();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AntdApp>
        <App />
      </AntdApp>
    </ThemeProvider>
  </React.StrictMode>,
);
