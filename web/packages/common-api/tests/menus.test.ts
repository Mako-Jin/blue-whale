import { describe, expect, it } from 'vitest';

import {
  AdminViews,
  AppMenus,
  AuditConsoleViews,
  EventConsoleViews,
  LogConsoleViews,
} from '../src/menus';

/**
 * 菜单码的两端对齐。
 *
 * <h3>为什么是"抄一份字面量"</h3>
 *
 * 菜单是静态配置、不是接口，因此不进 OpenAPI（跨端只认契约那条铁律管的是接口）。
 * 后端那一份由 `AuthMenuTaxonomyTest` 钉住，这一份由本文件钉住 ——
 * 两边各钉一次，中间没有机器能自动比对，所以只能靠**逐字**断言。
 *
 * <p>抄的是 `AuthPermissions` 里的实际值，**不是** `menus.ts` 的内容：
 * 若从被测对象复制一份，这个文件就只是在复述自己。
 *
 * <h3>不这样钉会怎样</h3>
 *
 * 后端改了码、前端没改 ⇒ 前端在等一个永远不会下发的权限 ⇒ 那个入口**永远不出现**。
 * 而所有人都会先去查"权限是不是没配"（见 `menus.ts` 的说明）。
 */
const BACKEND_APPLICATIONS = [
  'ui:log_console:view',
  'ui:event_console:view',
  'ui:environment:view',
  'ui:admin:view',
  'ui:audit_console:view',
];

const BACKEND_PAGES = {
  logConsole: ['ui:log_reporter:view', 'ui:log_query:view'],
  eventConsole: ['ui:event_track:view', 'ui:event_query:view'],
  admin: [
    'ui:admin_user:view',
    'ui:admin_tenant:view',
    'ui:admin_resource:view',
    'ui:admin_role:view',
    'ui:admin_user_group:view',
    'ui:admin_organization:view',
  ],
  auditConsole: ['ui:audit_flow:view', 'ui:audit_stats:view', 'ui:audit_drill:view'],
};

describe('菜单码与后端一致', () => {
  it('应用级码逐字一致，且顺序与 AuthMenus 的声明顺序相同', () => {
    // 顺序不一致不会报错，只会让"后台菜单目录"与导航栏看起来是两套东西
    expect(Object.values(AppMenus)).toEqual(BACKEND_APPLICATIONS);
  });

  it('各应用的页面级码逐字一致（顺序即标签页顺序）', () => {
    expect(Object.values(LogConsoleViews)).toEqual(BACKEND_PAGES.logConsole);
    expect(Object.values(EventConsoleViews)).toEqual(BACKEND_PAGES.eventConsole);
    expect(Object.values(AdminViews)).toEqual(BACKEND_PAGES.admin);
    expect(Object.values(AuditConsoleViews)).toEqual(BACKEND_PAGES.auditConsole);
  });

  it('三维环境刻意没有页面级码', () => {
    /*
     * 场景是本地几何与材质、不承载数据。给它编码既保护不住任何东西（假开关），
     * 又要给一个"当前没有、也不需要任何会话"的应用引进一整套认证链路 ——
     * 而独立运行那一支依赖后端可达，会弄坏"后端没起时单跑看渲染效果"这个最常用的开发方式。
     * 它的应用级码仍是真开关。
     */
    const environmentPages = Object.entries({
      LogConsoleViews,
      EventConsoleViews,
      AdminViews,
      AuditConsoleViews,
    }).flatMap(([, views]) => Object.values(views));

    expect(environmentPages.some((code) => code.includes('environment'))).toBe(false);
  });
});

describe('菜单码的形态', () => {
  const ALL_CODES = [
    ...Object.values(AppMenus),
    ...Object.values(LogConsoleViews),
    ...Object.values(EventConsoleViews),
    ...Object.values(AdminViews),
    ...Object.values(AuditConsoleViews),
  ];

  it('一律 ui: 开头、:view 结尾', () => {
    for (const code of ALL_CODES) {
      expect(code).toMatch(/^ui:[a-z][a-z0-9_]*:view$/);
    }
  });

  it('没有重复 —— 重复的码在种子里会静默跳过，看起来像"少配了一条"', () => {
    expect(new Set(ALL_CODES).size).toBe(ALL_CODES.length);
  });

  it('应用级与页面级不重叠', () => {
    const applications = new Set<string>(Object.values(AppMenus));
    for (const code of ALL_CODES.filter((item) => !applications.has(item))) {
      expect(applications.has(code)).toBe(false);
    }
    // 上面那条循环只说明"页面码不在应用码集合里"，这里补上总数对照：
    // 应用 5 + 页面 11 = 16，与后端 AuthPermissions.ALL 里新增的条数相同
    expect(ALL_CODES).toHaveLength(5 + 2 + 2 + 6 + 3);
  });
});
