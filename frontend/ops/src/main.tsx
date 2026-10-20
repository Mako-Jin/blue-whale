import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App as AntdApp, ConfigProvider } from 'antd';
import { antdThemeConfig, applyThemeVars } from '@bluewhale/whale-ui';
import App from './App';
import './index.css';

// 将设计令牌写入 :root CSS 变量，供样式层引用 --bw-*
applyThemeVars();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider theme={antdThemeConfig}>
      <AntdApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
);
