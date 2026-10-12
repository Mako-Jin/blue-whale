import type { MicroAppMeta } from './types';

/** 开发期从子应用自己的 dev server 拉取；生产期走基座同域的静态目录。 */
const isDev = import.meta.env.DEV;

/**
 * 已注册的子应用。
 *
 * 只登记**已经能被加载**的应用。`platform-user` 尚未创建，就不写在这里 ——
 * 注册一个加载不到的应用，只会在导航上多一个点了就报错的入口。
 */
export const MICRO_APPS: MicroAppMeta[] = [
  {
    name: 'platform-log-console',
    routePath: '/log-console',
    title: '日志控制台',
    devPort: 3200,
    prodEntry: '/log-console/',
    summary: '技术日志管道：产生日志 → 批量上报 → 落库 → 查询与链路追踪',
  },
  {
    name: 'platform-event-console',
    routePath: '/event-console',
    title: '行为事件控制台',
    devPort: 3300,
    prodEntry: '/event-console/',
    summary: '行为事件管道：埋点 → 上报 → 落库 → 行为分析查询',
  },
  {
    name: 'platform-environment',
    routePath: '/environment',
    title: '三维环境',
    devPort: 3400,
    prodEntry: '/environment/',
    summary: 'three.js 三维场景：渲染循环、相机控制与 GPU 资源释放',
  },
  {
    name: 'platform-admin',
    routePath: '/admin',
    title: '权限管理',
    devPort: 3500,
    prodEntry: '/admin/',
    summary: '权限域实体管理（用户 / 租户）：消费基座下发的权限，按标识显隐操作入口',
  },
];

export function findMicroApp(name: string): MicroAppMeta | undefined {
  return MICRO_APPS.find((app) => app.name === name);
}

/**
 * 解析子应用的 entry。
 *
 * 开发期写 `//localhost:<端口>`：qiankun 会跨域拉取子应用的 HTML 与脚本，
 * 因此子应用的 dev server **必须开启 CORS**（见各子应用 `vite.config.ts` 的 `server.cors`）。
 */
export function resolveEntry(meta: MicroAppMeta): string {
  return isDev ? `//localhost:${meta.devPort}` : meta.prodEntry;
}
