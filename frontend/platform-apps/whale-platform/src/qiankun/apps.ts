// 微应用注册配置
// name: 子应用名称（需与子应用导出的 name 一致）
// entry: 子应用访问地址（dev 时指向子应用 vite dev server）
// activeRule: 激活路由规则
// container: 挂载容器选择器

import { getCurrentUser, getToken } from '../api/token';

export interface WhaleMicroApp {
  name: string;
  entry: string;
  activeRule: string;
  container: string;
  props?: Record<string, unknown>;
}

export const apps: WhaleMicroApp[] = [
  {
    name: 'whale-portal',
    entry: '//localhost:8101',
    activeRule: '/portal',
    container: '#subapp-container',
    props: { token: 'bluewhale-platform-token' },
  },
  {
    name: 'whale-erp',
    entry: '//localhost:8102',
    activeRule: '/erp',
    container: '#subapp-container',
    props: { token: 'bluewhale-platform-token' },
  },
  {
    name: 'whale-oa',
    entry: '//localhost:8103',
    activeRule: '/oa',
    container: '#subapp-container',
    props: { token: 'bluewhale-platform-token' },
  },
  {
    name: 'whale-mes',
    entry: '//localhost:8105',
    activeRule: '/mes',
    container: '#subapp-container',
    props: { token: 'bluewhale-platform-token' },
  },
  {
    name: 'whale-pdm',
    entry: '//localhost:8106',
    activeRule: '/pdm',
    container: '#subapp-container',
    props: { token: 'bluewhale-platform-token' },
  },
  {
    name: 'whale-im',
    entry: '//localhost:8107',
    activeRule: '/im',
    container: '#subapp-container',
    // 开发态与基座不同源，localStorage 不共享，需显式注入登录态读取器
    props: {
      token: 'bluewhale-platform-token',
      getToken,
      getUserId: () => {
        const current = getCurrentUser();
        return current?.userId === undefined || current?.userId === null
          ? null
          : String(current.userId);
      },
      getPermissions: () => getCurrentUser()?.permissions ?? [],
    },
  },
];
