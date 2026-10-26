import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { ThemeProvider } from '@bluewhale/whale-common-theme';
import { initAuditLogging } from '@bluewhale/whale-log-sdk';
import App from './App';
import './index.css';

// 日期类组件（DatePicker 等）中文文案：antd locale + dayjs 语言包需同时生效
dayjs.locale('zh-cn');

// 前端日志与审计接入：console 开发者日志 + 全局事件审计（REQ-SEC-006 通道③④）
initAuditLogging();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN}>
      <ThemeProvider>
        <AntdApp>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AntdApp>
      </ThemeProvider>
    </ConfigProvider>
  </React.StrictMode>,
);
