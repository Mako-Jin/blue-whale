// BlueWhale whale-agent 路由入口：基座子应用 + 独立运行共用一套路由
//
// 基座以 activeRule=/agent 挂载，故 BrowserRouter 固定 basename="/agent"：
// 基座内 /agent/chat 与本应用内 /chat 指向同一页面，独立运行时访问
// http://localhost:8004/agent/chat 亦然（vite base 同为 /agent）。

import type { RouteObject } from 'react-router-dom';
import { BrowserRouter, Navigate, useRoutes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login';
import AgentWorkbench from './pages/workbench/AgentWorkbench';
import { GuestOnly, RequireAuth } from './router/guards';

/** 子应用路由前缀（与基座 apps.ts 的 activeRule 保持一致） */
const BASENAME = '/agent';

const routes: RouteObject[] = [
  {
    path: '/login',
    element: (
      <GuestOnly>
        <LoginPage />
      </GuestOnly>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/chat" replace /> },
      { path: 'chat', element: <AgentWorkbench /> },
      // 基座菜单「知识问答」落点为 /agent/chat；其余未知路径回落到工作台，
      // 避免基座内点到未实现路径时整页空白（Wave 6.18a）
      { path: '*', element: <Navigate to="/chat" replace /> },
    ],
  },
];

/** 路由表（需在 Router 上下文内调用 useRoutes） */
function AppRoutes() {
  return useRoutes(routes);
}

export default function App() {
  return (
    <BrowserRouter basename={BASENAME}>
      <AppRoutes />
    </BrowserRouter>
  );
}
