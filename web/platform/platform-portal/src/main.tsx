import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { ensureSessionRestored } from './auth';
import { startSessionSync } from './micro';
import './styles/index.less';

const container = document.getElementById('root');

if (!container) {
  throw new Error('未找到 #root 挂载点，请检查 index.html');
}

/*
 * 这两件事都发生在渲染之前、但**都不阻塞渲染**：
 * - 同步会话到全局状态：模块级订阅，登录页（不渲染布局）期间同样生效
 * - 恢复会话：要走一次网络，先渲染界面并显示"正在恢复会话"，
 *   而不是把首屏押在一次刷新请求上（docs/decisions/0003 的首屏零阻塞）
 */
startSessionSync();
void ensureSessionRestored();

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
