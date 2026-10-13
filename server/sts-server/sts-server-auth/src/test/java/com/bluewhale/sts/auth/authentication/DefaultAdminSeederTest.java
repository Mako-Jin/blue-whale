package com.bluewhale.sts.auth.authentication;

import com.bluewhale.sts.auth.common.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子数据的**口令校验**测试。
 *
 * <p>钉的是本切片新立的那条规矩（见 {@code docs/decisions/0029}）：
 * 种子口令**没有默认值**，开了种子却不给口令 ⇒ **拒绝启动**。
 *
 * <p><b>为什么依赖全传 {@code null} 也不会 NPE：</b>校验发生在**任何落库动作之前**。
 * 这不是取巧 —— 它顺便把"先校验、再落库"这个顺序一起钉住了：
 * 顺序一旦反过来（先种管理员、再校验审计员），这几个用例会立刻以 NPE 失败。
 * 而那正是要防的半成品状态：管理员种下去了、审计员报错退出，库里留着一个
 * 谁也没打算创建的账号。
 *
 * <p>这条性质在加入组织种子之后**变得更值钱了**：种组织树会走到
 * {@code OrganizationService.create}（一个带 {@code @Transactional} 的真实 Bean），
 * 因此"先把两个口令都校验完"这件事现在挡住的是一条更长的写入链 ——
 * 组织树建好了、人挂进去了、然后才发现审计员没口令。
 */
class DefaultAdminSeederTest {

    /**
     * 依赖全传 null：校验通过之前不该碰到它们；碰到了就是 NPE —— 那正是我们要的那个信号。
     *
     * <p>每加一个依赖就多一个 {@code null}，这正是本用例的价值所在：
     * 它让"种子在建任何数据之前有没有先问清口令"这件事随着依赖增多而**更难侥幸通过**。
     */
    private static DefaultAdminSeeder seeder(AuthProperties properties) {
        return new DefaultAdminSeeder(properties, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    @DisplayName("没开种子时直接返回，连口令都不看（所以依赖为 null 也不炸）")
    void shouldSkipEntirelyWhenDisabled() {
        assertDoesNotThrow(() -> seeder(new AuthProperties()).run(null));
    }

    @Test
    @DisplayName("开了种子却没给管理员口令 ⇒ 拒绝启动，且消息指明是哪个属性")
    void shouldRefuseToStartWithoutAdminPassword() {
        AuthProperties properties = new AuthProperties();
        properties.getSeedAdmin().setEnabled(true);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> seeder(properties).run(null));

        // 报错必须说清楚该给哪个属性赋值。只说"口令不能为空"等于把定位工作留给运维去猜。
        assertTrue(
                thrown.getMessage().contains("bluewhale.auth.seed-admin.password"),
                "报错消息里没写清属性名：" + thrown.getMessage());
    }

    @Test
    @DisplayName("管理员口令给了、审计员口令没给 ⇒ 同样拒绝启动（且此时一个库都还没碰）")
    void shouldRefuseToStartWithoutAuditorPassword() {
        AuthProperties properties = new AuthProperties();
        properties.getSeedAdmin().setEnabled(true);
        properties.getSeedAdmin().setPassword("admin-secret");
        properties.getSeedAuditor().setEnabled(true);
        // 审计员的口令刻意留空

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> seeder(properties).run(null));

        assertTrue(
                thrown.getMessage().contains("bluewhale.auth.seed-auditor.password"),
                "报错消息里没写清属性名：" + thrown.getMessage());
    }

    @Test
    @DisplayName("一片空白不算给了口令 —— 与 null 一样被拒绝")
    void shouldTreatBlankAsMissing() {
        AuthProperties properties = new AuthProperties();
        properties.getSeedAdmin().setEnabled(true);
        properties.getSeedAdmin().setPassword("   ");

        assertThrows(IllegalStateException.class, () -> seeder(properties).run(null));
    }
}
