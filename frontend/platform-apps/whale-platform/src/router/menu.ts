// 静态菜单配置 - 基座占位菜单与子应用入口
// 图标在 MainLayout 中通过 MENU_ICONS 映射，保持本文件为纯数据

export interface MenuNode {
  key: string;
  label: string;
  children?: MenuNode[];
}

export const menuConfig: MenuNode[] = [
  { key: '/', label: '工作台' },
  {
    key: '/system',
    label: '系统管理',
    children: [
      { key: '/system/users', label: '用户管理' },
      { key: '/system/roles', label: '角色管理' },
      { key: '/system/menus', label: '菜单管理' },
    ],
  },
  { key: '/portal', label: '门户应用' },
  { key: '/erp', label: 'ERP 应用' },
  { key: '/oa', label: 'OA 应用' },
  { key: '/im', label: '消息' },
];

const routeTitles = new Map<string, string>();

function collectTitles(nodes: MenuNode[]): void {
  nodes.forEach((node) => {
    routeTitles.set(node.key, node.label);
    if (node.children) collectTitles(node.children);
  });
}
collectTitles(menuConfig);

// 根据路径取页面标题：精确匹配菜单项；子应用子路由取第一段匹配；兜底返回路径本身
export function getRouteTitle(pathname: string): string {
  const exact = routeTitles.get(pathname);
  if (exact) return exact;
  const segment = `/${pathname.split('/')[1]}`;
  return routeTitles.get(segment) ?? pathname;
}

// 根据路径取面包屑文本链（如 ['系统管理', '用户管理']）
export function getBreadcrumbItems(pathname: string): string[] {
  for (const item of menuConfig) {
    if (item.children) {
      const child = item.children.find((node) => node.key === pathname);
      if (child) return [item.label, child.label];
    } else if (item.key === pathname) {
      return [item.label];
    }
  }
  if (pathname !== '/') {
    const segment = `/${pathname.split('/')[1]}`;
    const parent = routeTitles.get(segment);
    if (parent) return [parent];
  }
  return [];
}
