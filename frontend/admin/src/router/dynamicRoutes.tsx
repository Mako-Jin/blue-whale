// 动态路由与侧边导航：由 GET /auth/me 返回的 menus 驱动
// 菜单为空时 getDynamicRoutes / getSidebarItems 均返回 []，调用方回退到静态导航

import { Result } from 'antd';
import type { MenuProps } from 'antd';
import type { ComponentType } from 'react';
import type { RouteObject } from 'react-router-dom';
import type { MenuNode } from '../api/token';
import ComponentsDemo from '../pages/demo/ComponentsDemo';
import DeptList from '../pages/dept/DeptList';
import FileList from '../pages/file/FileList';
import JobList from '../pages/job/JobList';
import MenuList from '../pages/menu/MenuList';
import PermissionList from '../pages/permission/PermissionList';
import RoleList from '../pages/role/RoleList';
import UserList from '../pages/user/UserList';

/** 侧边导航项类型（与 antd Menu items 对齐） */
export type SidebarItem = NonNullable<MenuProps['items']>[number];

/** 组件标识 → 页面组件（FROZEN 契约键：user / role / menu / components / dept / permission / job / file） */
const COMPONENT_MAP: Record<
  'user' | 'role' | 'menu' | 'components' | 'dept' | 'permission' | 'job' | 'file',
  ComponentType
> = {
  user: UserList,
  role: RoleList,
  menu: MenuList,
  components: ComponentsDemo,
  dept: DeptList,
  permission: PermissionList,
  job: JobList,
  file: FileList,
};

type ComponentKey = keyof typeof COMPONENT_MAP;

/** 未匹配到已注册组件时的占位页——渲染在布局内容区，保证不崩溃 */
function UnmatchedPage() {
  return <Result status="404" title="页面未配置" subTitle="该菜单未匹配到已注册的页面组件" />;
}

/** visible=0 隐藏、status=0 停用；字段缺省视为可见/启用 */
function isActive(node: MenuNode): boolean {
  return node.visible !== 0 && node.status !== 0;
}

function isComponentKey(value: string): value is ComponentKey {
  return Object.prototype.hasOwnProperty.call(COMPONENT_MAP, value);
}

/**
 * 解析组件标识，同时兼容契约键与路径式取值：
 * user / role / menu / components / dept / permission / job / file，
 * 以及 system/user/index → user、system/dept/index → dept、system/job/index → job 等
 */
function resolveComponent(component?: string): ComponentType | undefined {
  if (!component) {
    return undefined;
  }
  const normalized = component
    .trim()
    .replace(/^\/+|\/+$/g, '')
    .replace(/\/index$/, '');
  if (isComponentKey(normalized)) {
    return COMPONENT_MAP[normalized];
  }
  // 路径式取值：从末段向前匹配契约键（如 system/user/index 命中 user）
  const segments = normalized.split('/');
  for (let i = segments.length - 1; i >= 0; i -= 1) {
    const segment = segments[i];
    if (isComponentKey(segment)) {
      return COMPONENT_MAP[segment];
    }
  }
  return undefined;
}

function toAbsolutePath(path?: string): string | undefined {
  const trimmed = path?.trim();
  if (!trimmed) {
    return undefined;
  }
  // 动态路由挂在同级 "/" 布局下，统一输出绝对路径
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

function buildRoutes(nodes: MenuNode[], acc: RouteObject[] = []): RouteObject[] {
  nodes.filter(isActive).forEach((node) => {
    // 有子节点视为分组目录，仅递归展开，不生成自身路由
    if (node.children?.length) {
      buildRoutes(node.children, acc);
      return;
    }
    const path = toAbsolutePath(node.path);
    if (!path) {
      return;
    }
    const Component = resolveComponent(node.component);
    acc.push({
      path,
      element: Component ? <Component /> : <UnmatchedPage />,
    });
  });
  return acc;
}

function buildSidebarItems(nodes: MenuNode[]): SidebarItem[] {
  return [...nodes]
    .filter(isActive)
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .flatMap<SidebarItem>((node) => {
      const children = node.children?.length ? buildSidebarItems(node.children) : [];
      // 原本声明的子菜单被过滤为空 → 整个分组不再展示
      if (node.children?.length && children.length === 0) {
        return [];
      }
      const key = node.path?.trim() || `menu-${node.id}`;
      if (children.length > 0) {
        return [{ key, label: node.name, children }];
      }
      return [{ key, label: node.name }];
    });
}

/** 由菜单树生成动态子路由（绝对路径）；菜单为空返回 [] */
export function getDynamicRoutes(menus?: MenuNode[]): RouteObject[] {
  if (!menus?.length) {
    return [];
  }
  return buildRoutes(menus);
}

/** 由菜单树生成侧边导航项；菜单为空返回 []，调用方据此回退静态导航 */
export function getSidebarItems(menus?: MenuNode[]): SidebarItem[] {
  if (!menus?.length) {
    return [];
  }
  return buildSidebarItems(menus);
}

/** 收集导航项全部 key（含子菜单），用于计算当前选中项 */
export function collectMenuKeys(items?: SidebarItem[]): string[] {
  const keys: string[] = [];
  const walk = (list?: SidebarItem[]) => {
    list?.forEach((item) => {
      if (!item || !('key' in item) || item.key == null) {
        return;
      }
      keys.push(String(item.key));
      if ('children' in item) {
        walk(item.children as SidebarItem[]);
      }
    });
  };
  walk(items);
  return keys;
}
