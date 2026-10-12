import { createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import RequireAuth from '@/components/RequireAuth';
import Home from '@/pages/Home';
import Login from '@/pages/Login';
import MicroAppContainer from '@/components/MicroAppContainer';
import { MICRO_APPS } from '@/micro';

/**
 * 主应用路由。
 *
 * 子应用路由**由注册表推导**，而不是逐个手写 ——
 * 新增一个子应用只需要改 `src/micro/apps.ts` 一处，
 * 不会出现"注册表里加了、路由忘了加"这种只在点击时才暴露的缺口。
 *
 * 路径统一加 `/*` 通配：子应用内部如何组织路由是它自己的事，
 * 基座只负责把这一段前缀交给它。
 *
 * 登录页**在 `BasicLayout` 之外**：未登录时导航栏本身就不该出现
 * （否则用户能点到一堆进去就被弹回来的入口）。
 * 其余全部包在 `RequireAuth` 里 —— 一处守卫覆盖所有受保护路由，
 * 新增子应用不必记得"再给路由加个守卫"。
 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <BasicLayout />
      </RequireAuth>
    ),
    children: [
      {
        index: true,
        element: <Home />,
      },
      ...MICRO_APPS.map((app) => ({
        path: `${app.routePath.replace(/^\//, '')}/*`,
        element: <MicroAppContainer appName={app.name} />,
      })),
    ],
  },
]);
