// BlueWhale Admin 路由入口：登录页 + 受保护首页
// 登录/登出均对接 whale-auth 真实接口（经网关转发），用于验证令牌黑名单（REQ-SEC-003）

import { useRoutes } from 'react-router-dom';
import HomePage from './pages/Home';
import LoginPage from './pages/Login';
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
        <HomePage />
      </RequireAuth>
    ),
  },
];

export default function App() {
  return useRoutes(routes);
}
