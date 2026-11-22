// 基座菜单本地化测试：钉死中英键对齐、默认中文、标题/面包屑跟随语言、权限裁剪不变

import { describe, expect, it } from 'vitest';

import {
  filterMenuByPermissions,
  getBreadcrumbItems,
  getRouteTitle,
  hasMenuEntry,
  localizedMenuLabel,
  menuConfig,
  menuLabelEn,
  type MenuNode,
} from './menu';

/** 展平菜单树，收集所有 key 与中文 label */
function flatten(nodes: MenuNode[]): MenuNode[] {
  return nodes.flatMap((node) => [node, ...(node.children ? flatten(node.children) : [])]);
}

const allNodes = flatten(menuConfig);

describe('menuLabelEn', () => {
  it('每个菜单节点都登记了英文（漏登记会导致英文界面显示中文）', () => {
    const keys = allNodes.map((node) => node.key);
    for (const key of keys) {
      expect(menuLabelEn[key], `菜单项 ${key} 缺英文`).toBeTruthy();
    }
  });

  it('无多余英文键（菜单删项后映射表应同步清理）', () => {
    const keys = new Set(allNodes.map((node) => node.key));
    for (const key of Object.keys(menuLabelEn)) {
      expect(keys.has(key), `menuLabelEn 存在已删除的菜单项 ${key}`).toBe(true);
    }
  });

  it('英文文案不含中文字符', () => {
    const han = /[\u4e00-\u9fff]/;
    for (const [key, value] of Object.entries(menuLabelEn)) {
      expect(han.test(value), `${key} 英文含中文: ${value}`).toBe(false);
    }
  });
});

describe('localizedMenuLabel', () => {
  it('中文取 label，英文取映射表', () => {
    const users = allNodes.find((node) => node.key === '/system/users')!;
    expect(localizedMenuLabel(users, 'zh-CN')).toBe('用户管理');
    expect(localizedMenuLabel(users, 'en-US')).toBe(menuLabelEn['/system/users']);
  });
});

describe('getRouteTitle / getBreadcrumbItems', () => {
  it('默认中文（向后兼容既有调用）', () => {
    expect(getRouteTitle('/system/users')).toBe('用户管理');
    expect(getBreadcrumbItems('/system/users')).toEqual(['系统管理', '用户管理']);
  });

  it('英文标题与面包屑跟随语言', () => {
    expect(getRouteTitle('/system/users', 'en-US')).toBe('Users');
    expect(getBreadcrumbItems('/system/users', 'en-US')).toEqual([
      'System Management',
      'Users',
    ]);
  });

  it('子应用子路由取第一段入口标题', () => {
    expect(getRouteTitle('/agent/chat', 'zh-CN')).toBe('知识助手');
    expect(getRouteTitle('/agent/chat', 'en-US')).toBe('Knowledge Assistant');
    expect(hasMenuEntry('/agent/chat')).toBe(false);
    expect(hasMenuEntry('/agent')).toBe(true);
  });

  it('未登记路由回落路径本身', () => {
    expect(getRouteTitle('/unknown/path', 'en-US')).toBe('/unknown/path');
  });
});

describe('filterMenuByPermissions', () => {
  it('英文语言下权限裁剪逻辑不变（只返回有权限节点）', () => {
    const visible = filterMenuByPermissions(menuConfig, ['sys:user:list']);
    const system = visible.find((node) => node.key === '/system');
    expect(system?.children?.map((node) => node.key)).toEqual(['/system/users']);
    expect(localizedMenuLabel(system!.children![0], 'en-US')).toBe('Users');
  });
});
