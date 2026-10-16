// 基础路由配置
// 基座自身路由 + 子应用挂载点

import { lazy, Suspense } from 'react';
import { Navigate } from 'react-router-dom';

const MainLayout = lazy(() => import('../layouts/MainLayout'));
const Dashboard = lazy(() => import('../pages/Dashboard'));
const SubAppContainer = lazy(() => import('../pages/SubAppContainer'));

export const routes = [
  {
    path: '/',
    element: (
      <Suspense fallback={null}>
        <MainLayout />
      </Suspense>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <Dashboard /> },
      // 子应用路由 - qiankun 根据 activeRule 激活
      { path: 'portal/*', element: <SubAppContainer /> },
      { path: 'erp/*', element: <SubAppContainer /> },
      { path: 'oa/*', element: <SubAppContainer /> },
    ],
  },
];
