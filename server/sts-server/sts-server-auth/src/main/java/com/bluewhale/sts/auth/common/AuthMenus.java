package com.bluewhale.sts.auth.common;

import java.util.List;

/**
 * 前端菜单结构 —— 应用入口与它内部可独立关闭的视图。
 *
 * <p><b>本类补上的是 {@link AuthPermissions} 表达不了的一件事：层级。</b>
 * 权限码只是一个平铺的字符串集合，"哪些码属于同一个应用"、"哪个码是应用入口"
 * 在没有结构的情况下只能靠命名约定去猜。而这两件事各自要用到：
 * <ul>
 *   <li>{@code bw_auth_resource} 有 {@code parent_id}，菜单目录本来就是一棵树，
 *       平铺进去只能得到一堆没有归属的孤行；</li>
 *   <li>种子数据要按类型建行 —— 应用入口是 {@code MENU}、内部视图是 {@code PAGE}，
 *       两者的 {@code requestScoped} 都是 {@code false}，但类型不能混。</li>
 * </ul>
 *
 * <p><b>它不含任何"谁能看"的信息。</b>结构是结构，授权是授权：
 * 本类只说"有这个菜单"、"它下面有这些页面"，至于哪个角色看得到，
 * 完全由角色与资源的授权关系决定（种子里的两个内置角色见
 * {@code DefaultAdminSeeder}）。把"默认给谁"写进结构里，就等于把一份可配的东西
 * 钉死在代码里 —— 那正是这次要消灭的状态。
 *
 * <p><b>名称是给资源目录界面看的，不是前端界面的标题。</b>两处刻意各留一份：
 * 后台改一个资源的显示名不该牵动前端；前端改一个标签页的措辞也不该牵动后台。
 * 它们是同一件事在两个界面上的两种说法，不是同一份数据。
 *
 * <p><b>前端的对应物在 {@code common-api} 的权限码常量与各应用的视图表里。</b>
 * 跨端只认契约这条铁律在这里有一个例外要说清：菜单结构**不进 OpenAPI**
 * （它是静态配置，不是接口），因此两端各声明一份，靠
 * {@code AuthMenuTaxonomyTest} 钉住后端这一份的自洽性（每个应用入口恰好
 * 一条 {@code MENU}、页面级码的父必须是某个应用入口、码不能重复）。
 */
public final class AuthMenus {

    private AuthMenus() {
    }

    /**
     * 一个应用入口。
     *
     * @param code  应用级权限码（{@link AuthPermissions} 里的常量）
     * @param name  资源目录里显示的名字
     * @param pages 该应用内部可独立关闭的视图；**可以为空** —— 那表示这个应用
     *              只有一个视图，没有页面级这一层（当前五个应用都有页面级）
     */
    public record Application(String code, String name, List<Page> pages) {
    }

    /**
     * 应用内部的一个视图。
     *
     * @param code 页面级权限码
     * @param name 资源目录里显示的名字
     */
    public record Page(String code, String name) {
    }

    /**
     * 全部应用入口，顺序即基座导航栏的顺序。
     *
     * <p>顺序不是随手排的：日志台 → 事件台 → 三维环境 → 权限管理 → 审计日志，
     * 与 {@code platform-portal} 的 {@code MICRO_APPS} 逐项对应。
     * 两处顺序不一致不会报错，只会让"后台菜单目录"与"导航栏"看起来是两套东西 ——
     * 因此改这里就要顺手改那边（跨语言的两份清单没法互查，只能写清楚）。
     *
     * <p><b>{@code pages} 为空是合法的</b>，表示"这个应用只有一个视图，没有页面级这一层"。
     * 今天只有三维环境是这样：它的场景不承载数据，按粒度定义不该有页面级码
     * （理由写在 {@link AuthResources} 的 ui: 段末尾）。空列表因此**不是漏写** ——
     * 加页面级码之前先回答"关掉它之后剩下的是不是仍然自洽、以及它保护住了什么"。
     */
    public static final List<Application> ALL = List.of(
            new Application(AuthPermissions.UI_LOG_CONSOLE_VIEW, "日志控制台", List.of(
                    new Page(AuthPermissions.UI_LOG_REPORTER_VIEW, "日志 · 上报演示"),
                    new Page(AuthPermissions.UI_LOG_QUERY_VIEW, "日志 · 服务端查询"))),

            new Application(AuthPermissions.UI_EVENT_CONSOLE_VIEW, "行为事件控制台", List.of(
                    new Page(AuthPermissions.UI_EVENT_TRACK_VIEW, "事件 · 埋点演示"),
                    new Page(AuthPermissions.UI_EVENT_QUERY_VIEW, "事件 · 服务端查询"))),

            new Application(AuthPermissions.UI_ENVIRONMENT_VIEW, "三维环境", List.of()),

            new Application(AuthPermissions.UI_ADMIN_VIEW, "权限管理", List.of(
                    new Page(AuthPermissions.UI_ADMIN_USER_VIEW, "权限管理 · 用户"),
                    new Page(AuthPermissions.UI_ADMIN_TENANT_VIEW, "权限管理 · 租户"),
                    new Page(AuthPermissions.UI_ADMIN_RESOURCE_VIEW, "权限管理 · 资源"),
                    new Page(AuthPermissions.UI_ADMIN_ROLE_VIEW, "权限管理 · 角色"),
                    new Page(AuthPermissions.UI_ADMIN_USER_GROUP_VIEW, "权限管理 · 用户组"),
                    // 组织排在最后：前面几项定的是"能不能做这个动作"，
                    // 组织定的是"看得到哪些数据" —— 它不参与那条从左到右的授权链
                    new Page(AuthPermissions.UI_ADMIN_ORGANIZATION_VIEW, "权限管理 · 组织"))),

            new Application(AuthPermissions.UI_AUDIT_CONSOLE_VIEW, "审计日志", List.of(
                    new Page(AuthPermissions.UI_AUDIT_FLOW_VIEW, "审计 · 流水"),
                    new Page(AuthPermissions.UI_AUDIT_STATS_VIEW, "审计 · 按动作聚合"),
                    new Page(AuthPermissions.UI_AUDIT_DRILL_VIEW, "审计 · 演练区"))));

    /** 全部应用级码。 */
    public static List<String> applicationCodes() {
        return ALL.stream().map(Application::code).toList();
    }

    /**
     * 全部页面级码（含可能的空列表应用）。
     */
    public static List<String> pageCodes() {
        return ALL.stream().flatMap(application -> application.pages().stream())
                .map(Page::code)
                .toList();
    }

    /** 全部菜单码（应用级 + 页面级）。 */
    public static List<String> allCodes() {
        return ALL.stream()
                .flatMap(application -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(application.code()),
                        application.pages().stream().map(Page::code)))
                .toList();
    }

    /** 只查应用级，给 {@code bw_auth_resource.resource_type = MENU} 用。 */
    public static boolean isApplicationCode(String code) {
        return applicationCodes().contains(code);
    }
}
