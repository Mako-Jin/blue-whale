// BlueWhale Admin 路由入口：登录页 + 受保护后台（权限门 + 主框架 + 子路由）

import { useRoutes } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import ComponentsDemo from './pages/demo/ComponentsDemo';
import HomePage from './pages/Home';
import LoginPage from './pages/Login';
import MenuList from './pages/menu/MenuList';
import RoleList from './pages/role/RoleList';
import UserList from './pages/user/UserList';
import PermissionGate from './router/PermissionGate';
import { GuestOnly, RequireAuth } from './router/guards';

const routes = [
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
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
