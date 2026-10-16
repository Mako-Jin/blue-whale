import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntdApp } from 'antd';
import ThemeProvider from './theme/ThemeProvider';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AntdApp>
        <App />
      </AntdApp>
    </ThemeProvider>
  </React.StrictMode>,
);
