import { createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import Home from '@/pages/Home';

/**
 * 主应用路由。
 *
 * 子应用就绪后，在 children 中按前缀挂载各自的容器组件：
 *   { path: 'user/*', element: <UserAppContainer /> }   用户端
 *   { path: 'admin/*', element: <AdminAppContainer /> } 管理端
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      {
        index: true,
        element: <Home />,
      },
    ],
  },
]);
