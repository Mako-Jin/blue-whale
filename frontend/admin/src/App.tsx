// BlueWhale Admin 路由入口：登录页 + 受保护后台（权限门 + 主框架 + 子路由）
// 子路由 = 静态基础路由 + 由 /auth/me 的 menus 派生的动态路由（menus 为空仅静态）

import type { RouteObject } from 'react-router-dom';
import { useRoutes } from 'react-router-dom';
import { useCurrentUser } from './api/token';
import AdminLayout from './layouts/AdminLayout';
import ComponentsDemo from './pages/demo/ComponentsDemo';
import HomePage from './pages/Home';
import LoginPage from './pages/Login';
import MenuList from './pages/menu/MenuList';
import RoleList from './pages/role/RoleList';
import UserList from './pages/user/UserList';
import { getDynamicRoutes } from './router/dynamicRoutes';
import PermissionGate from './router/PermissionGate';
import { GuestOnly, RequireAuth } from './router/guards';

export default function App() {
  const user = useCurrentUser();
  // menus 派生动态路由，与静态子路由同挂载在 "/" 布局下
  const dynamicRoutes = getDynamicRoutes(user?.menus);

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
          <PermissionGate>
            <AdminLayout />
          </PermissionGate>
        </RequireAuth>
      ),
      children: [
        { index: true, element: <HomePage /> },
        { path: 'users', element: <UserList /> },
        { path: 'roles', element: <RoleList /> },
        { path: 'menus', element: <MenuList /> },
        { path: 'components', element: <ComponentsDemo /> },
        ...dynamicRoutes,
      ],
    },
  ];

  return useRoutes(routes);
}
