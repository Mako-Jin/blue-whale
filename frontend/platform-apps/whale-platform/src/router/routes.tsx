// 基础路由配置
// 基座自身路由 + 子应用挂载点；/login 独立于主布局，主布局整体由 RequireAuth 守卫

import { lazy, Suspense } from 'react';
import Dashboard from '../pages/Dashboard';
import PlaceholderPage from '../pages/PlaceholderPage';
import SubAppContainer from '../pages/SubAppContainer';
import { GuestOnly, RequireAuth } from './guards';

const MainLayout = lazy(() => import('../layouts/MainLayout'));
const LoginPage = lazy(() => import('../pages/login'));

export const routes = [
  {
    path: '/login',
    element: (
      <Suspense fallback={null}>
        <GuestOnly>
          <LoginPage />
        </GuestOnly>
      </Suspense>
    ),
  },
  {
    path: '/',
    element: (
      <Suspense fallback={null}>
        <RequireAuth>
          <MainLayout />
        </RequireAuth>
      </Suspense>
    ),
    children: [
      // 工作台（首页，多标签页中固定不可关）
      { index: true, element: <Dashboard /> },
      // 系统管理 - 占位页
      { path: 'system/users', element: <PlaceholderPage title="用户管理" /> },
      { path: 'system/roles', element: <PlaceholderPage title="角色管理" /> },
      { path: 'system/menus', element: <PlaceholderPage title="菜单管理" /> },
      // 子应用路由 - qiankun 根据 activeRule 激活
      { path: 'portal/*', element: <SubAppContainer /> },
      { path: 'erp/*', element: <SubAppContainer /> },
      { path: 'oa/*', element: <SubAppContainer /> },
      // 消息中心（whale-im 子应用，具体页面由 qiankun 接管）
      { path: 'im/*', element: <SubAppContainer /> },
    ],
  },
];
