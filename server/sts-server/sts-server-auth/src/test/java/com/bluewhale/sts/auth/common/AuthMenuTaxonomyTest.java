package com.bluewhale.sts.auth.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单结构（{@link AuthMenus}）与权限码登记处（{@link AuthPermissions#ALL}）的一致性。
 *
 * <p><b>为什么值得单独钉一份测试：</b>菜单码是**唯一一类"前端必须知道、后端不参与判定"**
 * 的标识。它出错的方式全都是静默的 ——
 * <ul>
 *   <li>忘了登记进 {@code ALL} ⇒ 种子不建这一行 ⇒ 后端没有它、前端却在等它下发 ⇒
 *       那个入口**永远不出现**（看起来像"权限没配"，实际是根本没有这条资源）；</li>
 *   <li>登记了却没进结构表 ⇒ 它会以 {@code API} 类型建出来（种子按编码是否在
 *       {@link AuthMenus} 里分流），于是在资源目录里显示"参与后端准入"，
 *       而实际上没有任何接口要求过它 —— 管理员据此做配置判断会被误导；</li>
 *   <li>页面码的父写成另一个页面码 ⇒ 资源树多一层嵌套，菜单结构不再是"应用 → 页面"。</li>
 * </ul>
 *
 * <p>这三条都不会让任何一次请求失败，只会让界面与配置界面各说各话。
 * 因此把它们钉成断言，而不是靠评审记住。
 *
 * <p><b>本类管不到前端的 {@code MICRO_APPS} 与各子应用的视图表</b>：那是另一门语言的
 * 另一份声明，跨端互查没有低成本的办法（菜单结构刻意不进 OpenAPI —— 它是静态配置不是接口）。
 * 两端靠同一份编码对齐，改编码时两边都要改；这条约束只能写在文档里。
 */
class AuthMenuTaxonomyTest {

    @Test
    @DisplayName("菜单码全部登记在 AuthPermissions.ALL 里，一个不漏、一个不多")
    void everyMenuCodeShouldBeRegistered() {
        Set<String> registered = AuthPermissions.ALL.stream()
                .filter(code -> code.startsWith("ui:"))
                .collect(Collectors.toSet());

        assertThat(registered)
                .as("菜单码漏登记 ⇒ 种子不建这一行 ⇒ 前端的那个入口永远等不到权限下发")
                .containsExactlyInAnyOrderElementsOf(AuthMenus.allCodes());
    }

    @Test
    @DisplayName("菜单码一律 ui: 开头、:view 结尾，且不重复")
    void menuCodesShouldHaveTheMenuShape() {
        List<String> codes = AuthMenus.allCodes();

        assertThat(codes).allMatch(code -> code.startsWith("ui:") && code.endsWith(":view"));
        assertThat(codes)
                .as("同一个码出现两次 ⇒ 种子里后一次建行会静默跳过，页面与应用的归属就看声明顺序了")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("应用级码与页面级码不重叠")
    void pagesShouldNotBeApplications() {
        List<String> applications = AuthMenus.applicationCodes();

        assertThat(AuthMenus.pageCodes())
                .as("页面级码同时也是应用级码 ⇒ 这一个码无法表达两种粒度，"
                        + "而种子要靠 isApplicationCode 决定把它建成 MENU 还是 PAGE")
                .doesNotContainAnyElementsOf(applications);
    }

    @Test
    @DisplayName("应用顺序稳定（它就是导航与资源树的展示顺序）")
    void applicationsShouldKeepTheirOrder() {
        // 顺序本身就是信息（声明顺序 = 展示顺序），因此把它当作事实断言下来：
        // 改动顺序会让基座导航与资源目录同时变，那是要显式决定的事，不该顺手发生
        assertThat(AuthMenus.applicationCodes()).containsExactly(
                AuthPermissions.UI_LOG_CONSOLE_VIEW,
                AuthPermissions.UI_EVENT_CONSOLE_VIEW,
                AuthPermissions.UI_ENVIRONMENT_VIEW,
                AuthPermissions.UI_ADMIN_VIEW,
                AuthPermissions.UI_AUDIT_CONSOLE_VIEW);

        // 刻意**不**断言"每个应用至少有一个页面"：`pages` 为空是合法的，
        // 表示该应用只有一个视图（今天的三维环境就是这样，理由见 AuthResources 的 ui: 段）。
        // 断言非空会把"刻意没有页面级码"和"漏写了页面级码"判成同一件事，
        // 而这两者的处置完全相反。
    }
}
