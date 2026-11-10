// BlueWhale Ops 路由入口：登录页 + 受保护运维平台（主框架 + 三页）

import type { RouteObject } from 'react-router-dom';
import { Navigate, useRoutes } from 'react-router-dom';
import BasicLayout from './layouts/BasicLayout';
import Alert from './pages/Alert';
import Audit from './pages/Audit';
import LoginPage from './pages/Login';
import Monitor from './pages/Monitor';
import { GuestOnly, RequireAuth } from './router/guard';

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
        <BasicLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/monitor" replace /> },
      { path: 'monitor', element: <Monitor /> },
      { path: 'audit', element: <Audit /> },
      { path: 'alert', element: <Alert /> },
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
