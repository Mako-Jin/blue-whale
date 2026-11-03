// 微应用注册配置
// name: 子应用名称（需与子应用导出的 name 一致）
// entry: 子应用访问地址。开发态默认指向各子应用 vite dev server；
//        生产部署必须通过 VITE_SUBAPP_ENTRY_<NAME> 注入真实地址（反向代理或子应用域名），
//        回退值仅用于本地开发（P0-5 修复）。
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

/** 开发态各子应用 dev server 端口（与 docs/service-catalog.md §五 一致） */
const DEV_PORTS = {
  'whale-portal': 8101,
  'whale-erp': 8102,
  'whale-oa': 8103,
  'whale-mes': 8105,
  'whale-pdm': 8106,
  'whale-im': 8107,
  'whale-pm': 8108,
  'whale-contract': 8109,
  'whale-doc': 8110,
  'whale-music': 8111,
  'whale-video': 8114,
} as const;

type SubAppName = keyof typeof DEV_PORTS;

/**
 * 解析子应用 entry：环境变量优先（生产），本地 dev server 回退（开发）。
 *
 * @param name    子应用名（仅用于开发态端口回退）
 * @param envAddr VITE_SUBAPP_ENTRY_<NAME> 环境变量值，未配置时回落本地端口
 * @returns 子应用 entry 地址
 */
function subAppEntry(name: SubAppName, envAddr: string | undefined): string {
  return envAddr?.trim() ? envAddr.trim() : `//localhost:${DEV_PORTS[name]}`;
}

/**
 * 基座 → 子应用公共注入 props。
 *
 * 开发态子应用与基座不同源、localStorage 不共享，因此所有子应用统一注入
 * 登录态读取器（getToken/getUserId/getPermissions），不再依赖写死的占位 token；
 * token 字段保留为兼容旧子应用 host 逻辑的回退值，可用 VITE_PLATFORM_TOKEN 覆盖。
 */
function platformProps(): Record<string, unknown> {
  return {
    token: import.meta.env.VITE_PLATFORM_TOKEN ?? 'bluewhale-platform-token',
    getToken,
    getUserId: () => {
      const current = getCurrentUser();
      return current?.userId === undefined || current?.userId === null
        ? null
        : String(current.userId);
    },
    getPermissions: () => getCurrentUser()?.permissions ?? [],
  };
}

export const apps: WhaleMicroApp[] = [
  {
    name: 'whale-portal',
    entry: subAppEntry('whale-portal', import.meta.env.VITE_SUBAPP_ENTRY_PORTAL),
    activeRule: '/portal',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-erp',
    entry: subAppEntry('whale-erp', import.meta.env.VITE_SUBAPP_ENTRY_ERP),
    activeRule: '/erp',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-oa',
    entry: subAppEntry('whale-oa', import.meta.env.VITE_SUBAPP_ENTRY_OA),
    activeRule: '/oa',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-mes',
    entry: subAppEntry('whale-mes', import.meta.env.VITE_SUBAPP_ENTRY_MES),
    activeRule: '/mes',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-pdm',
    entry: subAppEntry('whale-pdm', import.meta.env.VITE_SUBAPP_ENTRY_PDM),
    activeRule: '/pdm',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-im',
    entry: subAppEntry('whale-im', import.meta.env.VITE_SUBAPP_ENTRY_IM),
    activeRule: '/im',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-pm',
    entry: subAppEntry('whale-pm', import.meta.env.VITE_SUBAPP_ENTRY_PM),
    activeRule: '/pm',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-contract',
    entry: subAppEntry('whale-contract', import.meta.env.VITE_SUBAPP_ENTRY_CONTRACT),
    activeRule: '/contract',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-doc',
    entry: subAppEntry('whale-doc', import.meta.env.VITE_SUBAPP_ENTRY_DOC),
    activeRule: '/doc',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-music',
    entry: subAppEntry('whale-music', import.meta.env.VITE_SUBAPP_ENTRY_MUSIC),
    activeRule: '/music',
    container: '#subapp-container',
    props: platformProps(),
  },
  {
    name: 'whale-video',
    entry: subAppEntry('whale-video', import.meta.env.VITE_SUBAPP_ENTRY_VIDEO),
    activeRule: '/video',
    container: '#subapp-container',
    props: platformProps(),
  },
];
