/**
 * 菜单与页面权限码 —— 与后端 `AuthPermissions` 里的 `ui:*` 逐字一致。
 *
 * <h3>为什么需要一层独立的码</h3>
 *
 * 在这之前，"导航栏上出现哪几个入口"是**写死在代码里**的：基座把 `MICRO_APPS`
 * 整个数组渲染出来，没有过滤。于是只读账号看得见全部入口、点进去才吃 403。
 * 菜单码把这件事变成一个**可配置的事实**：某个角色看不看得见某个入口，
 * 由它的授权关系决定，而不由代码决定。
 *
 * <h3>它与读写权限是两个层次，别混</h3>
 *
 * <pre>
 *   ui:admin:view         ← 本文件。"导航栏上有没有「权限管理」这个入口"
 *   auth:user:read        ← 读写权限。"能不能调用户列表接口"
 * </pre>
 *
 * 撤销一条菜单码，只会让一个入口消失 —— **接口照样放行**。
 * 因此菜单码**不是权限收窄工具**：想让某人看不见某个入口又仍然允许他直接调接口，
 * 用菜单码；想让他真的做不到某件事，只能动读写权限。
 * 反过来，合并成一层就会把"少看到一个按钮"升级成"少做一件事"。
 *
 * <h3>为什么不用后端下发结构</h3>
 *
 * 菜单是**静态配置**，不是接口，因此不进 OpenAPI（跨端只认契约那条铁律管的是接口）。
 * 两端各声明一份、靠同一串编码对齐。这也意味着**改一个码要同时改两端** ——
 * 后端有 `AuthMenuTaxonomyTest` 钉住那一份，本文件由 `menus.test.ts` 逐字钉住这一份。
 *
 * <h3>两端不一致时的症状</h3>
 *
 * 后端的码改了、前端没改 ⇒ 前端等一个永远不会下发的权限 ⇒ 那个入口**永远不出现**，
 * 而所有人都会先去查"权限是不是没配"。因此改码这件事在两端都要有测试拦。
 */

/**
 * 应用入口 —— 基座导航栏的一条。
 *
 * <p>名字里的 `APP_` 前缀把它与页面级码区分开：这两个粒度混在一处时，
 * 读代码的人需要多看一眼才知道某个码管的是整个应用还是一个区块。
 */
export const AppMenus = {
  LOG_CONSOLE: 'ui:log_console:view',
  EVENT_CONSOLE: 'ui:event_console:view',
  ENVIRONMENT: 'ui:environment:view',
  ADMIN: 'ui:admin:view',
  AUDIT_CONSOLE: 'ui:audit_console:view',
} as const;

/** 日志台内部：演示区（不需要后端权限）、服务端查询区（需要 `log:client:read`） */
export const LogConsoleViews = {
  REPORTER: 'ui:log_reporter:view',
  QUERY: 'ui:log_query:view',
} as const;

/** 事件台内部：埋点演示区、服务端查询区（需要 `event:client:read`） */
export const EventConsoleViews = {
  TRACK: 'ui:event_track:view',
  QUERY: 'ui:event_query:view',
} as const;

/**
 * 权限管理内部六个标签页。
 *
 * <p>顺序即标签页顺序，与 `App.tsx` 的 `TABS` 声明一致 ——
 * 两处顺序不一致不会报错，只会让"菜单目录"与"界面"看起来是两套东西。
 */
export const AdminViews = {
  USER: 'ui:admin_user:view',
  TENANT: 'ui:admin_tenant:view',
  RESOURCE: 'ui:admin_resource:view',
  ROLE: 'ui:admin_role:view',
  USER_GROUP: 'ui:admin_user_group:view',
  // 组织排在最后：前面几项定的是"能不能做这个动作"，组织定的是"看得到哪些数据"。
  // 这个顺序与后端 AuthMenus 的声明顺序、与 App.tsx 的 TABS 逐项一致 ——
  // 顺序不一致不会报错，只会让"后台菜单目录"与"界面"看起来是两套东西
  ORGANIZATION: 'ui:admin_organization:view',
} as const;

/**
 * 审计台内部三块。
 *
 * <p>`DRILL`（演练区）与前两块的性质不同：它会**写**（真实调一次建租户再删掉），
 * 因此它的按钮**刻意不做权限显隐**（见 `DrillPanel` 的注释）——
 * "越权尝试"本身就是要被记下来的事件。给它一个菜单码是为了让"谁看得到这个区域"
 * 仍然可配，但**不要**因为它而把按钮藏起来：那会把最值得看的记录抹掉。
 */
export const AuditConsoleViews = {
  FLOW: 'ui:audit_flow:view',
  STATS: 'ui:audit_stats:view',
  DRILL: 'ui:audit_drill:view',
} as const;

/**
 * 三维环境**只有应用级码**（见 {@link AppMenus.ENVIRONMENT}），没有页面级 ——
 * 场景是本地几何与材质，不承载数据，给它编码既保护不住任何东西，
 * 又要把一整套会话依赖引进一个"后端没起也要能单跑看渲染"的应用。
 * 完整的推理写在 `AuthResources` 的 `ui:` 段末尾；将来若场景变成有归属的内容
 * （例如按租户授权的场景包），触发条件就到了，那时再补。
 */

/** 全部菜单码的联合类型。加码时它跟着变，因此消费点的 switch 会被编译器追着补 */
export type MenuPermission =
  | (typeof AppMenus)[keyof typeof AppMenus]
  | (typeof LogConsoleViews)[keyof typeof LogConsoleViews]
  | (typeof EventConsoleViews)[keyof typeof EventConsoleViews]
  | (typeof AdminViews)[keyof typeof AdminViews]
  | (typeof AuditConsoleViews)[keyof typeof AuditConsoleViews];
