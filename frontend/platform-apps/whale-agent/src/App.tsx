// BlueWhale whale-agent 路由入口：登录页 + 受保护的知识问答工作台

import type { RouteObject } from 'react-router-dom';
import { Navigate, useRoutes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login';
import AgentWorkbench from './pages/workbench/AgentWorkbench';
import { GuestOnly, RequireAuth } from './router/guards';

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
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
